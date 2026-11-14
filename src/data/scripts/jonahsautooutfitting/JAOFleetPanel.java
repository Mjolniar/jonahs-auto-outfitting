package data.scripts.jonahsautooutfitting;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * "Optimize fleet" (JAOFleetApply): plans the whole fleet a frame at a time, then shows what would change on every ship
 * (weapons, wings, hullmods off and on, s-mods and their story points), the fleet before and after, and where the
 * items come from. Nothing changes until Apply to all ships.
 */
final class JAOFleetPanel extends JAOOverlayPanel {
    static final float W = 900f, H = 600f;
    final JAOFleetApply job;   // null when planning couldn't start
    /** Opens Fleet analysis (null: no button). */
    final Runnable analysis;
    boolean failed;
    ButtonAPI apply;
    TooltipMakerAPI status;

    JAOFleetPanel(JAOFleetApply job, Runnable close, Runnable analysis, boolean overlay) { super(close, overlay); this.job = job; this.analysis = analysis; }

    static JAOFleetApply start(Object refit) {
        try {
            return new JAOFleetApply(refit);
        } catch (Throwable t) {
            Global.getLogger(JAOFleetPanel.class).error("Auto Outfitting: Optimize fleet couldn't start", t);
            return null;
        }
    }

    @Override String title() { return "Optimize fleet"; }
    @Override float width() { return W; }
    @Override float height() { return H; }
    @Override String progress() { return job == null ? "" : job.fleet.progress(); }

    @Override
    boolean work() {
        if (job == null) return true;
        try {
            return job.step(25L);
        } catch (Throwable t) {
            Global.getLogger(JAOFleetPanel.class).error("Auto Outfitting: Optimize fleet planning failed", t);
            failed = true;
            return true;
        }
    }

    @Override
    void buildResult() {
        Color h = Misc.getHighlightColor(), g = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        TooltipMakerAPI head = element(W - 20f, 170f, false, 10f, 10f);
        head.addSectionHeading(title(), Alignment.MID, 0f);
        if (job == null || failed) {
            head.addPara("Couldn't plan the fleet (see starsector.log).", 6f, bad);
            closeButton();
            return;
        }
        int planned = 0;
        for (JAOFleetPlan.ShipPlan s : job.fleet.ships) if (s.plan != null) planned++;
        head.addPara("Every combat ship planned together from what you have here: %s ships, %s of them change.", 6f, h,
                "" + planned, "" + job.changedShips);
        head.addPara(esc(JAOOrderPanel.mixLine(job.before, job.fleet.mix())), 4f, g);
        String from = job.storage != null ? "fleet cargo and your storage at " + job.market.getName() + " (" + job.fromStorage + " items from storage)"
                : "fleet cargo only" + (job.market == null ? " (dock where you have storage to use what's stored there)" : " (no storage of yours here)");
        head.addPara("Weapons and LPCs: what the ships carry, plus " + esc(from) + ". What comes off and isn't used again goes to "
                + (job.storage != null ? "that storage." : "cargo."), 4f, g);
        int sp = Global.getSector().getPlayerStats().getStoryPoints();
        if (!job.smods) head.addPara("S-mods: none. Optimize fleet only builds s-mods while \"Build in s-mods\" is ticked in the Autofit panel.", 4f, g);
        else if (job.storyPoints > 0) head.addPara("S-mods: %s to build in, one story point each (you have %s, keeping %s" + (JAOSettings.smodFleetMax > 0 ? ", at most " + JAOSettings.smodFleetMax + " per Optimize fleet" : "") + "; \"Build in s-mods\" is ticked in the Autofit panel).", 4f, h,
                "" + job.storyPoints, "" + sp, "" + JAOSettings.smodReserve);
        else head.addPara("S-mods: none to build (ships full, no story points above the %s kept" + (JAOSettings.smodFleetMax > 0 ? ", Story points per Optimize fleet at " + JAOSettings.smodFleetMax : "") + ", or nothing worth one).", 4f, g, h, "" + JAOSettings.smodReserve);
        StringBuilder notes = new StringBuilder();
        int dmodShips = 0;
        for (JAOFleetPlan.ShipPlan s : job.fleet.ships) if (!s.civilian && dmods(s.member) > 0) dmodShips++;
        if (dmodShips > 0) notes.append(dmodShips).append(dmodShips == 1 ? " ship has" : " ships have")
                .append(" d-mods: each is fitted for its own armor, flux, speed, max CR, flight deck and turrets. ");
        if (job.civilian > 0) notes.append(job.civilian).append(" civilian ships get the logistics fit (campaign hullmods, no weapons or flux")
                .append(job.smods && JAOSettings.civilianSmods ? "; cruisers, capitals and salvage rigs build in fleet-wide ones). " : "). ");
        if (job.fleet.locked > 0) notes.append(job.fleet.locked).append(job.fleet.locked == 1
                ? " locked ship stays as it is, and what it carries isn't used for other ships"
                : " locked ships stay as they are, and what they carry isn't used for other ships").append(" (lock or unlock on the refit screen). ");
        if (job.fleet.failed > 0) notes.append(job.fleet.failed).append(" ships couldn't be planned and stay as they are (see starsector.log). ");
        if (job.crShips > 0) notes.append("Refitting in space costs CR, as on the refit screen: ").append(job.crShips)
                .append(job.crShips == 1 ? " ship loses " : " ships lose ").append("up to ").append(Math.round(job.crMax * 100f))
                .append("% (shown per ship). Refits at a market cost no CR. ");
        if (notes.length() > 0) head.addPara(esc(notes.toString().trim()), 4f, g);

        TooltipMakerAPI list = element(W - 20f, H - 250f, true, 10f, 190f);
        Color text = Global.getSettings().getColor("standardTextColor");
        int same = 0;
        for (JAOFleetApply.Change c : job.changes) {
            if (!c.any()) { if (!c.ship.locked) same++; continue; }
            List<String> parts = new ArrayList<>();
            if (c.weapons > 0) parts.add(c.weapons + " weapon" + (c.weapons > 1 ? "s" : ""));
            if (c.wings > 0) parts.add(c.wings + " wing" + (c.wings > 1 ? "s" : ""));
            for (String m : c.added) parts.add("+" + m);
            for (String m : c.removed) parts.add("-" + m);
            if (!c.smods.isEmpty()) parts.add("s-mod " + String.join(", ", c.smods));
            if (parts.isEmpty() && c.flux > 0) parts.add("vents/capacitors");
            if (c.cr >= 0.005f) parts.add("CR -" + Math.round(c.cr * 100f) + "%");
            // highlight arguments go in as they are (String.format doesn't read them): "CR -12%" needs no escaping
            list.addPara("%s  %s", 3f, new Color[]{h, text}, c.ship.name, String.join(", ", parts));
            for (String n : c.ship.notes) list.addPara("    " + esc(n) + ".", 0f, g);
        }
        if (same > 0) list.addPara(same + " ships already match the plan.", 6f, g);
        // notes on ships the plan doesn't change (a Guru with no plague LPCs to fly)
        for (JAOFleetApply.Change c : job.changes) {
            if (c.any()) continue;
            if (c.ship.locked) list.addPara("%s  %s", 3f, new Color[]{h, g}, c.ship.name, "locked: left as it is.");
            for (String n : c.ship.notes) list.addPara("%s  %s", 3f, new Color[]{h, g}, c.ship.name, n + ".");
        }

        TooltipMakerAPI buttons = element(W - 20f, 40f, false, 10f, H - 50f);
        apply = buttons.addButton("Apply to all ships", "apply", 220f, 30f, 0f);
        apply.setEnabled(job.changedShips > 0);
        ButtonAPI c = buttons.addButton("Close", "close", 140f, 30f, 0f);
        c.getPosition().rightOfTop(apply, 10f);
        if (analysis != null) {
            ButtonAPI a = buttons.addButton("Fleet analysis", "analysis", 160f, 30f, 0f);
            a.getPosition().rightOfTop(c, 10f);
        }
    }

    /** D-mods on the ship (hullmods tagged dmod). */
    static int dmods(com.fs.starfarer.api.fleet.FleetMemberAPI m) {
        int n = 0;
        try {
            for (String id : m.getVariant().getHullMods()) {
                com.fs.starfarer.api.loading.HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
                if (spec != null && spec.hasTag("dmod")) n++;
            }
        } catch (Throwable ignored) { }
        return n;
    }

    @Override
    public void buttonPressed(Object id) {
        if ("close".equals(id)) {
            close.run();
        } else if ("analysis".equals(id) && analysis != null) {
            analysis.run();
        } else if ("apply".equals(id) && apply != null && apply.isEnabled() && job != null) {
            apply.setEnabled(false);
            String msg;
            boolean ok = true;
            try {
                msg = job.apply();
            } catch (Throwable t) {
                Global.getLogger(JAOFleetPanel.class).error("Auto Outfitting: Optimize fleet failed while applying", t);
                msg = "Something went wrong part way (see starsector.log)";
                ok = false;
            }
            if (status == null) status = element(W - 400f, 40f, false, 390f, H - 48f);
            status.addPara(esc(msg) + ".", 0f, ok ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor());
        }
    }
}
