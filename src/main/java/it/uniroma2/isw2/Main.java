package it.uniroma2.isw2;

import it.uniroma2.isw2.builder.ClassRanker;
import it.uniroma2.isw2.builder.DatasetBuilder;
import it.uniroma2.isw2.builder.WekaBuilder;
import it.uniroma2.isw2.builder.WhatIfBuilder;
import it.uniroma2.isw2.refactoring.SmellReportService;

import java.io.File;


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
                break;

            default:
                System.out.println("Unknown milestone.");
        }
    }
}