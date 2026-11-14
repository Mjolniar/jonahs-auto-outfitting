package data.scripts.jonahsautooutfitting;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.plugins.AutofitPlugin.AutofitPluginDelegate;
import com.fs.starfarer.api.plugins.AutofitPlugin.AvailableFighter;
import com.fs.starfarer.api.plugins.AutofitPlugin.AvailableWeapon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole fleet planned together (user 11/05: "scan the entire fleet, determine optimally what everything should be").
 * Every combat ship gets its Optimal fit from one shared stock, biggest ships first, balanced against the rest of the
 * plan (JAOFleetMix) rather than the fleet as it stands; then up to two more passes in which every ship sees all the
 * others and keeps its plan unless a new one scores KEEP better (JAOOptimizer.scoreAsIs). The first pass chooses
 * hullmods (and s-mods, when asked: story points shared across the fleet); later passes re-pick weapons and wings around
 * them. The same hulls, stock and blueprints give the same plan whatever is fitted now: what the ships carry goes into
 * the stock and every ship is planned from bare.
 *
 * Runs a little each frame (step) so the screen stays live. Used by Order fleet weapons (JAOFleetPlanner, blueprints
 * unlimited) and Optimize fleet (JAOFleetApply, what can be fitted here).
 */
final class JAOFleetPlan {
    static final int PASSES = 3;
    static final float KEEP = 0.01f;

    static final class ShipPlan {
        final FleetMemberAPI member;
        final String name;
        /** The planned variant (a copy, modules included); null until planned. */
        ShipVariantAPI plan;
        /** Stock entries the plan takes, one per unit (PlanWeapon / PlanFighter). */
        final List<Object> used = new ArrayList<>();
        List<JAOModel.W> ws = new ArrayList<>();
        JAOSMods.Plan smods;
        int newSmods;
        boolean failed;
        /** Bays the ship's own scripts fill (Nimbus drones on a Maquech): in the plan, never taken from stock or cargo. */
        final java.util.Set<Integer> scripted = new java.util.TreeSet<>();
        /** What the player should know about this ship's plan (bay rules, penalty hullmods its scripts add). */
        final List<String> notes = new ArrayList<>();
        /** A civilian hull: the logistics fit (campaign hullmods, no weapons, fleet-wide s-mods on keepers), planned once, outside the fleet's balance. */
        boolean civilian;
        /** Locked on the refit screen (JAOLocks, 0.5.21): its plan is the ship as it is, what it carries stays on it, and it still counts toward the balance. */
        boolean locked;

        ShipPlan(FleetMemberAPI member) {
            this.member = member;
            this.name = member.getShipName() == null || member.getShipName().isEmpty() ? member.getHullSpec().getHullName() : member.getShipName();
        }
    }

    final List<ShipPlan> ships = new ArrayList<>();
    final List<AvailableWeapon> weapons;
    final List<AvailableFighter> fighters;
    final List<String> hullmods;
    /** Optimize fleet: hullmod rules of the place you're docked, s-mods if asked. */
    final boolean fit, smods;
    final MarketAPI market;
    final CampaignUIAPI.CoreUITradeMode tradeMode;
    final JAOAutofitPlugin plugin = new JAOAutofitPlugin(Global.getSector().getPlayerPerson());
    /** A ship as it is now (Optimize fleet: the refit screen's own copy for the ship it shows). */
    java.util.function.Function<FleetMemberAPI, ShipVariantAPI> variantOf = FleetMemberAPI::getVariant;
    int pass, index, changedThisPass, storyPoints, failed;
    boolean done;
    long nanos;

    JAOFleetPlan(List<FleetMemberAPI> members, List<AvailableWeapon> weapons, List<AvailableFighter> fighters, boolean fit, boolean smods,
                 MarketAPI market, CampaignUIAPI.CoreUITradeMode tradeMode) {
        this.weapons = weapons; this.fighters = fighters; this.fit = fit; this.smods = smods;
        this.market = market; this.tradeMode = tradeMode;
        this.hullmods = new ArrayList<>(new java.util.TreeSet<>(Global.getSector().getPlayerFaction().getKnownHullMods()));
        // one Optimize fleet spends story points down to the reserve, or at most jao_smodFleetMax of them when set (0.5.30; 0 = no cap since 0.5.35)
        this.storyPoints = spendable(Global.getSector().getPlayerStats().getStoryPoints(), JAOSettings.smodReserve, JAOSettings.smodFleetMax);
        List<FleetMemberAPI> order = new ArrayList<>(members);
        order.sort(Comparator.comparingInt((FleetMemberAPI m) -> -m.getHullSpec().getHullSize().ordinal())
                .thenComparing(m -> -m.getDeploymentPointsCost()).thenComparing(m -> m.getShipName() == null ? "" : m.getShipName())
                .thenComparing(FleetMemberAPI::getId));
        for (FleetMemberAPI m : order) {
            ShipPlan s = new ShipPlan(m);
            s.civilian = JAOSettings.civilianLogistics && JAOOptimizer.civilian(m.getHullSpec());
            s.locked = JAOLocks.locked(m);
            if (s.locked) locked++;
            ships.add(s);
        }
        // warships first, then civilians (0.5.34): the story points one Optimize fleet may spend go to warships before
        // civilians' fleet-wide s-mods (a civilian capital used up the per-run cap before any warship was planned)
        ships.sort(Comparator.comparingInt((ShipPlan s) -> s.civilian ? 1 : 0));
        done = ships.isEmpty();
    }

    /**
     * Story points the plan works with: the player's, cut to the reserve plus the per-run cap (0 = no cap: down to the
     * reserve). The s-mod limit (JAOOptimizer.smodLimit) takes the reserve off again, so a plan spends at most perRun.
     */
    static int spendable(int have, int reserve, int perRun) {
        if (perRun <= 0) return Math.max(0, have);
        return Math.max(0, Math.min(have, reserve + perRun));
    }

    /**
     * The rest of the fleet's utility hullmods for a civilian's logistics fit (JAOUtility, 0.5.31): civilians already
     * planned count with their plan, every other ship as it is now.
     */
    private JAOUtility.Others utilityOthers(ShipPlan self) {
        JAOUtility.Others o = new JAOUtility.Others();
        try {
            Map<String, ShipPlan> byId = new HashMap<>();
            for (ShipPlan t : ships) byId.put(t.member.getId(), t);
            for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                if (m.getId().equals(self.member.getId())) continue;
                ShipPlan t = byId.get(m.getId());
                float[] u = t != null && t.civilian && t.plan != null ? planUtility(t) : JAOUtility.read(m.getStats());
                o.add(u, JAOUtility.civilian(m), m.isMothballed(), m.getRepairTracker().getCR() >= JAOUtility.HRS_MIN_CR);
            }
        } catch (Throwable t) {
            Global.getLogger(JAOFleetPlan.class).warn("Auto Outfitting: couldn't read the fleet's utility hullmods for " + self.name, t);
        }
        return o;
    }

    private final Map<ShipPlan, Object[]> planUtility = new HashMap<>();

    /** A planned ship's utility numbers, from a scratch member of its plan (kept until the plan changes). */
    private float[] planUtility(ShipPlan t) {
        Object[] c = planUtility.get(t);
        if (c != null && c[0] == t.plan) return (float[]) c[1];
        float[] u = JAOUtility.read(Global.getFactory().createFleetMember(com.fs.starfarer.api.fleet.FleetMemberType.SHIP, t.plan.clone()).getStats());
        planUtility.put(t, new Object[]{t.plan, u});
        return u;
    }

    /** Ships locked on the refit screen. */
    int locked;
    private boolean lockedReady;

    /**
     * Locked ships before any planning: each keeps its own fit as its plan, what it carries leaves the shared stock, and
     * its weapons count in the fleet's balance from the first ship planned on.
     */
    private void readyLocked() {
        lockedReady = true;
        for (ShipPlan s : ships) {
            if (!s.locked) continue;
            try {
                ShipVariantAPI v = variantOf.apply(s.member).clone();
                keepOwn(s.member);
                s.plan = v;
                s.ws = s.civilian ? new ArrayList<>() : JAOFleetContext.weaponsOf(v);
            } catch (Throwable t) {
                s.failed = true;
                failed++;
                Global.getLogger(JAOFleetPlan.class).warn("Auto Outfitting: couldn't read locked ship " + s.name, t);
            }
        }
    }

    /** Plans ships until budgetMs has gone by; true when the whole plan is done. */
    boolean step(long budgetMs) {
        if (!lockedReady) readyLocked();
        long t0 = System.nanoTime(), end = t0 + budgetMs * 1_000_000L;
        while (!done && System.nanoTime() < end) {
            planOne(ships.get(index));
            if (++index >= ships.size()) {
                index = 0;
                pass++;
                if (pass >= PASSES || (pass > 1 && changedThisPass == 0)) done = true;
                changedThisPass = 0;
            }
        }
        nanos += System.nanoTime() - t0;
        return done;
    }

    String progress() {
        if (done) return "Planned " + ships.size() + " ships";
        return "Planning " + ships.get(index).name + " (" + (index + 1) + " of " + ships.size() + ", pass " + (pass + 1) + " of " + PASSES + ")";
    }

    private void planOne(ShipPlan s) {
        if (s.locked) return;
        if (s.civilian) { planCivilian(s); return; }
        ShipVariantAPI old = s.plan;
        List<Object> oldUsed = new ArrayList<>(s.used);
        if (old != null) give(oldUsed);
        JAOFleetMix others = mixWithout(s);
        PlanDelegate d = null;
        try {
            // the first pass fits from the ship as it is (hullmods and s-mods chosen), later ones re-pick weapons and
            // wings around the first pass's hullmods
            ShipVariantAPI v = (old == null ? variantOf.apply(s.member) : old).clone();
            d = delegate(s.member, old == null ? hullmods : new ArrayList<>(v.getNonBuiltInHullmods()));
            JAOOptimizer o = new JAOOptimizer(plugin, v, d, JAOSettings.style, false, true);
            o.fullPlan = old == null;
            o.planSmods = old == null && smods;
            o.storyPoints = storyPoints;
            o.fleetMix = others;
            o.run();
            // what the ship's hullmod scripts do to it when the game refreshes it: wings they place in empty bays join the
            // plan and their OP comes out of flux (JAOCompat); judged that way against the old plan too
            JAOCompat.Probe probe = JAOCompat.settle(v, capsFirst(v));
            if (old != null) {
                float vNew = score(v, others, s.member), vOld = score(old, others, s.member);
                if (vNew <= vOld * (1f + KEEP)) {
                    give(d.used);
                    take(oldUsed);
                    return;
                }
            }
            // everything that can fail first, then the plan is committed in one go
            List<JAOModel.W> ws = JAOFleetContext.weaponsOf(v);
            int newSmods = old == null ? Math.max(0, v.getSMods().size() - variantOf.apply(s.member).getSMods().size()) : s.newSmods;
            s.plan = v;
            s.used.clear();
            s.used.addAll(d.used);
            s.ws = ws;
            s.scripted.clear();
            s.scripted.addAll(probe.wings.keySet());
            notes(s, probe);
            if (old == null) {
                s.smods = o.smodPlan;
                s.newSmods = newSmods;
                storyPoints -= newSmods;
            }
            changedThisPass++;
        } catch (Throwable t) {
            // whatever the failed fit had taken goes back; the ship keeps its old plan, or (never planned) what it carries
            if (d != null) give(d.used);
            if (old != null) take(oldUsed);
            else keepOwn(s.member);
            if (!s.failed) failed++;
            s.failed = true;
            Global.getLogger(JAOFleetPlan.class).warn("Auto Outfitting: couldn't plan " + s.name, t);
        }
    }

    /**
     * A civilian ship's logistics fit (user 11/06: "it could automatically do the attuned drive field, surveying equipment,
     * salvage gantries"): the same campaign hullmods a single Optimal fit gives it, from bare, no weapons. With s-mods
     * asked for and the civilian s-mods setting on, keepers (cruisers, capitals, salvage rigs) build in the fleet-wide
     * ones (user 11/06, JAOOptimizer.FLEET_WIDE), out of the same story points as the warships. Takes nothing from the
     * stock (what it carries goes back into it for the warships) and is planned once.
     */
    private void planCivilian(ShipPlan s) {
        if (s.plan != null || s.failed) return;
        try {
            ShipVariantAPI v = variantOf.apply(s.member).clone();
            PlanDelegate d = delegate(s.member, hullmods);
            JAOOptimizer o = new JAOOptimizer(plugin, v, d, JAOSettings.style, false, true);
            o.logistics = true;
            o.utilityOthers = utilityOthers(s);
            o.planSmods = smods;
            o.storyPoints = storyPoints;
            o.run();
            int newSmods = Math.max(0, v.getSMods().size() - variantOf.apply(s.member).getSMods().size());
            s.newSmods = newSmods;
            storyPoints -= newSmods;
            JAOCompat.Probe probe = JAOCompat.settle(v, true);
            s.plan = v;
            s.used.clear();
            s.used.addAll(d.used);
            s.ws = new ArrayList<>();
            s.scripted.clear();
            s.scripted.addAll(probe.wings.keySet());
            changedThisPass++;
        } catch (Throwable t) {
            failed++;
            s.failed = true;
            Global.getLogger(JAOFleetPlan.class).warn("Auto Outfitting: couldn't plan " + s.name + "'s logistics fit", t);
        }
    }

    /** Carriers, phase, support and missile ships keep capacitors and give up vents first (JAOOptimizer.settleOp). */
    private static boolean capsFirst(ShipVariantAPI v) {
        JAORole r = JAOOptimizer.roleOf(v.getHullSpec());
        return r == JAORole.CARRIER || r == JAORole.PHASE || r == JAORole.MISSILE_BOAT || r == JAORole.SUPPORT;
    }

    /**
     * The plan's notes: bays a bay rule leaves empty (a Guru with no plague LPCs in stock) and penalty hullmods the ship's
     * scripts would add (error, warning, malfunction or penalty markers).
     */
    private static void notes(ShipPlan s, JAOCompat.Probe probe) {
        s.notes.clear();
        ShipVariantAPI v = s.plan;
        int builtIn = v.getHullSpec().getBuiltInWings() == null ? 0 : v.getHullSpec().getBuiltInWings().size();
        int empty = 0, bays = Global.getSettings().computeNumFighterBays(v);
        for (int i = builtIn; i < bays; i++) if (JAOFleetApply.wingAt(v, i) == null) empty++;
        String rule = empty > 0 ? JAOCompat.bayRule(v) : null;
        if (rule != null) s.notes.add(rule + "; none in stock, so " + empty + (empty == 1 ? " bay stays" : " bays stay") + " empty");
        List<String> bad = new ArrayList<>();
        for (String id : probe.added) {
            String n = (id + " " + JAOSMods.name(id)).toLowerCase(java.util.Locale.ROOT);
            if (n.contains("error") || n.contains("warning") || n.contains("malfunction") || n.contains("penalty")) bad.add(id);
        }
        if (!bad.isEmpty()) s.notes.add("its scripts add " + JAOCompat.names(bad));
        if (!probe.added.isEmpty() || !probe.wings.isEmpty()) Global.getLogger(JAOFleetPlan.class).info("Auto Outfitting: " + s.name
                + " plan: scripts fill bays " + probe.wings + ", add hullmods " + probe.added + (probe.removed.isEmpty() ? "" : ", remove " + probe.removed)
                + (probe.over > 0 ? ", " + probe.over + " OP over (taken from flux)" : ""));
    }

    /** A ship that couldn't be planned keeps what it carries: those items leave the shared stock. */
    private void keepOwn(FleetMemberAPI m) {
        Map<String, Integer> w = new java.util.TreeMap<>(), f = new java.util.TreeMap<>();
        try { addMountedWithModules(variantOf.apply(m), w, f); } catch (Throwable ignored) { return; }
        for (Map.Entry<String, Integer> e : w.entrySet()) {
            int n = e.getValue();
            for (AvailableWeapon a : weapons) {
                if (n <= 0) break;
                if (!(a instanceof PlanWeapon) || ((PlanWeapon) a).blueprint || !a.getId().equals(e.getKey())) continue;
                int k = Math.min(n, a.getQuantity());
                a.setQuantity(a.getQuantity() - k);
                n -= k;
            }
        }
        for (Map.Entry<String, Integer> e : f.entrySet()) {
            int n = e.getValue();
            for (AvailableFighter a : fighters) {
                if (n <= 0) break;
                if (!(a instanceof PlanFighter) || ((PlanFighter) a).blueprint || !a.getId().equals(e.getKey())) continue;
                int k = Math.min(n, a.getQuantity());
                a.setQuantity(a.getQuantity() - k);
                n -= k;
            }
        }
    }

    private float score(ShipVariantAPI v, JAOFleetMix others, FleetMemberAPI member) {
        JAOOptimizer o = new JAOOptimizer(plugin, v.clone(), delegate(member, new ArrayList<>()), JAOSettings.style, false, true);
        o.fleetMix = others;
        return o.scoreAsIs();
    }

    private JAOFleetMix mixWithout(ShipPlan except) {
        JAOFleetMix mix = new JAOFleetMix();
        for (ShipPlan s : ships) if (s != except && s.plan != null) mix.addShip(s.ws);
        return mix;
    }

    /** The whole plan's mix (the fleet after it). */
    JAOFleetMix mix() { return mixWithout(null); }

    private PlanDelegate delegate(FleetMemberAPI member, List<String> mods) {
        PlanDelegate d = new PlanDelegate(weapons, fighters, member, mods);
        d.campaign = fit;
        d.market = market;
        d.tradeMode = tradeMode;
        return d;
    }

    private static void give(List<Object> used) {
        for (Object o : used) {
            if (o instanceof AvailableWeapon) ((AvailableWeapon) o).setQuantity(((AvailableWeapon) o).getQuantity() + 1);
            else ((AvailableFighter) o).setQuantity(((AvailableFighter) o).getQuantity() + 1);
        }
    }

    private static void take(List<Object> used) {
        for (Object o : used) {
            if (o instanceof AvailableWeapon) ((AvailableWeapon) o).setQuantity(((AvailableWeapon) o).getQuantity() - 1);
            else ((AvailableFighter) o).setQuantity(((AvailableFighter) o).getQuantity() - 1);
        }
    }

    // ------------------------------------------------------------------ stock entries and the planning delegate

    static final class PlanWeapon implements AvailableWeapon {
        final WeaponSpecAPI spec;
        final float price;
        final boolean blueprint;
        int qty;

        PlanWeapon(WeaponSpecAPI spec, float price, int qty, boolean blueprint) { this.spec = spec; this.price = price; this.qty = qty; this.blueprint = blueprint; }
        public String getId() { return spec.getWeaponId(); }
        public WeaponSpecAPI getSpec() { return spec; }
        public float getPrice() { return price; }
        public CargoAPI getSource() { return null; }
        public SubmarketAPI getSubmarket() { return null; }
        public int getQuantity() { return qty; }
        public void setQuantity(int quantity) { qty = quantity; }
        public float getOPCost(MutableCharacterStatsAPI stats, MutableShipStatsAPI shipStats) { return spec.getOrdnancePointCost(stats, shipStats); }
    }

    static final class PlanFighter implements AvailableFighter {
        final FighterWingSpecAPI spec;
        final float price;
        final boolean blueprint;
        int qty;

        PlanFighter(FighterWingSpecAPI spec, float price, int qty, boolean blueprint) { this.spec = spec; this.price = price; this.qty = qty; this.blueprint = blueprint; }
        public String getId() { return spec.getId(); }
        public FighterWingSpecAPI getWingSpec() { return spec; }
        public float getPrice() { return price; }
        public CargoAPI getSource() { return null; }
        public SubmarketAPI getSubmarket() { return null; }
        public int getQuantity() { return qty; }
        public void setQuantity(int quantity) { qty = quantity; }
    }

    /**
     * Writes fits into a copy of the variant and remembers which stock entries they used. With campaign set (Optimize
     * fleet), hullmods follow the refit rules where you are: the ones that need a spaceport only when docked at one.
     */
    static final class PlanDelegate implements AutofitPluginDelegate {
        final List<AvailableWeapon> weapons;
        final List<AvailableFighter> fighters;
        final FleetMemberAPI member;
        final List<String> hullmods;
        final List<Object> used = new ArrayList<>();
        boolean campaign;
        MarketAPI market;
        CampaignUIAPI.CoreUITradeMode tradeMode = CampaignUIAPI.CoreUITradeMode.NONE;

        PlanDelegate(List<AvailableWeapon> weapons, List<AvailableFighter> fighters, FleetMemberAPI member, List<String> hullmods) {
            this.weapons = weapons; this.fighters = fighters; this.member = member; this.hullmods = hullmods;
        }

        public void fitFighterInSlot(int index, AvailableFighter fighter, ShipVariantAPI variant) {
            fighter.setQuantity(fighter.getQuantity() - 1);
            variant.setWingId(index, fighter.getId());
            used.add(fighter);
        }
        public void clearFighterSlot(int index, ShipVariantAPI variant) { variant.setWingId(index, null); }
        public void fitWeaponInSlot(WeaponSlotAPI slot, AvailableWeapon weapon, ShipVariantAPI variant) {
            weapon.setQuantity(weapon.getQuantity() - 1);
            variant.addWeapon(slot.getId(), weapon.getId());
            used.add(weapon);
        }
        public void clearWeaponSlot(WeaponSlotAPI slot, ShipVariantAPI variant) { variant.clearSlot(slot.getId()); }
        public List<AvailableWeapon> getAvailableWeapons() { return new ArrayList<>(weapons); }
        public List<AvailableFighter> getAvailableFighters() { return new ArrayList<>(fighters); }
        public boolean isPriority(WeaponSpecAPI weapon) { return false; }
        public boolean isPriority(FighterWingSpecAPI wing) { return false; }
        public List<String> getAvailableHullmods() { return new ArrayList<>(hullmods); }
        public void syncUIWithVariant(ShipVariantAPI variant) { }
        public ShipAPI getShip() { return null; }
        public FactionAPI getFaction() { return Global.getSector().getPlayerFaction(); }
        public boolean isAllowSlightRandomization() { return false; }
        public boolean isPlayerCampaignRefit() { return campaign; }
        public boolean canAddRemoveHullmodInPlayerCampaignRefit(String modId) {
            if (!campaign) return true;
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(modId);
            if (spec == null || spec.getEffect() == null) return true;
            try { return spec.getEffect().canBeAddedOrRemovedNow(null, market, tradeMode); } catch (Throwable t) { return false; }
        }
        public MarketAPI getMarket() { return market; }
        public FleetMemberAPI getFleetMember() { return member; }
    }

    /** Stock entries from id -> count maps (owned, price 0), in id order. */
    static void owned(Map<String, Integer> w, Map<String, Integer> f, List<AvailableWeapon> weapons, List<AvailableFighter> fighters) {
        for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(w).entrySet()) {
            WeaponSpecAPI spec = weaponSpec(e.getKey());
            if (spec != null && e.getValue() > 0) weapons.add(new PlanWeapon(spec, 0f, e.getValue(), false));
        }
        for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(f).entrySet()) {
            FighterWingSpecAPI spec = wingSpec(e.getKey());
            if (spec != null && e.getValue() > 0) fighters.add(new PlanFighter(spec, 0f, e.getValue(), false));
        }
    }

    /**
     * The weapons and wings a ship (or module) carries that a refit could take off: not built-in or mod-owned ones, and
     * not wings only scripts place (JAOCompat.boundWing: they stay in their bay).
     */
    static void addMounted(ShipVariantAPI v, Map<String, Integer> w, Map<String, Integer> f) {
        java.util.Set<String> skip = JAORatings.skipSlots(v.getHullSpec().getHullId(), v.getHullSpec().getBaseHullId());
        for (String slot : v.getFittedWeaponSlots()) {
            WeaponSlotAPI s = v.getSlot(slot);
            if (s == null || !JAOOptimizer.mountable(s) || skip.contains(slot) || v.getWeaponId(slot) == null) continue;
            w.merge(v.getWeaponId(slot), 1, Integer::sum);
        }
        int builtIn = v.getHullSpec().getBuiltInWings() == null ? 0 : v.getHullSpec().getBuiltInWings().size();
        int bays = Global.getSettings().computeNumFighterBays(v);
        for (int i = builtIn; i < bays; i++) {
            String id = JAOFleetApply.wingAt(v, i);   // an empty bay reads "" in getWings()
            if (id != null && !JAOCompat.boundWing(id)) f.merge(id, 1, Integer::sum);
        }
    }

    static void addMountedWithModules(ShipVariantAPI v, Map<String, Integer> w, Map<String, Integer> f) {
        addMounted(v, w, f);
        for (String slot : v.getStationModules().keySet()) {
            ShipVariantAPI mv = v.getModuleVariant(slot);
            if (mv != null) addMounted(mv, w, f);
        }
    }

    static void addCargo(CargoAPI c, Map<String, Integer> w, Map<String, Integer> f) {
        for (CargoAPI.CargoItemQuantity<String> q : c.getWeapons()) w.merge(q.getItem(), q.getCount(), Integer::sum);
        for (CargoAPI.CargoItemQuantity<String> q : c.getFighters()) f.merge(q.getItem(), q.getCount(), Integer::sum);
    }

    static WeaponSpecAPI weaponSpec(String id) {
        try { return Global.getSettings().getWeaponSpec(id); } catch (Throwable t) { return null; }
    }

    static FighterWingSpecAPI wingSpec(String id) {
        try { return Global.getSettings().getFighterWingSpec(id); } catch (Throwable t) { return null; }
    }

    /** Civilian hulls that get the logistics fit (setting on), not fighter wings. */
    static List<FleetMemberAPI> civilianShips(List<FleetMemberAPI> members) {
        List<FleetMemberAPI> out = new ArrayList<>();
        if (!JAOSettings.civilianLogistics) return out;
        for (FleetMemberAPI m : members) {
            if (m.isFighterWing() || m.getVariant() == null || m.getHullSpec() == null) continue;
            if (JAOOptimizer.civilian(m.getHullSpec())) out.add(m);
        }
        return out;
    }

    /** The combat ships a plan covers: not fighter wings, not civilian hulls with the logistics fit on. */
    static List<FleetMemberAPI> combatShips(List<FleetMemberAPI> members, int[] civilian) {
        List<FleetMemberAPI> out = new ArrayList<>();
        for (FleetMemberAPI m : members) {
            if (m.isFighterWing() || m.getVariant() == null || m.getHullSpec() == null) continue;
            if (JAOSettings.civilianLogistics && JAOOptimizer.civilian(m.getHullSpec())) { if (civilian != null) civilian[0]++; continue; }
            out.add(m);
        }
        return out;
    }
}
