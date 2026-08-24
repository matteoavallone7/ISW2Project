package it.uniroma2.isw2.refactoring;

import it.uniroma2.isw2.builder.GitMetrics;
import it.uniroma2.isw2.builder.PMDService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Computes the metrics dataset for the original and refactored versions
 * of LRUMap and Filters.
 *
 * C0:
 *   All metrics are computed from the Git history and PMD analysis.
 *
 * C1, C2, C3, C4:
 *   LOC and NSmells are recomputed on the refactored source code.
 *   All other metrics are inherited from C0, since the refactoring
 *   does not have an independent Git history.
 */
public class RefactoredMetricsService {

    private static final String RELEASE = "4.1.1";

    private static final String LRU_MAP_NAME = "LRUMap";
    private static final String FILTERS_NAME = "Filters";

    private static final String LRU_MAP_GIT_PATH =
            "openjpa-lib/src/main/java/org/apache/openjpa/lib/util/LRUMap.java";

    private static final String FILTERS_GIT_PATH =
            "openjpa-kernel/src/main/java/org/apache/openjpa/kernel/Filters.java";

    private static final String LRU_MAP_MODULE = "openjpa-lib";
    private static final String FILTERS_MODULE = "openjpa-kernel";

    private final File repoDir;
    private final Path refactoredDir;

    private final GitMetrics gitMetrics;
    private final PMDService pmdService;

    /**
     * Creates the service.
     *
     * @param repoDir         root directory of the OpenJPA repository
     * @param refactoredDir   directory containing the refactored classes
     */
    public RefactoredMetricsService(File repoDir,
                                    Path refactoredDir
                                    ) {

        this.repoDir = repoDir;
        this.refactoredDir = refactoredDir;

        this.gitMetrics = new GitMetrics(repoDir.getAbsolutePath());
        this.pmdService = new PMDService();
    }

    /**
     * Computes the complete dataset.
     *
     * The returned list contains:
     *
     * LRUMap  -> C0, C1, C2, C3
     * Filters -> C0, C1, C2, C3, C4
     *
     * Every row has the same set of metrics.
     */
    public List<Map<String, String>> compute() {

        List<Map<String, String>> rows = new ArrayList<>();

        /*
         * -------------------------------------------------------------
         * LRUMap
         * -------------------------------------------------------------
         */
        Map<String, String> lruMapC0 = computeC0(
                LRU_MAP_NAME,
                LRU_MAP_GIT_PATH,
                LRU_MAP_MODULE
        );

        rows.add(lruMapC0);

        rows.addAll(computeRefactoredVersions(
                LRU_MAP_NAME,
                "lru_map",
                lruMapC0
        ));

        /*
         * -------------------------------------------------------------
         * Filters
         * -------------------------------------------------------------
         */
        Map<String, String> filtersC0 = computeC0(
                FILTERS_NAME,
                FILTERS_GIT_PATH,
                FILTERS_MODULE
        );

        rows.add(filtersC0);

        rows.addAll(computeRefactoredVersions(
                FILTERS_NAME,
                "filters",
                filtersC0
        ));

        return rows;
    }

    /**
     * Computes all metrics for the original C0 version.
     */
    private Map<String, String> computeC0(String className,
                                          String gitPath,
                                          String moduleName) {

        long releaseTimestamp = gitMetrics.getTimestamp(RELEASE);

        GitMetrics.Metrics metrics = gitMetrics.compute(
                gitPath,
                null,
                RELEASE,
                releaseTimestamp
        );

        /*
         * PMDService returns paths relative to the directory passed
         * to analyzeRepository().
         *
         * For example, for openjpa-lib:
         *
         * src/main/java/org/apache/openjpa/lib/util/LRUMap.java
         */
        File moduleDir = new File(repoDir, moduleName);

        Map<String, Integer> smells =
                pmdService.analyzeRepository(moduleDir);

        String relativePath = gitPath.substring(
                moduleName.length() + 1
        );

        int nSmells = smells.getOrDefault(relativePath, 0);

        return createRow(
                className,
                "C0",
                metrics,
                metrics.loc,
                nSmells
        );
    }

    /**
     * Computes all refactored versions of a class.
     *
     * Only LOC and NSmells are changed with respect to C0.
     * All other metrics are copied from the C0 row.
     */
    private List<Map<String, String>> computeRefactoredVersions(
            String className,
            String refactoredDirectoryName,
            Map<String, String> c0Row) {

        List<Map<String, String>> rows = new ArrayList<>();

        Path classDirectory =
                refactoredDir.resolve(refactoredDirectoryName);

        if (!Files.exists(classDirectory)) {
            throw new IllegalArgumentException(
                    "Refactored directory does not exist: "
                            + classDirectory
            );
        }

        try (var stream = Files.list(classDirectory)) {

            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString()
                    ))
                    .forEach(versionDirectory -> {

                        String version =
                                versionDirectory.getFileName().toString();

                        /*
                         * Only directories named C1, C2, C3, C4
                         * are considered.
                         */
                        if (!version.matches("C[1-4]")) {
                            return;
                        }

                        Path javaFile = findJavaFile(versionDirectory);

                        if (javaFile == null) {
                            throw new IllegalArgumentException(
                                    "No Java file found in: "
                                            + versionDirectory
                            );
                        }

                        int loc = countLoc(javaFile);

                        int nSmells = computeSmells(javaFile);

                        /*
                         * Start from C0 so that all columns are present
                         * and all process/history metrics remain identical.
                         */
                        Map<String, String> row =
                                new LinkedHashMap<>(c0Row);

                        row.put("version", version);

                        /*
                         * These are the only two metrics that change
                         * after refactoring.
                         */
                        row.put("LOC", String.valueOf(loc));
                        row.put("Smells", String.valueOf(nSmells));

                        rows.add(row);
                    });

        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot read refactored directory: "
                            + classDirectory,
                    e
            );
        }

        return rows;
    }

    /**
     * Finds the Java file inside a Cx directory.
     *
     * Example:
     *
     * refactored/lru_map/C1/LRUMap.java
     */
    private Path findJavaFile(Path versionDirectory) {

        try (var stream = Files.list(versionDirectory)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .endsWith(".java"))
                    .findFirst()
                    .orElse(null);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot read directory: "
                            + versionDirectory,
                    e
            );
        }
    }

    /**
     * Computes LOC as the number of physical lines in the Java file.
     *
     * This is consistent with GitMetrics.countLoc(), which counts
     * the lines returned by git show.
     */
    private int countLoc(Path javaFile) {

        try (var lines = Files.lines(javaFile)) {
            return (int) lines.count();

        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot read Java file: " + javaFile,
                    e
            );
        }
    }

    /**
     * Runs PMD on the directory containing a single refactored class.
     *
     * PMDService returns paths relative to the directory passed to it,
     * therefore the smell count is obtained using only the Java filename.
     */
    private int computeSmells(Path javaFile) {

        File directory = javaFile.getParent().toFile();

        Map<String, Integer> smells =
                pmdService.analyzeRepository(directory);

        String fileName =
                javaFile.getFileName().toString();

        return smells.getOrDefault(fileName, 0);
    }

    /**
     * Creates a row containing all metrics.
     */
    private Map<String, String> createRow(
            String className,
            String version,
            GitMetrics.Metrics metrics,
            int loc,
            int nSmells) {

        Map<String, String> row =
                new LinkedHashMap<>();

        row.put("class_name", className);
        row.put("version", version);

        row.put("LOC", String.valueOf(loc));
        row.put("LOC_touched", String.valueOf(metrics.locTouched));
        row.put("NR", String.valueOf(metrics.nr));
        row.put("NFix", String.valueOf(metrics.nFix));
        row.put("NAuth", String.valueOf(metrics.nAuth));

        row.put("LOC_added", String.valueOf(metrics.locAdded));
        row.put("MAX_LOC_Added",
                String.valueOf(metrics.maxLocAdded));
        row.put("AVG_LOC_Added",
                String.valueOf(metrics.avgLocAdded));

        row.put("Churn", String.valueOf(metrics.churn));
        row.put("MAX_Churn",
                String.valueOf(metrics.maxChurn));
        row.put("AVG_Churn",
                String.valueOf(metrics.avgChurn));

        row.put("ChgSetSize",
                String.valueOf(metrics.chgSetSize));
        row.put("MAX_ChgSet",
                String.valueOf(metrics.maxChgSet));
        row.put("AVG_ChgSet",
                String.valueOf(metrics.avgChgSet));

        row.put("Age", String.valueOf(metrics.age));
        row.put("WeightedAge",
                String.valueOf(metrics.weightedAge));

        row.put("Smells", String.valueOf(nSmells));

        return row;
    }

    /**
     * Writes the computed dataset to a CSV file.
     *
     * @param outputFile destination CSV file
     */
    public void writeCsv(Path outputFile) {

        List<Map<String, String>> rows = compute();

        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "No metrics were computed."
            );
        }

        try {
            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }

            try (BufferedWriter writer =
                         Files.newBufferedWriter(outputFile)) {

                /*
                 * Header
                 */
                Map<String, String> firstRow = rows.get(0);

                writer.write(String.join(",", firstRow.keySet()));
                writer.newLine();

                /*
                 * Data
                 */
                for (Map<String, String> row : rows) {

                    List<String> values = new ArrayList<>();

                    for (String value : row.values()) {
                        values.add(csvEscape(value));
                    }

                    writer.write(String.join(",", values));
                    writer.newLine();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot write CSV file: " + outputFile,
                    e
            );
        }
    }

    private String formatValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            double number = Double.parseDouble(value);

            // Interi senza .00
            if (number == Math.rint(number)) {
                return String.valueOf((long) number);
            }

            // Massimo 2 cifre decimali
            return String.format(Locale.US, "%.2f", number);

        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * Escapes a value according to CSV rules.
     */
    private String csvEscape(String value) {

        if (value == null) {
            return "";
        }

        value = formatValue(value);

        if (value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r")) {

            return "\"" +
                    value.replace("\"", "\"\"") +
                    "\"";
        }

        return value;
    }
}