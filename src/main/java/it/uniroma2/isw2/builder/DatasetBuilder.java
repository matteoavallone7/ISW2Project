package it.uniroma2.isw2.builder;

import it.uniroma2.isw2.model.Ticket;
import it.uniroma2.isw2.retrievers.GetReleaseInfo;
import it.uniroma2.isw2.retrievers.RetrieveBugTickets;
import it.uniroma2.isw2.utils.SZZ;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class DatasetBuilder {

    private static final Logger logger = Logger.getLogger(DatasetBuilder.class.getName());
    private static final String PROJECT_NAME = "OPENJPA";

    // Proportion of releases to KEEP (first 34%)
    private static final double KEEP_RATIO  = 0.34;
    private static final Pattern PRE_RELEASE = Pattern.compile("(?i).*(alpha|beta|rc|snapshot|m\\d+|ea\\d*).*");

    private static final String[] CSV_HEADER = {
            "Version",
            "VersionIndex",
            "File Name",
            "LOC",
            "LOC_touched",
            "NR",
            "NFix",
            "NAuth",
            "LOC_added",
            "MAX_LOC_added",
            "AVG_LOC_added",
            "Churn",
            "MAX_Churn",
            "AVG_Churn",
            "ChgSetSize",
            "MAX_ChgSet",
            "AVG_ChgSet",
            "Age",
            "WeightedAge",
            "Smells",
            "Buggy"
    };

    private DatasetBuilder(){}

    public static void buildDataset(String repoPath, String outputDir) throws IOException {
        logger.info("=== Milestone 1 – Dataset Creation for " + PROJECT_NAME + " ===");

        // ── Step 1: releases ──────────────────────────────────────────────────────
        logger.info("[1/5] Fetching releases...");

        // nameToDate maps version name → release date (JIRA-first, git as fallback)
        Map<String, LocalDateTime> nameToDate = new HashMap<>();

        try {
            GetReleaseInfo.load(PROJECT_NAME);
            for (LocalDateTime dt : GetReleaseInfo.releases) {
                nameToDate.put(GetReleaseInfo.releaseNames.get(dt), dt);
            }
            logger.info("    JIRA releases: " + GetReleaseInfo.releases.size());
        } catch (Exception e) {
            logger.severe("Unable to retrieve release information from JIRA.");
            throw new IOException("Cannot continue without release dates.", e);
        }

        GitMetrics git = new GitMetrics(repoPath);
        List<String[]> allGitReleases = git.getStableReleasesSortedByDate();
        logger.info("    Stable git tags: " + allGitReleases.size());

        // Keep only releases that have a known JIRA release date
        List<String[]> validReleases = new ArrayList<>();

        for (String[] release : allGitReleases) {

            if (nameToDate.containsKey(release[0])) {
                validReleases.add(release);
            } else {
                logger.fine("Ignoring release without date: " + release[0]);
            }
        }

        allGitReleases = validReleases;

        allGitReleases.removeIf(r -> PRE_RELEASE.matcher(r[0]).matches());

        // Sort releases according to the official JIRA release date
        allGitReleases.sort(
                Comparator.comparing(r -> nameToDate.get(r[0]))
        );

        logger.info("Releases in chronological order:");
        for (String[] r : allGitReleases) {
            logger.info(r[0] + " -> " + nameToDate.get(r[0]));
        }

        // Remove releases that contain no commits
        List<String[]> filteredReleases = new ArrayList<>();

        for (int i = 0; i < allGitReleases.size(); i++) {

            String previous = (i == 0) ? null : allGitReleases.get(i - 1)[0];

            String current = allGitReleases.get(i)[0];

            if (git.hasCommitsBetween(previous, current)) {
                filteredReleases.add(allGitReleases.get(i));
            } else {
                logger.fine("Ignoring release without commits: " + current);
            }
        }

        allGitReleases = filteredReleases;

        // Keep only first 34% of releases
        int nKeep = (int) Math.ceil(allGitReleases.size() * KEEP_RATIO);
        List<String[]> keptReleases = new ArrayList<>(allGitReleases.subList(0, nKeep));
        logger.info("    Using first 34% → " + nKeep + " releases:");
        for (String[] r : keptReleases) logger.info("      " + r[0]);


        // Full sorted release dates needed by ProportionMethod
        List<LocalDateTime> sortedRelDates = new ArrayList<>();
        for (String[] r : allGitReleases) {
            LocalDateTime dt = nameToDate.get(r[0]);
            if (dt != null) sortedRelDates.add(dt);
        }

        sortedRelDates.sort(Comparator.naturalOrder());

        // Map: index in sortedRelDates (all releases) → index in keptReleases
        Map<Integer, Integer> fullToKeptIdx = new HashMap<>();
        for (int k = 0; k < keptReleases.size(); k++) {
            LocalDateTime keptDt = nameToDate.get(keptReleases.get(k)[0]);
            int fullIdx = sortedRelDates.indexOf(keptDt);
            if (fullIdx >= 0) fullToKeptIdx.put(fullIdx, k);
        }

        // ── Step 2: tickets ───────────────────────────────────────────────────────
        logger.info("[2/5] Fetching bug tickets from JIRA...");
        List<Ticket> tickets = new ArrayList<>();
        try {
            tickets = RetrieveBugTickets.fetchAll(PROJECT_NAME);
            logger.info("    Tickets fetched: " + tickets.size());
        } catch (Exception e) {
            logger.warning("    JIRA unreachable for tickets: " + e.getMessage());
        }

        // ── Step 3: Proportion Total ──────────────────────────────────────────────
        logger.info("[3/5] Computing Proportion Total P...");
        ProportionMethod proportion = new ProportionMethod(sortedRelDates);
        double P = proportion.computeP(tickets, nameToDate);
        logger.info(String.format("    P = %.4f (from %d tickets with AV)", P,
                tickets.stream().filter(t -> !t.affectedVersions.isEmpty()).count()));

        String lastAllHash   = git.resolveHash(allGitReleases.get(allGitReleases.size() - 1)[0]);

        git.linkCommitsToTickets(tickets, lastAllHash);

        long withCommits = tickets.stream()
                .filter(t -> !t.getFixCommits().isEmpty())
                .count();
        long totalCommits = tickets.stream()
                .mapToLong(t -> t.getFixCommits().size())
                .sum();
        logger.info("Tickets with commits linked: " + withCommits + "/" + tickets.size());
        logger.info("Total fix commits linked: " + totalCommits);
        if (withCommits > 0) {
            Ticket sample = tickets.stream()
                    .filter(t -> !t.getFixCommits().isEmpty())
                    .findFirst().get();
            logger.info("Sample ticket: " + sample.key
                    + " → " + sample.getFixCommits().size() + " commits"
                    + " first hash=" + sample.getFixCommits().get(0).hash
                    + " ts=" + sample.getFixCommits().get(0).timestamp);
        }

        // ── Step 4: build buggy set ───────────────────────────────────────────────
        // Key format: "filePath##releaseIdx"  (releaseIdx = index in keptReleases)
        logger.info("[4/5] Labelling buggy classes (SZZ + Proportion)...");

        Set<String> buggySet = buildBuggySet(tickets, git, proportion, nameToDate,
                P, keptReleases, fullToKeptIdx);
        logger.info("    Buggy (file, release) pairs: " + buggySet.size());

        // ── Step 5: compute metrics and write rows ────────────────────────────────
        logger.info("[5/5] Computing metrics for each release and class...");
        List<String[]> allRows = new ArrayList<>();
        PMDService pmd = new PMDService();

        for (int relIdx = 0; relIdx < keptReleases.size(); relIdx++) {
            String tag      = keptReleases.get(relIdx)[0];
            long   relTs    = Long.parseLong(keptReleases.get(relIdx)[1]);
            String relHash  = git.resolveHash(tag);
            String prevHash = findPredecessor(tag, allGitReleases, git);
            git.checkout(tag);
            Map<String,Integer> smells = pmd.analyzeRepository(new File(repoPath));

            List<String> javaFiles = git.listJavaFiles(tag);
            logger.info(String.format("  Release %2d/%d  %-12s  (%d java files)",
                    relIdx + 1, keptReleases.size(), tag, javaFiles.size()));

            for (String fp : javaFiles) {
                GitMetrics.Metrics m = git.compute(fp, prevHash, relHash, relTs);
                m.smells = smells.getOrDefault(fp, 0);
                String buggy = buggySet.contains(fp + "##" + relIdx) ? "Yes" : "No";
                allRows.add(buildRow(tag, relIdx + 1, fp, m, buggy));
            }
        }
        logger.info("    Total rows: " + allRows.size());


        new File(outputDir).mkdirs();
        writeCSV(allRows,    outputDir + "/OPENJPA_full_dataset.csv");
        printBugStats("Full dataset", allRows);

        logger.info("=== Done ===");
        logger.info("  Full    : " + allRows.size()   + " rows");
    }

    // ── Buggy set construction ────────────────────────────────────────────────────
    private static Set<String> buildBuggySet(
            List<Ticket> tickets,
            GitMetrics git,
            ProportionMethod proportion,
            Map<String, LocalDateTime> nameToDate,
            double P,
            List<String[]> keptReleases,
            Map<Integer, Integer> fullToKeptIdx) {

        Set<String> buggySet  = new HashSet<>();
        SZZ         szz       = new SZZ(git);
        int noCommits = 0, badTime = 0, noFiles = 0, noIV = 0, linked = 0;

        for (Ticket t : tickets) {

            // Ticket has no associated commits
            if (t.getFixCommits().isEmpty()) {
                noCommits++;
                continue;
            }

            // Commit date must be within ticket lifetime
            long created = t.getCreationTime();

            long resolved = t.getResolutionTime();

            boolean validCommit = false;

            for (Ticket.CommitInfo ci : t.getFixCommits()) {
                if (ci.timestamp >= created && (resolved < 0 || ci.timestamp <= resolved)) {
                    validCommit = true;
                    break;
                }
            }

            if (!validCommit) {
                badTime++;
                continue;
            }
            // ── Branch 1: SZZ → which files? ─────────────────────────────────
            Set<String> buggyFiles = szz.findBuggyFiles(t);
            if (buggyFiles.isEmpty()) {
                noFiles++;
                logger.fine("  " + t.key + ": no fix commit found in git, skipping");
                continue;
            }

            // ── Branch 2: Proportion → which releases? ────────────────────────
            int ivIdx = proportion.getIVIndex(t, nameToDate, P);
            int fvIdx = proportion.getFVIndex(t, nameToDate);

            if (ivIdx < 0 || fvIdx < 0 || ivIdx >= fvIdx) {
                logger.fine("  " + t.key + ": could not determine valid IV/FV, skipping");
                continue;
            }

            linked++;

            // ── Combine: label file as buggy in every release in [IV, FV) If a file modified by a fix commit did not exist in an affected release, do not label it buggy. ─────
            for (String fp : buggyFiles) {
                for (int r = ivIdx; r < fvIdx; r++) {
                    Integer keptIdx = fullToKeptIdx.get(r);
                    if (keptIdx == null) continue;  // this release is in the ignored 66%
                    if (git.fileExists(fp, keptReleases.get(keptIdx)[0])) {
                        buggySet.add(fp + "##" + keptIdx);
                    }
                }
            }

            logger.fine(String.format("  %s → %d files × releases [%d, %d)",
                    t.key, buggyFiles.size(), ivIdx, fvIdx));

            logger.info("buildBuggySet breakdown: noCommits=" + noCommits
                    + " badTime=" + badTime
                    + " noFiles=" + noFiles
                    + " noIV=" + noIV
                    + " linked=" + linked);
        }
        return buggySet;
    }

    private static String[] buildRow(String tag, int relIdx, String filePath,
                                     GitMetrics.Metrics m, String buggy) {
        return new String[]{
                tag, String.valueOf(relIdx), filePath,
                String.valueOf(m.loc),
                String.valueOf(m.locTouched),
                String.valueOf(m.nr),
                String.valueOf(m.nFix),
                String.valueOf(m.nAuth),
                String.valueOf(m.locAdded),
                String.valueOf(m.maxLocAdded),
                String.format(Locale.US, "%.2f", m.avgLocAdded),
                String.valueOf(m.churn),
                String.valueOf(m.maxChurn),
                String.format(Locale.US, "%.2f", m.avgChurn),
                String.valueOf(m.chgSetSize),
                String.valueOf(m.maxChgSet),
                String.format(Locale.US, "%.2f", m.avgChgSet),
                String.format(Locale.US, "%.2f", m.age),
                String.format(Locale.US, "%.2f", m.weightedAge),
                String.valueOf(m.smells),
                buggy
        };
    }

    private static String findPredecessor(String tag,
                                          List<String[]> allReleases,
                                          GitMetrics git) {
        // Find the most recent release on the same major.minor branch
        // that is an actual git ancestor of this tag
        String[] parts = tag.split("\\.");
        String prefix  = parts[0] + "." + parts[1] + ".";

        String bestHash = null;
        long   bestTs   = 0;

        for (String[] candidate : allReleases) {
            if (candidate[0].equals(tag)) continue;
            if (!candidate[0].startsWith(prefix)) continue;

            // Confirm it is actually an ancestor in git
            if (!git.isAncestor(candidate[0], tag)) continue;

            long ts = Long.parseLong(candidate[1]);
            if (ts > bestTs) {
                bestTs   = ts;
                bestHash = git.resolveHash(candidate[0]);
            }
        }

        // Fallback: if no same-branch predecessor found, use first commit
        return bestHash != null ? bestHash : git.getFirstCommitHash();
    }

    private static void writeCSV(List<String[]> rows, String filePath)
            throws IOException {
        try (FileWriter fw = new FileWriter(filePath);
             CSVPrinter printer = new CSVPrinter(fw, CSVFormat.DEFAULT.withHeader(
                     CSV_HEADER))) {
            for (String[] row : rows) printer.printRecord((Object[]) row);
            printer.flush();
        }
        logger.info("    Written: " + filePath + " (" + rows.size() + " rows)");
    }


    private static void printBugStats(String label, List<String[]> rows) {
        long yes = rows.stream().filter(r -> "Yes".equals(r[r.length - 1])).count();
        System.out.printf("  %s buggy: %d / %d (%.1f%%)%n",
                label, yes, rows.size(),
                rows.isEmpty() ? 0 : 100.0 * yes / rows.size());
    }

    public static void logStopping() {
        String loggerString = """
        ┌──────────────────────────────────────────┐
        │  SYSTEM STOPPED // SHUTDOWN COMPLETE     │
        └──────────────────────────────────────────┘
        """;
        logger.info("\n" + loggerString);
    }

    public static void logStarting() {
        String loggerString = """
        ┌──────────────────────────────────────────┐
        │  SYSTEM STARTING // INITIALIZING DB      │
        └──────────────────────────────────────────┘
        """;
        logger.info("\n" + loggerString);
    }
}
