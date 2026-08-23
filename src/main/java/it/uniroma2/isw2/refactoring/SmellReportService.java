package it.uniroma2.isw2.refactoring;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SmellReportService {

    private static final Logger LOGGER = Logger.getLogger(SmellReportService.class.getName());

    private static final String[] RULESETS = {
            "category/java/design.xml",
            "category/java/errorprone.xml",
            "category/java/bestpractices.xml",
            "category/java/codestyle.xml"
    };

    private static final int CONTEXT_LINES = 2;


    public String generateReport(File repoRoot) {
        StringBuilder out = new StringBuilder();
        PMDConfiguration config = buildConfiguration(repoRoot);

        List<RuleViolation> violations = new ArrayList<>();

        try (PmdAnalysis analysis = PmdAnalysis.create(config)) {
            for (String rs : RULESETS) {
                analysis.addRuleSet(analysis.newRuleSetLoader().loadFromResource(rs));
            }

            Report report = analysis.performAnalysisAndCollectReport();
            String root = repoRoot.getAbsolutePath();

            for (RuleViolation v : report.getViolations()) {
                String absPath = v.getFileId().getAbsolutePath();
                String relPath = toRelative(root, absPath);

                if (isTestFile(relPath)) continue;

                violations.add(v);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return "PMD analysis failed: " + e.getMessage();
        }


        // Group by file so the LLM sees violations clustered per class
        Map<String, List<RuleViolation>> byFile = violations.stream()
                .collect(Collectors.groupingBy(
                        v -> toRelative(repoRoot.getAbsolutePath(), v.getFileId().getAbsolutePath()),
                        LinkedHashMap::new, Collectors.toList()));

        out.append("PMD Code Smell Report\n");
        out.append("Rulesets: ").append(String.join(", ", RULESETS)).append("\n");
        out.append("Files with violations: ").append(byFile.size()).append("\n");
        out.append("Total violations: ").append(violations.size()).append("\n");
        out.append("=".repeat(80)).append("\n\n");

        for (Map.Entry<String, List<RuleViolation>> entry : byFile.entrySet()) {
            String relPath = entry.getKey();
            List<RuleViolation> fileViolations = entry.getValue();
            List<String> sourceLines = readLinesQuietly(new File(repoRoot, relPath));

            out.append("FILE: ").append(relPath).append("\n");
            out.append("  ").append(fileViolations.size()).append(" violation(s)\n");
            out.append("-".repeat(80)).append("\n");

            int i = 1;
            for (RuleViolation v : fileViolations) {
                out.append(String.format("[%d] Rule: %s%n", i++, v.getRule().getName()));
                out.append("    Category: ").append(v.getRule().getRuleSetName()).append("\n");
                out.append("    Priority: ").append(v.getRule().getPriority()).append("\n");
                out.append("    Line: ").append(v.getBeginLine());
                if (v.getEndLine() != v.getBeginLine()) {
                    out.append("-").append(v.getEndLine());
                }
                out.append("\n");
                out.append("    Message: ").append(v.getDescription()).append("\n");

                String snippet = extractSnippet(sourceLines, v.getBeginLine(), v.getEndLine());
                if (!snippet.isEmpty()) {
                    out.append("    Code:\n");
                    for (String line : snippet.split("\n")) {
                        out.append("      ").append(line).append("\n");
                    }
                }
                out.append("\n");
            }
            out.append("\n");
        }

        return out.toString();
    }

    public File generateReportToDirectory(File repoRoot, String className, String outputDir) throws IOException {
        File dir = new File(outputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create output directory: " + outputDir);
        }

        String fileName = "PMD_SMELL_REPORT_" + className + ".txt";

        File outputFile = new File(dir, fileName);
        String report = generateReport(repoRoot);
        Files.writeString(outputFile.toPath(), report);
        return outputFile;
    }


    private static String baseName(String path) {
        String normalized = path.replace("\\", "/");
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    private static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private PMDConfiguration buildConfiguration(File repoRoot) {
        PMDConfiguration config = new PMDConfiguration();

        LanguageVersion javaVersion = Objects.requireNonNull(LanguageRegistry.PMD
                        .getLanguageById("java"))
                .getDefaultVersion();
        config.setDefaultLanguageVersion(javaVersion);

        File mainSrc = new File(repoRoot, "src/main/java");
        if (mainSrc.exists()) {
            config.addInputPath(mainSrc.toPath());
        } else {
            config.addInputPath(repoRoot.toPath());
        }

        List<Path> pathToExclude = new ArrayList<>();
        pathToExclude.add(new File(repoRoot, "target").toPath());
        pathToExclude.add(new File(repoRoot, "build").toPath());
        config.setExcludes(pathToExclude);

        config.setIgnoreIncrementalAnalysis(true);
        config.setThreads(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

        return config;
    }

    private String toRelative(String root, String absPath) {
        String relPath = absPath;
        if (absPath.startsWith(root)) {
            relPath = relPath.substring(root.length());
        }
        relPath = relPath.replace("\\", "/");
        while (relPath.startsWith("/")) {
            relPath = relPath.substring(1);
        }
        return relPath;
    }

    private static boolean isTestFile(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("/test/")
                || lower.contains("/tests/")
                || lower.endsWith("test.java")
                || lower.endsWith("tests.java")
                || lower.endsWith("testcase.java");
    }

    private List<String> readLinesQuietly(File file) {
        try {
            return Files.readAllLines(file.toPath());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private String extractSnippet(List<String> lines, int beginLine, int endLine) {
        if (lines.isEmpty()) return "";
        int start = Math.max(1, beginLine - CONTEXT_LINES);
        int end = Math.min(lines.size(), endLine + CONTEXT_LINES);

        StringBuilder sb = new StringBuilder();
        for (int ln = start; ln <= end; ln++) {
            String marker = (ln >= beginLine && ln <= endLine) ? ">> " : "   ";
            sb.append(marker).append(ln).append(": ").append(lines.get(ln - 1)).append("\n");
        }
        return sb.toString().stripTrailing();
    }

}
