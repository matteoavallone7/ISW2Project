package it.uniroma2.isw2.builder;

import it.uniroma2.isw2.model.Ticket;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Computes all class-level metrics from the git log of a repository.
 * Covers every metric required by Milestone 1 (slides 4-5).
 */
public class GitMetrics {

    private final String repoPath;

    // Regex to detect bug-fix related commit messages
    private static final Pattern FIX_PATTERN =
            Pattern.compile("\\b(fix|bug|defect|patch|issue|error)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern TICKET_PATTERN =
            Pattern.compile("OPENJPA-\\d+", Pattern.CASE_INSENSITIVE);

    private static final Logger LOGGER = Logger.getLogger(GitMetrics.class.getName());

    public GitMetrics(String repoPath) {
        this.repoPath = repoPath;
    }

    // ── Public result container ─────────────────────────────────────────────────
    public static class Metrics {
        public int    loc                  = 0;
        public long   locTouched           = 0;
        public int    nr                   = 0;
        public int    nFix                 = 0;
        public int    nAuth                = 0;
        public long   locAdded             = 0;
        public long   maxLocAdded          = 0;
        public double avgLocAdded          = 0;
        public long   churn                = 0;
        public long   maxChurn             = 0;
        public double avgChurn             = 0;
        public long   chgSetSize           = 0;
        public long   maxChgSet            = 0;
        public double avgChgSet            = 0;
        public double age                  = 0;
        public double weightedAge          = 0;
        public int    smells               = 0;
        public double entropy              = 0;
        public double modifiedFiles        = 0;
        public double modifiedDirectories  = 0;
        public long   linesDeleted         = 0;
    }

    // ── Entry point ─────────────────────────────────────────────────────────────
    /**
     * Compute metrics for a single Java file at a given release.
     *
     * @param filePath   relative path inside the repo (e.g. src/Foo.java)
     * @param prevHash   commit hash of the PREVIOUS release (null for first release)
     * @param releaseHash commit hash of THIS release
     * @param releaseTimeSec Unix timestamp (seconds) of this release
     */
    public Metrics compute(String filePath, String prevHash,
                           String releaseHash, long releaseTimeSec) {
        Metrics m = new Metrics();

        // 1. LOC at this release
        m.loc = countLoc(filePath, releaseHash);

        // 2. Per-commit stats within this release window
        List<CommitStat> stats = getCommitStats(filePath, prevHash, releaseHash);

        m.nr = stats.size();
        Set<String> authors = new HashSet<>();
        long csTotal = 0;

        for (CommitStat cs : stats) {
            authors.add(cs.author);
            if (cs.isFix) m.nFix++;

            m.locAdded    += cs.added;
            m.churn       += cs.added + cs.deleted;
            m.locTouched  += cs.added + cs.deleted;
            m.linesDeleted += cs.deleted;
            m.modifiedFiles += cs.modifiedFiles;
            m.modifiedDirectories += cs.modifiedDirectories;
            m.entropy += cs.entropy;

            if (cs.added            > m.maxLocAdded) m.maxLocAdded = cs.added;
            if (cs.added + cs.deleted > m.maxChurn)  m.maxChurn    = cs.added + cs.deleted;

            long cs2 = changeSetSize(cs.hash);
            csTotal += cs2;
            if (cs2 > m.maxChgSet) m.maxChgSet = cs2;
        }

        m.nAuth      = authors.size();
        m.chgSetSize = csTotal;

        if (m.nr > 0) {
            m.avgLocAdded = (double) m.locAdded / m.nr;
            m.avgChurn    = (double) m.churn    / m.nr;
            m.avgChgSet   = (double) csTotal    / m.nr;
            m.modifiedFiles /= m.nr;
            m.modifiedDirectories /= m.nr;
            m.entropy /= m.nr;
        }

        // 3. Age
        long first = getFileFirstCommitTimestamp(filePath, releaseHash);
        if (first <= 0) first = releaseTimeSec;

        m.age         = (releaseTimeSec - first) / 86400.0;
        m.weightedAge = m.age * m.locTouched;

        return m;
    }

    private static class FileChange {
        String path;
        long added;
        long deleted;
    }

    // ── Private inner class for per-commit data ─────────────────────────────────
    private static class CommitStat {
        String hash;
        String author;
        String subject;
        long   added;
        long   deleted;
        long timestamp;
        boolean isFix;
        int modifiedFiles;
        int modifiedDirectories;
        double entropy;
        List<FileChange> changes = new ArrayList<>();
    }

    // ── git log --numstat ───────────────────────────────────────────────────────
    private List<CommitStat> getCommitStats(String filePath,
                                            String fromHash,
                                            String toHash) {

        List<String> cmd = new ArrayList<>();

        cmd.add("git");
        cmd.add("log");

        if (fromHash != null && !fromHash.isEmpty()) {
            cmd.add(fromHash + ".." + toHash);
        } else {
            cmd.add(toHash);
        }

        cmd.add("--follow");
        cmd.add("--numstat");
        cmd.add("--pretty=format:COMMIT%x09%H%x09%ae%x09%ct%x09%s");
        cmd.add("--");
        cmd.add(filePath);

        String output = runGit(cmd);

        return parseNumstatOutput(output);
    }

    private List<CommitStat> parseNumstatOutput(String output) {
        List<CommitStat> list = new ArrayList<>();
        CommitStat current = null;

        for (String line : output.split("\n")) {
            if (line.startsWith("COMMIT\t")) {
                if (current != null) {
                    finalizeCommit(current);
                    list.add(current);
                }
                String[] parts = line.split("\t", 5);
                current = new CommitStat();
                current.hash    = parts.length > 1 ? parts[1].trim() : "";
                current.author  = parts.length > 2 ? parts[2].trim() : "";
                current.timestamp = parts.length > 3 ? Long.parseLong(parts[3]) : 0L;
                current.subject = parts.length > 4 ? parts[4] : "";
                current.isFix   = FIX_PATTERN.matcher(current.subject).find()
                        || TICKET_PATTERN.matcher(current.subject).find();
                current.added   = 0;
                current.deleted = 0;

            } else if (current != null && line.matches("^\\d+\\s+\\d+\\s+.*")) {
                String[] parts = line.split("\\s+", 3);
                try {
                    FileChange fc = new FileChange();

                    fc.path = parts[2];
                    fc.added = Long.parseLong(parts[0]);
                    fc.deleted = Long.parseLong(parts[1]);

                    current.changes.add(fc);

                    current.added += fc.added;
                    current.deleted += fc.deleted;
                } catch (NumberFormatException ignored) {}
            }
        }
        if (current != null) {
            finalizeCommit(current);
            list.add(current);
        }
        return list;
    }

    // ── LOC at tag ──────────────────────────────────────────────────────────────
    private int countLoc(String filePath, String ref) {
        String out = runGit(Arrays.asList("git", "show", ref + ":" + filePath));
        if (out.isBlank()) return 0;
        return (int) out.lines().count();
    }

    // ── Change-set size (files committed together) ──────────────────────────────
    public long changeSetSize(String commitHash) {
        String out = runGit(Arrays.asList(
                "git", "diff-tree", "--no-commit-id", "-r", "--name-only", commitHash));
        long count = 0;
        for (String l : out.split("\n")) if (!l.isBlank()) count++;
        return count;
    }

    // ── Utility: resolve a tag/ref to a commit hash ─────────────────────────────
    public String resolveHash(String ref) {
        return runGit(Arrays.asList("git", "rev-list", "-1", ref)).trim();
    }

    /** Returns all Java files tracked at the given ref. */
    public List<String> listJavaFiles(String ref) {
        String out = runGit(Arrays.asList(
                "git", "ls-tree", "-r", "--name-only", ref));
        List<String> files = new ArrayList<>();
        for (String l : out.split("\n")) {
            l = l.trim();
            if (l.endsWith(".java") && !l.contains("/test/")
                    && !l.contains("\\test\\")) {
                files.add(l);
            }
        }
        return files;
    }

    /** Returns Unix timestamp (seconds) of the commit pointed to by ref. */
    public long getTimestamp(String ref) {
        String ts = runGit(Arrays.asList(
                "git", "log", "-1", "--format=%ct", ref)).trim();
        try { return Long.parseLong(ts); } catch (NumberFormatException e) { return 0L; }
    }

    public boolean fileExists(String filePath, String ref) {

        return runGitExitZero(Arrays.asList(
                "git",
                "cat-file",
                "-e",
                ref + ":" + filePath
        ));
    }

    public boolean hasCommitsBetween(String previousTag,
                                     String currentTag) {

        if (previousTag == null) {
            return true;
        }

        String out = runGit(Arrays.asList(
                "git",
                "diff",
                "--name-only",
                previousTag,
                currentTag));

        return out.lines()
                .anyMatch(f -> f.endsWith(".java")
                        && !f.contains("/test/")
                        && !f.contains("\\test\\"));
    }

    public void linkCommitsToTickets(
            List<Ticket> tickets,
            String untilHash) {

        LOGGER.info("linkCommitsToTickets: untilHash=" + untilHash
                + " tickets=" + tickets.size());
        Map<Ticket, Pattern> patterns = new HashMap<>();
        for (Ticket t : tickets) {
            patterns.put(t, Pattern.compile(
                    Pattern.quote(t.key) + "\\b",
                    Pattern.CASE_INSENSITIVE));
        }

        String log = runGit(Arrays.asList(
                "git",
                "log",
                untilHash,
                "--pretty=format:%H%x09%ct%x09%s"));

        long lineCount = log.lines().count();
        LOGGER.info("linkCommitsToTickets: git log returned " + lineCount + " lines");

        for (String line : log.split("\n")) {
            String[] parts = line.split("\t", 3);
            if (parts.length < 3) continue;
            String hash = parts[0].trim();
            long ts;
            try { ts = Long.parseLong(parts[1].trim()); }
            catch (NumberFormatException e) { continue; }
            String msg = parts[2];

            for (Ticket t : tickets) {
                if (patterns.get(t).matcher(msg).find()) {
                    t.addFixCommit(hash, ts);
                }
            }
        }

        // Print first 5 raw lines from git log
        String[] rawLines = log.split("\n");
        LOGGER.info("First 5 raw git log lines:");
        for (int i = 0; i < Math.min(5, rawLines.length); i++) {
            LOGGER.info("  [" + i + "] '" + rawLines[i] + "'");
        }

        // Print first 5 ticket keys
        LOGGER.info("First 5 ticket keys:");
        for (int i = 0; i < Math.min(5, tickets.size()); i++) {
            LOGGER.info("  '" + tickets.get(i).key + "'");
        }

        if (rawLines.length > 0) {
            String first = rawLines[0];
            LOGGER.info("First line length: " + first.length());
            LOGGER.info("Char codes of first 50 chars:");
            StringBuilder codes = new StringBuilder();
            for (int i = 0; i < Math.min(50, first.length()); i++) {
                codes.append((int) first.charAt(i)).append(" ");
            }
            LOGGER.info("  " + codes);
        }
    }

    /** Returns all stable tags (x.y.z, no snapshot/beta/RC) sorted by date. */
    public List<String[]> getStableReleasesSortedByDate() {
        String out = runGit(Arrays.asList("git", "tag"));
        List<String[]> dated = new ArrayList<>();
        for (String tag : out.split("\n")) {
            tag = tag.trim();
            if (tag.matches(".*\\d+\\.\\d+\\.\\d+.*")
                    && !tag.toLowerCase().contains("beta")
                    && !tag.toLowerCase().contains("rc")
                    && !tag.toLowerCase().contains("snapshot")) {
                long ts = getTimestamp(tag);
                if (ts > 0) dated.add(new String[]{tag, String.valueOf(ts)});
            }
        }
        dated.sort(Comparator.comparingLong(a -> Long.parseLong(a[1])));
        return dated;
    }

    /** Returns the first-ever commit hash in the repo. */
    public String getFirstCommitHash() {
        String out = runGit(Arrays.asList(
                "git", "rev-list", "--max-parents=0", "HEAD")).trim();
        return out.split("\n")[0].trim();   // take only the first root
    }

    // ── Low-level process runner ────────────────────────────────────────────────
    public String runGit(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void finalizeCommit(CommitStat cs) {

        cs.modifiedFiles = cs.changes.size();

        Set<String> dirs = new HashSet<>();

        for (FileChange fc : cs.changes) {

            File f = new File(fc.path);

            if (f.getParent() != null)
                dirs.add(f.getParent());
        }

        cs.modifiedDirectories = dirs.size();

        long total = 0;

        for (FileChange fc : cs.changes)
            total += fc.added + fc.deleted;

        if (total == 0)
            return;

        double entropy = 0;

        for (FileChange fc : cs.changes) {

            double p = (double)(fc.added + fc.deleted) / total;

            entropy -= p * Math.log(p) / Math.log(2);
        }

        cs.entropy = entropy;
    }

    public void checkout(String ref) {

        runGit(Arrays.asList(
                "git",
                "checkout",
                "-f",
                ref
        ));
    }

    private long getFileFirstCommitTimestamp(String filePath, String releaseHash) {
        String out = runGit(Arrays.asList(
                "git", "log", "--follow", "--diff-filter=A",
                "--format=%ct", releaseHash, "--", filePath)).trim();
        // git log is newest-first, so last line is the oldest
        String[] lines = out.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String l = lines[i].trim();
            if (!l.isEmpty()) {
                try { return Long.parseLong(l); } catch (NumberFormatException ignored) {}
            }
        }
        return 0L;
    }

    public boolean isAncestor(String olderTag, String newerTag) {
        return runGitExitZero(Arrays.asList(
                "git", "merge-base", "--is-ancestor",
                resolveHash(olderTag),
                resolveHash(newerTag)));
    }


    public boolean runGitExitZero(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(repoPath));
            Process p = pb.start();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
