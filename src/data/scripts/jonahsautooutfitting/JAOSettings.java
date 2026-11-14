package data.scripts.jonahsautooutfitting;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * The options. From LunaLib's settings menu when LunaLib is enabled, otherwise from
 * data/config/jonahs_auto_outfitting.json. Values missing from both fall back to the defaults written here.
 */
public class JAOSettings {
    public static final String MOD = "jonahs_auto_outfitting", LUNA = "lunalib", FILE = "data/config/jonahs_auto_outfitting.json";
    static final Logger LOG = Global.getLogger(JAOSettings.class);

    public static boolean enabled = true, roles = true, civilianLogistics = true, civilianSmods, productionButton = true, allowDrawbacks, allStyles, specialWeapons = true, preferRare, cheatItems, communityTiers = true, keepPriority = true, logistics, debug, smodWait = true, fleetBalance = true, ratSettlement = true;
    public static JAOStyle style = JAOStyle.AUTO;
    /** 0 auto, 1 long, 2 close. */
    public static int range;
    public static float fightSeconds = 90f, pdFloor = 1f, rareBonus = 0.15f, smodMinGain = 0.02f, smodWaitMargin = 0.1f;
    /** Score gain a bought weapon or LPC must add per 10,000 credits it costs (0.01 = 1%; 0 = prices ignored). */
    public static float buyGain = 0.01f;
    public static int smodCount = 1, smodReserve = 3, smodFleetMax = 2, smodWaitDays = 30;
    /** Share of the player's credits a fleet production order may cost. */
    public static float productionBudget = 0.5f;
    /** Safety Overrides: 0 never, 1 frigates and destroyers, 2 whenever it scores best. */
    public static int safetyOverrides = 1;
    /** Weapon ids or patterns (with *) the Optimal fit never picks. */
    public static String[] skipWeapons = new String[0];

    static JSONObject file;
    static boolean useLuna;

    public static boolean luna() { return Global.getSettings().getModManager().isModEnabled(LUNA); }

    /** Reads every value. False (nothing changed) while LunaLib is enabled but hasn't loaded its settings yet. */
    public static boolean load() { return load(true); }

    /** fromLuna false reads the file even when LunaLib is enabled. */
    public static boolean load(boolean fromLuna) {
        if (file == null) {
            try { file = Global.getSettings().loadJSON(FILE, MOD); }
            catch (Throwable t) { file = new JSONObject(); LOG.warn("Auto Outfitting: can't read " + FILE + "; using defaults", t); }
        }
        useLuna = fromLuna && luna();
        if (useLuna) {
            Boolean probe;
            try { probe = JAOLuna.bool("jao_enabled"); } catch (Throwable t) { probe = null; }
            if (probe == null) return false;
        }
        enabled = bool("jao_enabled", true);
        style = JAOStyle.named(string("jao_style", "Auto"));
        allStyles = bool("jao_allStyles", false);
        roles = bool("jao_roles", true);
        civilianLogistics = bool("jao_civilianLogistics", true);
        civilianSmods = bool("jao_civilianSmods", false);
        productionButton = bool("jao_productionButton", true);
        fleetBalance = bool("jao_fleetBalance", true);
        productionBudget = clamp(dbl("jao_productionBudget", 0.5f), 0.05f, 1f);
        ratSettlement = bool("jao_ratSettlement", true);
        String r = string("jao_range", "Auto");
        range = "Long".equalsIgnoreCase(r) ? 1 : "Close".equalsIgnoreCase(r) ? 2 : 0;
        fightSeconds = clamp(integer("jao_fightSeconds", 90), 30, 300);
        specialWeapons = bool("jao_specialWeapons", true);
        preferRare = bool("jao_preferRare", false);
        cheatItems = bool("jao_cheatItems", false);
        communityTiers = bool("jao_communityTiers", true);
        rareBonus = clamp(dbl("jao_rareBonus", 0.15f), 0f, 0.5f);
        keepPriority = bool("jao_keepPriority", true);
        pdFloor = clamp(dbl("jao_pdFloor", 1f), 0f, 3f);
        buyGain = clamp(dbl("jao_buyGain", 0.01f), 0f, 0.1f);
        logistics = bool("jao_logistics", false);
        allowDrawbacks = bool("jao_allowDrawbacks", false);
        String so = string("jao_safetyOverrides", "Frigates and destroyers");
        safetyOverrides = "Never".equalsIgnoreCase(so) ? 0 : so.toLowerCase().startsWith("when") ? 2 : 1;
        skipWeapons = string("jao_skipWeapons", "").trim().isEmpty() ? new String[0] : string("jao_skipWeapons", "").trim().split("\s*,\s*");
        smodCount = clamp(integer("jao_smodCount", 1), 1, 3);
        smodFleetMax = clamp(integer("jao_smodFleetMax", 2), 0, 100);
        smodMinGain = clamp(dbl("jao_smodMinGain", 0.02f), 0f, 0.2f);
        smodWait = bool("jao_smodWait", true);
        smodWaitMargin = clamp(dbl("jao_smodWaitMargin", 0.1f), 0f, 1f);
        smodWaitDays = clamp(integer("jao_smodWaitDays", 30), 1, 720);
        smodReserve = clamp(integer("jao_smodReserve", 3), 0, 100);
        debug = bool("jao_debug", false);
        return true;
    }

    static boolean bool(String id, boolean def) {
        if (useLuna) { try { Boolean v = JAOLuna.bool(id); if (v != null) return v; } catch (Throwable ignored) { } }
        return file.optBoolean(id, def);
    }

    static int integer(String id, int def) {
        if (useLuna) { try { Integer v = JAOLuna.integer(id); if (v != null) return v; } catch (Throwable ignored) { } }
        return file.optInt(id, def);
    }

    static float dbl(String id, float def) {
        if (useLuna) { try { Double v = JAOLuna.dbl(id); if (v != null) return v.floatValue(); } catch (Throwable ignored) { } }
        return (float) file.optDouble(id, def);
    }

    static String string(String id, String def) {
        if (useLuna) { try { String v = JAOLuna.string(id); if (v != null) return v.trim(); } catch (Throwable ignored) { } }
        return file.optString(id, def).trim();
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /** One log line with the current options. */
    static String summary() {
        return (enabled ? "Optimal goal on" : "Optimal goal off") + ", style " + style.label + (roles ? ", fleet roles" : "") + (civilianLogistics ? ", civilian logistics fits" + (civilianSmods ? " with fleet-wide s-mods" : "") : "") + (fleetBalance ? ", fleet balance" : "") + (ratSettlement ? "" : ", RAT settlement orders off") + (allStyles ? " (+ one goal per style)" : "")
                + ", range " + (range == 1 ? "long" : range == 2 ? "close" : "auto") + ", fight " + (int) fightSeconds + "s"
                + ", special weapons " + specialWeapons + (preferRare ? ", rare bonus " + rareBonus : "") + (cheatItems ? ", cheat-only items allowed" : "") + (communityTiers ? "" : ", no community tiers") + ", PD floor " + pdFloor + ", buy gain " + buyGain + " per 10k" + ", logistics " + logistics + ", drawbacks " + (allowDrawbacks ? "allowed" : "never") + ", SO " + (safetyOverrides == 0 ? "never" : safetyOverrides == 1 ? "frigates/destroyers" : "when best")
                + ", s-mods up to " + smodCount + " per fit, " + smodFleetMax + " SP per Optimize fleet (min gain " + smodMinGain + (smodWait ? ", wait " + smodWaitMargin + " up to " + smodWaitDays + " days" : "") + ", keep " + smodReserve + " SP"
                + ")"
                + (debug ? ", debug log" : "");
    }
}
