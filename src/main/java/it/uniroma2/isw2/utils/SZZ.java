package it.uniroma2.isw2.utils;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.*;
import it.uniroma2.isw2.builder.GitMetrics;
import it.uniroma2.isw2.model.Ticket;


public class SZZ {

    private static final Logger logger = Logger.getLogger(SZZ.class.getName());
    private final GitMetrics git;

    public SZZ(GitMetrics git) {
        this.git = git;
    }

    /**
     * For a single ticket, returns the set of .java file paths
     * that were modified in its fix commit(s).
     * Blame is used to confirm the lines were genuinely introduced
     * (not just comments or whitespace), filtering trivial changes.
     *
     */
    public Set<String> findBuggyFiles(Ticket ticket) {
        Set<String> buggyFiles = new HashSet<>();

        // Step 1: find fix commits referencing this ticket

        for (Ticket.CommitInfo ci : ticket.getFixCommits()) {

            // Step 2: get removed lines per file in this fix commit
            String diff = git.runGit(Arrays.asList(
                    "git", "diff-tree", "--no-commit-id",
                    "-r", "--unified=0", ci.hash));

            Map<String, Set<Integer>> fileToRemovedLines = parseRemovedLines(diff);

            for (Map.Entry<String, Set<Integer>> entry : fileToRemovedLines.entrySet()) {
                String       filePath = entry.getKey();
                Set<Integer> lines    = entry.getValue();

                if (lines.isEmpty()) {
                    // Fix only added lines (e.g. missing null check) — still count the file
                    buggyFiles.add(filePath);
                    continue;
                }

                // Step 3: blame the PARENT of the fix commit on those removed lines
                // to confirm they were genuinely introduced (not whitespace/comments)
                String blame = git.runGit(Arrays.asList(
                        "git", "blame", "--porcelain",
                        "-p", ci.hash + "^", "--", filePath));

                Set<String> introducingCommits = parseBlameForLines(blame, lines);

                // If blame found introducing commits the file is genuinely buggy
                // If blame found nothing (e.g. file was added in the fix commit itself)
                // we still include the file since it was explicitly in the fix diff
                if (!introducingCommits.isEmpty()) {
                    buggyFiles.add(filePath);
                }

                logger.fine("  " + filePath + " — introducing commits: "
                        + introducingCommits.size());
            }
        }

        return buggyFiles;
    }


    /**
     * Parses `git diff-tree --unified=0` output.
     * Returns for each .java file the set of line numbers that were REMOVED
     * (i.e. the buggy lines in the parent version, before the fix).
     */
    private Map<String, Set<Integer>> parseRemovedLines(String diff) {
        Map<String, Set<Integer>> result = new HashMap<>();
        String currentFile = null;

        for (String line : diff.split("\n")) {
            if (line.startsWith("+++ b/")) {
                currentFile = line.substring(6).trim();
                if (!currentFile.endsWith(".java")
                        || currentFile.contains("/test/")) {
                    currentFile = null;
                }
            } else if (currentFile != null && line.startsWith("@@")) {
                // Hunk header: @@ -<start>,<count> +<start>,<count> @@
                // The -start,count part = lines removed from the parent (buggy lines)
                Matcher m = Pattern.compile("@@ -(\\d+)(?:,(\\d+))?").matcher(line);
                if (m.find()) {
                    int start = Integer.parseInt(m.group(1));
                    int count = m.group(2) != null
                            ? Integer.parseInt(m.group(2)) : 1;
                    result.computeIfAbsent(currentFile, k -> new HashSet<>());
                    for (int i = start; i < start + count; i++) {
                        result.get(currentFile).add(i);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Parses `git blame --porcelain` output.
     * Returns the set of commit hashes that introduced the given line numbers.
     */
    private Set<String> parseBlameForLines(String blame, Set<Integer> targetLines) {
        Set<String> introducing = new HashSet<>();
        String currentHash = null;
        int    currentLine = 0;

        for (String line : blame.split("\n")) {
            Matcher m = Pattern.compile(
                    "^([0-9a-f]{40}) \\d+ (\\d+)").matcher(line);
            if (m.find()) {
                currentHash = m.group(1);
                currentLine = Integer.parseInt(m.group(2));
            }
            if (targetLines.contains(currentLine) && currentHash != null) {
                introducing.add(currentHash);
            }
        }
        return introducing;
    }
}
