package it.uniroma2.isw2;

import it.uniroma2.isw2.builder.ClassRanker;
import it.uniroma2.isw2.builder.DatasetBuilder;
import it.uniroma2.isw2.builder.WekaBuilder;
import it.uniroma2.isw2.builder.WhatIfBuilder;
import it.uniroma2.isw2.enums.BalancingStrategy;
import it.uniroma2.isw2.enums.ClassifierType;
import it.uniroma2.isw2.enums.FeatureSelectionStrategy;
import it.uniroma2.isw2.refactoring.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public class Main {
    private static final String REPO = "/Users/matteoavallone/openjpa";
    private static final String OUTPUT = "outputFiles";
    private static final String SIMPLE_CLASS = "/Users/matteoavallone/openjpa/openjpa-lib/src/main/java/org/apache/openjpa/lib/util/LRUMap.java";
    private static final String COMPLEX_CLASS = "/Users/matteoavallone/openjpa/openjpa-kernel/src/main/java/org/apache/openjpa/kernel/Filters.java";

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.out.println("""
                Usage:
                  m1 - Milestone 1
                  m2 - Milestone 2
                  m3 - Milestone 3
                  m4 - Milestone 4
                """);
            return;
        }

        switch (args[0]) {

            case "m1":
                DatasetBuilder.logStarting();
                DatasetBuilder.buildDataset(REPO, OUTPUT);
                DatasetBuilder.logStopping();
                break;

            case "m2":
                WekaBuilder.run(OUTPUT + "/OPENJPA_full_dataset.csv", OUTPUT);
                break;

            case "m3":
                WhatIfBuilder.run(OUTPUT + "/OPENJPA_full_dataset.csv", OUTPUT);
                break;

            case "m4":
                ClassRanker ranker = new ClassRanker(REPO, OUTPUT);
                ranker.run();
                break;

            case "m4-refactoring":
                SmellReportService report = new SmellReportService();
                report.generateReportToDirectory(new File(SIMPLE_CLASS), "LRUMap", OUTPUT);
                report.generateReportToDirectory(new File(COMPLEX_CLASS), "Filters", OUTPUT);
                RefactoredMetricsService service =
                        new RefactoredMetricsService(
                                new File(REPO),
                                Path.of(REPO, "refactoring")
                        );

                service.writeCsv(Path.of(OUTPUT, "OPENJPA_refactoring_dataset.csv"));

                String metrics = OUTPUT + "/OPENJPA_refactoring_dataset.csv";
                // 2. Dataset originale usato per il training
                String trainingCsv = OUTPUT + "/OPENJPA_full_dataset.csv";
                RefactoredCsvLoader loader = new RefactoredCsvLoader();
                List<Map<String, String>> rows = loader.load(metrics);

                // 3. Crea il predictor
                RefactoredPredictorService predictor =
                        new RefactoredPredictorService(
                                ClassifierType.RANDOM_FOREST,
                                FeatureSelectionStrategy.NONE,
                                BalancingStrategy.NONE
                        );

                // 4. Training + predizioni sulle versioni refactored
                predictor.annotateWithPredictions(rows, trainingCsv);
                loader.write(rows, OUTPUT + "/OPENJPA_refactored_predictions.csv");

                System.out.println("3. Loading original correlations...");

                List<Map<String, String>> correlations =
                        loader.load(
                                OUTPUT + "/OPENJPA_correlation.csv"
                        );

                System.out.println("   Loaded correlations: " + correlations.size());

                // ---------------------------------------------------------
                // 4. Compute refactoring impact
                // ---------------------------------------------------------
                System.out.println("4. Computing refactoring impact...");

                RefactoringImpactService impactService = new RefactoringImpactService();

                List<Map<String, String>> impact =
                        impactService.compute(
                                rows,
                                correlations
                        );

                System.out.println("   Impact rows: " + impact.size());

                // ---------------------------------------------------------
                // 5. Write impact CSV
                // ---------------------------------------------------------
                String impactFile = OUTPUT + "/OPENJPA_refactoring_impact.csv";
                loader.write(impact, impactFile);

                System.out.println("5. Impact CSV written to: " + impactFile);
                System.out.println("=== END REFACTORING ANALYSIS ===");
                break;

            default:
                System.out.println("Unknown milestone.");
        }
    }
}