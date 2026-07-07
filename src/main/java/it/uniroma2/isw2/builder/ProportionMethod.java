package it.uniroma2.isw2.builder;

import it.uniroma2.isw2.model.Ticket;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

////**
// * Implements the Proportion Total method to determine IV and FV indices
// * for each bug ticket.
// *
//         * Responsibility: answer "WHICH RELEASES?" only.
// * Which files are buggy is determined separately by SZZ.
// *
//         * Formula:  P = (FV - IV) / (FV - OV)  →  IV = FV - P * (FV - OV)
//        *
//        *   OV = Opening Version : first release after ticket creation date
// *   FV = Fix Version     : release where the bug was fixed
// *   IV = Injected Version: release where the bug was introduced
// ** P is computed as the average over all tickets that have AV (Proportion Total).

public class ProportionMethod {

    private final List<LocalDateTime> sortedReleases;
    private static final Logger logger = Logger.getLogger(ProportionMethod.class.getName());

    public ProportionMethod(List<LocalDateTime> sortedReleases) {
        this.sortedReleases = new ArrayList<>(sortedReleases);
    }

    /**
     * Computes the global Proportion value P using all tickets that have AV.
     * This is the "Total" variant: one P value computed across all tickets at once.
     */
    public double computeP(List<Ticket> tickets,
                           Map<String, LocalDateTime> nameToDate) {
        double sumP  = 0;
        int    count = 0;
        int skippedNoMatch = 0;
        int skippedInvalid = 0;

        for (Ticket t : tickets) {
            if (t.affectedVersions.isEmpty() || t.fixVersions.isEmpty()) continue;

            LocalDateTime iv = earliestRelease(t.affectedVersions, nameToDate);
            LocalDateTime fv = earliestRelease(t.fixVersions,      nameToDate);
            LocalDateTime ov = openingVersion(t.created,           nameToDate);
            if (iv == null || fv == null || ov == null) {
                skippedNoMatch++;
                continue;
            }

            int ivIdx = sortedReleases.indexOf(iv);
            int fvIdx = sortedReleases.indexOf(fv);
            int ovIdx = sortedReleases.indexOf(ov);

            if (fvIdx <= ovIdx || ivIdx > fvIdx) {
                skippedInvalid++; // invalid data
                continue;
            }

            double p = (double)(fvIdx - ivIdx) / (fvIdx - ovIdx);
            if (p >= 0 && p <= 1) {
                sumP += p;
                count++;
            }
        }

        logger.info("computeP: skipped (no match)=" + skippedNoMatch
                + " skipped (invalid)=" + skippedInvalid
                + " valid=" + count);

        // Default to 0.5 if no tickets had valid AV data
        return count > 0 ? sumP / count : 0.5;
    }

    /**
     * Returns the IV index (into sortedReleases) for a ticket.
     *   - If AV is available → use earliest AV directly
     *   - Otherwise          → estimate via Proportion formula
     */
    public int getIVIndex(Ticket ticket,
                          Map<String, LocalDateTime> nameToDate,
                          double p) {
        if (!ticket.affectedVersions.isEmpty()) {
            LocalDateTime iv = earliestRelease(ticket.affectedVersions, nameToDate);
            return iv != null ? sortedReleases.indexOf(iv) : -1;
        }
        return estimateIV(ticket, nameToDate, p);
    }

    /**
     * Returns the FV index (into sortedReleases) for a ticket.
     * Uses the earliest fix version listed on the ticket.
     */
    public int getFVIndex(Ticket ticket,
                          Map<String, LocalDateTime> nameToDate) {
        if (ticket.fixVersions.isEmpty()) return -1;
        LocalDateTime fv = earliestRelease(ticket.fixVersions, nameToDate);
        return fv != null ? sortedReleases.indexOf(fv) : -1;
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Estimates IV using the Proportion formula when AV is not available.
     * Returns the index into sortedReleases, or -1 if it cannot be computed.
     */
    private int estimateIV(Ticket ticket,
                           Map<String, LocalDateTime> nameToDate,
                           double p) {
        if (ticket.fixVersions.isEmpty()) return -1;

        LocalDateTime fv = earliestRelease(ticket.fixVersions, nameToDate);
        LocalDateTime ov = openingVersion(ticket.created,      nameToDate);
        if (fv == null || ov == null) return -1;

        int fvIdx = sortedReleases.indexOf(fv);
        int ovIdx = sortedReleases.indexOf(ov);
        if (fvIdx < 0 || ovIdx < 0 || fvIdx <= ovIdx) return -1;

        int ivIdx = (int) Math.max(0, Math.round(fvIdx - p * (fvIdx - ovIdx)));
        return Math.min(ivIdx, fvIdx - 1);
    }

    /** Returns the earliest release date matching any of the given version names. */
    private LocalDateTime earliestRelease(List<String> versionNames,
                                          Map<String, LocalDateTime> nameToDate) {
        LocalDateTime earliest = null;
        for (String name : versionNames) {
            LocalDateTime dt = nameToDate.get(name);
            if (dt != null && (earliest == null || dt.isBefore(earliest))) {
                earliest = dt;
            }
        }
        return earliest;
    }

    /** Opening Version = first release on or after the ticket creation date. */
    private LocalDateTime openingVersion(String createdStr,
                                         Map<String, LocalDateTime> nameToDate) {
        if (createdStr == null || createdStr.isEmpty()) return null;
        try {
            // JIRA format: "2010-04-19T17:33:26.000+0000"
            LocalDateTime created = java.time.LocalDate
                    .parse(createdStr.substring(0, 10)).atStartOfDay();
            for (LocalDateTime r : sortedReleases) {
                if (!r.isBefore(created)) return r;
            }
        } catch (Exception ignored) {}
        return sortedReleases.isEmpty()
                ? null : sortedReleases.get(sortedReleases.size() - 1);
    }
}

