package it.uniroma2.isw2.builder;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PMDService {

    private static final Logger LOGGER = Logger.getLogger(PMDService.class.getName());

    private static final String[] RULESETS = {
            "category/java/design.xml",
            "category/java/errorprone.xml",
            "category/java/bestpractices.xml",
            "category/java/codestyle.xml"
    };

    public Map<String,Integer> analyzeRepository(File repoRoot) {

        Map<String,Integer> smells = new HashMap<>();

        PMDConfiguration config = buildConfiguration(repoRoot);

        try (PmdAnalysis analysis = PmdAnalysis.create(config)) {

            for (String rs : RULESETS) {
                analysis.addRuleSet(analysis.newRuleSetLoader().loadFromResource(rs));
            }

            Report report = analysis.performAnalysisAndCollectReport();
            String root = repoRoot.getAbsolutePath();

            for (RuleViolation v : report.getViolations()) {

                String absPath = v.getFileId().getAbsolutePath();
                String relPath = toRelative(root, absPath);
                if (isTestFile(relPath))
                    continue;
                smells.merge(relPath, 1, Integer::sum);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }

        return smells;
    }

    private PMDConfiguration buildConfiguration(File repoRoot) {
        PMDConfiguration config = new PMDConfiguration();

        LanguageVersion javaVersion = Objects.requireNonNull(LanguageRegistry.PMD
                        .getLanguageById("java"))
                .getDefaultVersion();
        config.setDefaultLanguageVersion(javaVersion);

        // Source directory: only src/main/java subtree to skip test sources
        // If the project layout differs, add more paths or use repoRoot directly.
        File mainSrc = new File(repoRoot, "src/main/java");
        if (mainSrc.exists()) {
            config.addInputPath(mainSrc.toPath());
        } else {
            // Fallback: scan the whole repo root (test filter applied later)
            config.addInputPath(repoRoot.toPath());
        }

        List<Path> pathToExclude=new ArrayList<>();
        pathToExclude.add(new File(repoRoot, "target").toPath());
        pathToExclude.add(new File(repoRoot, "build").toPath());
        config.setExcludes(pathToExclude);

        config.setIgnoreIncrementalAnalysis(true); // always do a full analysis
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
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("/test/")
                || lower.contains("/tests/")
                || lower.endsWith("test.java")
                || lower.endsWith("tests.java")
                || lower.endsWith("testcase.java");
    }
}