package data.scripts.jonahsautooutfitting;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.plugins.SurveyPlugin;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Fleet needs for utility hullmods whose fleet-wide effect stops adding up (0.5.31). A civilian logistics fit and the
 * fleet-wide s-mods add one only while the rest of the fleet hasn't reached the point past which it does nothing:
 * - Surveying Equipment: survey supplies bottom out at 5 (SurveyPluginImpl); the fleet needs as much reduction as the
 *   sector's planets not yet fully surveyed call for (90th percentile, through the game's survey plugin).
 * - Treasure Hunt Package (Treasure Hunt mod): each progress source gives at most 3x its base (THUtils.applyBoost); the
 *   fleet needs (3 - 1) x the median reward.
 * - High Resolution Sensors: the fleet bonus grows with log base 2.5 of the ship count (HighResSensors
 *   .getAdjustedHRSModifier); one more is worth it while it adds at least half its own range.
 * - Burn hullmods: the fleet moves at its slowest ship; one is worth it only on a ship slower than the slowest ship that
 *   won't take burn hullmods (the warships).
 * Each is recognised by what it does on the scratch stats, so mods' hullmods with the same stats follow the same rules.
 */
final class JAOUtility {
    /** Indices into read(): survey supplies reduction, Treasure Hunt boost, sensor range bonus, max burn. */
    static final int SURVEY = 0, TREASURE = 1, SENSORS = 2, BURN = 3;
    static final String TH_BOOST = "th_treasure_hunt_boost";
    /** One more High Resolution Sensors ship must add at least this share of its own range to the fleet's. */
    static final float HRS_MIN_SHARE = 0.5f;
    /** Share of the sector's unsurveyed planets the fleet's survey reduction should bring to the floor. */
    static final float SURVEY_PERCENTILE = 0.9f;
    /** Used when the sector or the mod's numbers can't be read. */
    static final float SURVEY_FALLBACK = 75f, TREASURE_FALLBACK = 20f;
    /** HighResSensors.MIN_CR: below it a ship's sensors don't count. */
    static final float HRS_MIN_CR = 0.1f;

    /** What the rest of the fleet already gives. */
    static final class Others {
        float survey, treasure;
        final List<Float> sensors = new ArrayList<>();
        /** Max burn of the slowest ship that won't take burn hullmods (warships); MAX_VALUE when there is none. */
        float burnFloor = Float.MAX_VALUE;

        /** One fleet member's stats. Mothballed ships add nothing (the game skips them) but still set the fleet's burn. */
        void add(float[] u, boolean civilian, boolean mothballed, boolean sensorsWork) {
            if (!civilian) burnFloor = Math.min(burnFloor, u[BURN]);
            if (mothballed) return;
            survey += u[SURVEY];
            treasure += u[TREASURE];
            if (sensorsWork && u[SENSORS] > 0f) sensors.add(u[SENSORS]);
        }
    }

    /** {survey supplies reduction, Treasure Hunt boost, sensor range bonus, max burn} on these stats. */
    static float[] read(MutableShipStatsAPI st) {
        float[] u = new float[4];
        if (st == null) return u;
        try { u[SURVEY] = st.getDynamic().getMod(Stats.getSurveyCostReductionId(Commodities.SUPPLIES)).computeEffective(0f); } catch (Throwable ignored) { }
        try { u[TREASURE] = st.getDynamic().getMod(TH_BOOST).computeEffective(0f); } catch (Throwable ignored) { }
        try { u[SENSORS] = st.getDynamic().getMod(Stats.HRS_SENSOR_RANGE_MOD).computeEffective(0f); } catch (Throwable ignored) { }
        try { u[BURN] = st.getMaxBurnLevel().getModifiedValue(); } catch (Throwable ignored) { }
        return u;
    }

    /** The player fleet without skip (the ship being fitted), from each member's live stats. */
    static Others ofFleet(FleetMemberAPI skip) {
        Others o = new Others();
        try {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet == null) return o;
            for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                if (skip != null && m.getId().equals(skip.getId())) continue;
                o.add(read(m.getStats()), civilian(m), m.isMothballed(), m.getRepairTracker().getCR() >= HRS_MIN_CR);
            }
        } catch (Throwable t) {
            Global.getLogger(JAOUtility.class).warn("Auto Outfitting: couldn't read the fleet's utility hullmods", t);
        }
        return o;
    }

    static boolean civilian(FleetMemberAPI m) {
        return JAOSettings.civilianLogistics && m.getHullSpec() != null && JAOOptimizer.civilian(m.getHullSpec());
    }

    /**
     * Whether going from base to with (this ship's numbers without and with one hullmod) helps the fleet. A hullmod
     * that changes none of the four is always fine; one that changes several is fine if any of them still helps.
     */
    static boolean useful(float[] base, float[] with, Others o, float surveyTarget, float treasureTarget) {
        boolean touched = false, ok = false;
        if (with[SURVEY] > base[SURVEY] + 1e-3f) { touched = true; ok |= o.survey + base[SURVEY] < surveyTarget; }
        if (with[TREASURE] > base[TREASURE] + 1e-3f) { touched = true; ok |= o.treasure + base[TREASURE] < treasureTarget; }
        if (with[SENSORS] > base[SENSORS] + 1e-3f) {
            touched = true;
            float gain = sensorBonus(o.sensors, with[SENSORS]) - sensorBonus(o.sensors, base[SENSORS]);
            ok |= gain >= HRS_MIN_SHARE * (with[SENSORS] - base[SENSORS]) - 1e-3f;
        }
        if (with[BURN] > base[BURN] + 1e-3f) { touched = true; ok |= base[BURN] < o.burnFloor; }
        return !touched || ok;
    }

    /** Names of what base -> with would add, for the fit log. */
    static String what(float[] base, float[] with) {
        List<String> w = new ArrayList<>();
        if (with[SURVEY] > base[SURVEY] + 1e-3f) w.add("survey");
        if (with[TREASURE] > base[TREASURE] + 1e-3f) w.add("treasure hunt");
        if (with[SENSORS] > base[SENSORS] + 1e-3f) w.add("sensors");
        if (with[BURN] > base[BURN] + 1e-3f) w.add("burn");
        return String.join("/", w);
    }

    /** The fleet's sensor range bonus from the others' ships and one more of own (0 = none), as HighResSensors sums it. */
    static float sensorBonus(List<Float> others, float own) {
        float max = 0f, total = 0f;
        for (float v : others) { if (v <= 0f) continue; max = Math.max(max, v); total += v; }
        if (own > 0f) { max = Math.max(max, own); total += own; }
        if (max <= 0f) return 0f;
        float units = total / max;
        if (units <= 1f) return max;
        float mult = (float) (Math.log(units) / Math.log(2.5)) + 1f;
        return Math.max(1f, total * mult / units);
    }

    /** p-th percentile (0-1) of the values, 0 for none. */
    static float percentile(List<Float> values, float p) {
        if (values.isEmpty()) return 0f;
        List<Float> v = new ArrayList<>(values);
        Collections.sort(v);
        int i = Math.max(0, Math.min(v.size() - 1, (int) Math.ceil(p * v.size()) - 1));
        return v.get(i);
    }

    /** Treasure Hunt's need: (mult - 1) x the median reward, the boost past which half the reward kinds give no more. */
    static float treasureNeed(List<Float> rewards, float mult) {
        if (rewards.isEmpty() || mult <= 1f) return TREASURE_FALLBACK;
        List<Float> v = new ArrayList<>(rewards);
        Collections.sort(v);
        int n = v.size();
        float median = n % 2 == 1 ? v.get(n / 2) : 0.5f * (v.get(n / 2 - 1) + v.get(n / 2));
        return (mult - 1f) * median;
    }

    private static float surveyTarget = -1f, treasureTarget = -1f;
    private static long surveyDay = Long.MIN_VALUE;

    /**
     * Survey supplies reduction the fleet needs: the 90th percentile, over the sector's planets not yet fully surveyed,
     * of what the game's survey plugin charges past the 5-supply floor with no surveying equipment. Recounted once a day.
     */
    static float surveyTarget() {
        long day;
        try { day = Global.getSector().getClock().getDay() + 31L * Global.getSector().getClock().getMonth() + 400L * Global.getSector().getClock().getCycle(); }
        catch (Throwable t) { return SURVEY_FALLBACK; }
        if (day == surveyDay && surveyTarget >= 0f) return surveyTarget;
        float target = SURVEY_FALLBACK;
        try {
            float floor = 5f;
            try { floor = com.fs.starfarer.api.impl.campaign.SurveyPluginImpl.MIN_SUPPLIES_OR_MACHINERY; } catch (Throwable ignored) { }
            CampaignFleetAPI none = Global.getFactory().createEmptyFleet(Factions.PLAYER, "survey check", true);
            // the player fleet's survey cost multipliers (Makeshift Equipment x0.5), without its surveying equipment
            try {
                com.fs.starfarer.api.combat.MutableStat own = Global.getSector().getPlayerFleet().getStats().getDynamic().getStat(Stats.SURVEY_COST_MULT);
                com.fs.starfarer.api.combat.MutableStat mine = none.getStats().getDynamic().getStat(Stats.SURVEY_COST_MULT);
                for (Map.Entry<String, com.fs.starfarer.api.combat.MutableStat.StatMod> e : own.getMultMods().entrySet()) {
                    mine.modifyMult(e.getKey(), e.getValue().getValue());
                }
            } catch (Throwable ignored) { }
            List<Float> needs = new ArrayList<>();
            for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
                for (PlanetAPI p : sys.getPlanets()) {
                    if (p.isStar() || p.getMarket() == null || p.getMarket().getSurveyLevel() == MarketAPI.SurveyLevel.FULL) continue;
                    // a new plugin per planet: SurveyPluginImpl keeps its cost modifiers between init calls
                    SurveyPlugin plugin = (SurveyPlugin) Global.getSettings().getNewPluginInstance("surveyPlugin");
                    plugin.init(none, p);
                    Integer s = plugin.getConsumed().get(Commodities.SUPPLIES);
                    if (s != null) needs.add(Math.max(0f, s - floor));
                }
            }
            target = percentile(needs, SURVEY_PERCENTILE);
        } catch (Throwable t) {
            Global.getLogger(JAOUtility.class).warn("Auto Outfitting: couldn't read survey costs; Surveying Equipment target " + SURVEY_FALLBACK, t);
        }
        surveyDay = day;
        surveyTarget = target;
        return target;
    }

    /** Treasure Hunt boost the fleet needs, from the mod's own reward table and clamp (read once). */
    static float treasureTarget() {
        if (treasureTarget >= 0f) return treasureTarget;
        float mult = 3f;
        List<Float> rewards = new ArrayList<>();
        try {
            JSONObject s = Global.getSettings().loadJSON("treasurehunt_settings.json", "spinloki_treasurehunt");
            mult = (float) s.optDouble("th_treasure_hunt_package_mult_clamp", 3.0);
        } catch (Throwable ignored) { }
        try {
            JSONObject r = Global.getSettings().getJSONObject("th_rewards");
            for (Iterator<?> it = r.keys(); it.hasNext(); ) {
                JSONObject e = r.optJSONObject(String.valueOf(it.next()));
                if (e != null && e.has("value")) rewards.add((float) e.optDouble("value", 0.0));
            }
        } catch (Throwable ignored) { }
        treasureTarget = treasureNeed(rewards, mult);
        return treasureTarget;
    }

    /** Fit log text: the fleet totals and targets this fit was judged against. */
    static String describe(Others o) {
        return "fleet survey " + Math.round(o.survey) + " of " + Math.round(surveyTarget()) + ", treasure hunt " + Math.round(o.treasure)
                + " of " + Math.round(treasureTarget()) + ", sensors " + Math.round(sensorBonus(o.sensors, 0f))
                + ", slowest warship burn " + (o.burnFloor == Float.MAX_VALUE ? "none" : String.valueOf(Math.round(o.burnFloor)));
    }
}
