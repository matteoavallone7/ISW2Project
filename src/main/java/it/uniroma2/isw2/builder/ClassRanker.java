package it.uniroma2.isw2.builder;

import it.uniroma2.isw2.model.ClassInfo;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClassRanker {

    private static final Logger LOGGER = Logger.getLogger(ClassRanker.class.getName());

    // ── Configuration ─────────────────────────────────────────────────────────
    private static final String  LAST_RELEASE = "4.1.1";
    private static final int     MIN_LOC      = 80;   // escludi file troppo piccoli
    private static final int     MIN_METHODS = 5;
    private static final int     MAX_METHODS = 100;
    private static final int     NAME_INITIAL_INDEX = 3; // M=13, 13 mod 5 = 3

    private final String repoPath;
    private final String outputDir;

    public ClassRanker(String repoPath, String outputDir) {
        this.repoPath  = repoPath;
        this.outputDir = outputDir;
    }


    public void run() throws Exception {
        LOGGER.info("=== Milestone 4 - Class Ranking for " + LAST_RELEASE + " ===");

        // Step 1: checkout last release
        LOGGER.info("Checking out " + LAST_RELEASE + "...");
        checkout();

        // Step 2: run PMD on the whole repo
        LOGGER.info("Running PMD...");
        PMDService pmd = new PMDService();
        Map<String, Integer> smellsMap = pmd.analyzeRepository(new File(repoPath));
        LOGGER.info("PMD found smells in " + smellsMap.size() + " files");

        // Step 3: list all Java files and enrich with LOC + class type
        LOGGER.info("Analysing Java files...");
        List<ClassInfo> classes = new ArrayList<>();

        File sourceDir = new File(repoPath, "src/main/java");

        if (!sourceDir.exists()) {
            sourceDir = new File(repoPath);
        }

        List<File> javaFiles = collectJavaFiles(sourceDir);

        for (File file : javaFiles) {

            String relativePath = new File(repoPath)
                    .toPath()
                    .relativize(file.toPath())
                    .toString()
                    .replace("\\", "/");

            int smells = smellsMap.getOrDefault(relativePath, 0);

            String content = Files.readString(file.toPath());
            if (isTestClass(relativePath, content))
                continue;

            int loc = countLoc(content);
            int methods = countMethods(content);

            boolean isInterface = isInterface(content);
            boolean isAbstract  = isAbstract(content);
            boolean isEnum      = isEnum(content);

            classes.add(new ClassInfo(
                    relativePath,
                    smells,
                    loc,
                    methods,
                    isInterface,
                    isAbstract,
                    isEnum));
        }

        LOGGER.info("Total Java files with smells: " + classes.size());

        // Step 4: filter
        List<ClassInfo> filtered = classes.stream()
                .filter(c -> c.loc >= MIN_LOC)
                .filter(c -> c.methods >= MIN_METHODS)
                .filter(c -> c.methods <= MAX_METHODS)
                .filter(c -> !c.isInterface)
                .filter(c -> !c.isAbstract)
                .filter(c -> !c.isEnum)
                .filter(c -> !isUtilityWrapper(c.path))
                // Exclude vendored Commons Collections classes
                .filter(c -> !c.path.contains("lib/util/collections"))
                // Exclude generated classes
                .filter(c -> !c.path.contains("generated"))
                // Exclude anonymous/inner class files
                .filter(c -> !new File(c.path).getName().contains("$"))
                .filter(c -> !isExample(c.path))
                .filter(c -> !isDataOrEventClass(c.path))
                .collect(Collectors.toList());

        LOGGER.info("After filtering: " + filtered.size() + " classes");

        // Step 5: rank by smells descending, then by LOC descending as tiebreaker
        filtered.sort(Comparator
                .comparingInt(ClassInfo::getSmells).reversed()
                .thenComparingInt(ClassInfo::getLoc));

        // Step 6: select the two classes
        int n = filtered.size();
        if (n < NAME_INITIAL_INDEX + 4) {
            LOGGER.severe("Not enough classes after filtering: " + n
                    + " (need at least " + (NAME_INITIAL_INDEX + 4) + ")");
            return;
        }

        ClassInfo selected1 = filtered.get(NAME_INITIAL_INDEX);         // index 3
        ClassInfo selected2 = filtered.get(n - 1 - NAME_INITIAL_INDEX); // index N-4

        LOGGER.info("=== SELECTED CLASSES ===");
        LOGGER.info("Class 1 (rank " + (NAME_INITIAL_INDEX + 1) + "): "
                + selected1.path + " — smells =" + selected1.smells
                + " LOC =" + selected1.loc);
        LOGGER.info("Class 2 (rank " + (n - NAME_INITIAL_INDEX) + "): "
                + selected2.path + " — smells =" + selected2.smells
                + " LOC =" + selected2.loc);

        new File(outputDir).mkdirs();
        writeRankedList(filtered, selected1, selected2);
        writeSelectedPair(selected1, selected2, n);

        LOGGER.info("=== Done ===");
    }


    private int countLoc(String content) {
        return (int) content.lines()
                .filter(l -> !l.isBlank())
                .count();
    }

    private boolean isInterface(String content) {
        return content.lines().anyMatch(l -> {
            String t = l.trim();
            return t.contains(" interface ") || t.startsWith("interface ");
        });
    }


    private boolean isAbstract(String content) {
        return content.lines().anyMatch(l -> {
            String t = l.trim();
            return (t.contains("abstract class ") || t.contains("abstract "))
                    && (t.contains(" class ") || t.startsWith("class "))
                    && !t.startsWith("//") && !t.startsWith("*");
        });
    }

    private boolean isDataOrEventClass(String path) {
        String name = new File(path).getName();

        if (name.endsWith("Event.java") ||
                name.endsWith("Exception.java") ||
                name.endsWith("Info.java") ||
                name.endsWith("DTO.java") ||
                name.endsWith("Key.java")) {
            return true;
        }

        try {
            String lowerContent = Files.readString(Path.of(path)).toLowerCase();
            return lowerContent.contains("extends eventobject")
                    || lowerContent.contains("extends exception")
                    || lowerContent.contains("implements serializable");
        } catch (IOException e) {
            LOGGER.warning("Could not read file for data/event class check: " + path);
            return false;
        }
    }

    private boolean isExample(String path) {
        String p = path.toLowerCase();

        return p.contains("/example/")
                || p.contains("/examples/")
                || p.contains("/trader/")
                || p.contains("/demo/")
                || p.contains("/sample/");
    }

    private boolean isEnum(String content) {
        return content.lines().anyMatch(l -> {
            String t = l.trim();
            return t.contains(" enum ") || t.startsWith("enum ");
        });
    }

    private List<File> collectJavaFiles(File dir) {

        List<File> files = new ArrayList<>();

        File[] children = dir.listFiles();

        if (children == null)
            return files;

        for (File child : children) {

            if (child.isDirectory()) {
                files.addAll(collectJavaFiles(child));
            }

            else if (child.getName().endsWith(".java")) {
                files.add(child);
            }
        }

        return files;
    }

    private boolean isUtilityWrapper(String path) {

        String fileName = new File(path).getName();

        fileName = fileName.replace(".java", "");

        return fileName.startsWith("Abstract")
                || fileName.startsWith("Unmodifiable")
                || fileName.endsWith("Decorator")
                || fileName.endsWith("Wrapper")
                || fileName.endsWith("Adapter")
                || fileName.endsWith("Proxy");
    }

    private int countMethods(String content) {

        int methods = 0;

        for (String line : content.split("\\R")) {

            String t = line.trim();

            // Skip empty lines and comments
            if (t.isEmpty()
                    || t.startsWith("//")
                    || t.startsWith("*")
                    || t.startsWith("/*")
                    || t.startsWith("@")) {
                continue;
            }

            // Must look like a method declaration
            if (t.contains("(")
                    && t.contains(")")
                    && t.endsWith("{")
                    && !t.startsWith("if")
                    && !t.startsWith("for")
                    && !t.startsWith("while")
                    && !t.startsWith("switch")
                    && !t.startsWith("catch")
                    && !t.startsWith("do")
                    && !t.startsWith("try")
                    && !t.startsWith("else")
                    && !t.contains(" class ")
                    && !t.contains(" interface ")
                    && !t.contains(" enum ")
                    && !t.contains("=")) {

                methods++;
            }
        }

        return methods;
    }

    private boolean isTestClass(String path, String content) {

        String lowerPath = path.toLowerCase();

        if (lowerPath.contains("/test/")
                || lowerPath.contains("/tests/")
                || lowerPath.contains("/src/test/")
                || lowerPath.contains("/src/tests/")) {
            return true;
        }

        String name = new File(path).getName().toLowerCase();

        if (name.endsWith("test.java")
                || name.endsWith("tests.java")
                || name.endsWith("testcase.java")) {
            return true;
        }

        String lowerContent = content.toLowerCase();

        return lowerContent.contains("extends testcase")
                || lowerContent.contains("@test")
                || lowerContent.contains("org.junit")
                || lowerContent.contains("junit.framework")
                || lowerContent.contains("org.testng");
    }


    private void writeRankedList(List<ClassInfo> ranked,
                                 ClassInfo sel1, ClassInfo sel2)
            throws IOException {
        String path = outputDir + "/OPENJPA_ranked_classes.csv";

        try (FileWriter fw = new FileWriter(path);
             CSVPrinter printer = new CSVPrinter(fw, CSVFormat.DEFAULT.withHeader(
                     "Rank", "Class", "NSmells", "LOC", "Methods", "Selected"))) {

            for (int i = 0; i < ranked.size(); i++) {
                ClassInfo c = ranked.get(i);
                String selected = "";
                if (c == sel1) selected = "YES (first+" + NAME_INITIAL_INDEX + ")";
                if (c == sel2) selected = "YES (last-" + NAME_INITIAL_INDEX + ")";

                printer.printRecord(i + 1, c.path, c.smells, c.loc, c.methods, selected);
            }
            printer.flush();
        }
        LOGGER.info("Ranked list written to " + path);
    }

    private void writeSelectedPair(ClassInfo sel1, ClassInfo sel2, int totalRanked)
            throws IOException {
        String path = outputDir + "/OPENJPA_selected_classes.csv";

        try (FileWriter fw = new FileWriter(path);
             CSVPrinter printer = new CSVPrinter(fw, CSVFormat.DEFAULT.withHeader(
                     "Selection", "Rank", "Class", "NSmells", "LOC", "Methods",
                     "SelectionFormula"))) {

            printer.printRecord(
                    "Class 1",
                    NAME_INITIAL_INDEX + 1,
                    sel1.path,
                    sel1.smells,
                    sel1.loc,
                    sel1.methods,
                    "first+" + NAME_INITIAL_INDEX + " (M=13, 13 mod 5=3)");

            printer.printRecord(
                    "Class 2",
                    totalRanked - NAME_INITIAL_INDEX,
                    sel2.path,
                    sel2.smells,
                    sel2.loc,
                    sel2.methods,
                    "last-" + NAME_INITIAL_INDEX + " (M=13, 13 mod 5=3)");

            printer.flush();
        }
        LOGGER.info("Selected pair written to " + path);
    }


    private void checkout() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "checkout", "-f", ClassRanker.LAST_RELEASE);
            pb.directory(new File(repoPath));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            LOGGER.severe("Checkout failed: " + e.getMessage());
        }
    }


}
