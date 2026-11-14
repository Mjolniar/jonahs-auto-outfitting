package data.scripts.jonahsautooutfitting;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug option "Remove all s-mods and refund story points" (0.5.33, LunaLib menu, jao_debugRefundSmods). Runs once when
 * the option goes from off to on in the menu during a game, never on load: every ship in the player fleet, modules
 * included, loses its s-mods (built-in hullmods lose their s-mod bonus), and the player gets one story point back per
 * s-mod. Bonus XP from building them is kept.
 */
final class JAODebug {
    /** Whether a settings save should run the refund: the option went from off to on, in a game. */
    static boolean shouldRun(boolean before, boolean now, boolean inGame) {
        return !before && now && inGame;
    }

    static boolean inGame() {
        try {
            return Global.getSector() != null && Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerStats() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Takes every s-mod off the player fleet and refunds them. Returns {s-mods removed, ships changed}. */
    static int[] refundFleet() {
        int removed = 0, ships = 0;
        List<String> lines = new ArrayList<>();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
            ShipVariantAPI v = m.getVariant();
            if (v == null || count(v) == 0) continue;
            v = v.clone();
            v.setSource(VariantSource.REFIT);
            List<String> gone = new ArrayList<>();
            int n = strip(v, gone);
            if (n == 0) continue;
            m.setVariant(v, false, true);
            m.updateStats();
            removed += n;
            ships++;
            lines.add((m.getShipName() == null ? m.getHullSpec().getHullName() : m.getShipName()) + " " + gone);
        }
        if (removed > 0) {
            MutableCharacterStatsAPI ps = Global.getSector().getPlayerStats();
            ps.addStoryPoints(removed);
            fleet.getFleetData().setSyncNeeded();
            fleet.getFleetData().syncIfNeeded();
        }
        Global.getLogger(JAODebug.class).info("Auto Outfitting: debug refund: removed " + removed + " s-mods from " + ships + " ships, refunded "
                + removed + " story points" + (lines.isEmpty() ? "" : ": " + String.join("; ", lines)));
        try {
            Global.getSector().getCampaignUI().addMessage("Auto Outfitting: removed " + removed + " s-mods from " + ships
                    + " ships and refunded " + removed + " story points. Turn the debug option off again.");
        } catch (Throwable ignored) { }
        return new int[]{removed, ships};
    }

    /** S-mods on the variant and its modules (s-mods plus s-modded built-ins). */
    static int count(ShipVariantAPI v) {
        int n = v.getSMods().size() + v.getSModdedBuiltIns().size();
        for (String slot : v.getModuleSlots()) {
            ShipVariantAPI mv = v.getModuleVariant(slot);
            if (mv != null) n += count(mv);
        }
        return n;
    }

    /** Removes every s-mod from the variant and its modules; returns how many. */
    static int strip(ShipVariantAPI v, List<String> gone) {
        int n = 0;
        for (String id : new ArrayList<>(v.getSMods())) {
            v.removePermaMod(id);
            v.removeMod(id);
            gone.add(id);
            n++;
        }
        for (String id : new ArrayList<>(v.getSModdedBuiltIns())) {
            v.getSModdedBuiltIns().remove(id);
            gone.add(id + " (built-in)");
            n++;
        }
        for (String slot : v.getModuleSlots()) {
            ShipVariantAPI mv = v.getModuleVariant(slot);
            if (mv == null) continue;
            ShipVariantAPI copy = mv.clone();
            int k = strip(copy, gone);
            if (k > 0) {
                copy.setSource(VariantSource.REFIT);
                v.setModuleVariant(slot, copy);
                n += k;
            }
        }
        return n;
    }
}
