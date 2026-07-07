package it.uniroma2.isw2;

import it.uniroma2.isw2.builder.DatasetBuilder;



public class Main {
    private static final String REPO = "/Users/matteoavallone/openjpa";
    private static final String OUTPUT = "outputFiles";

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
                // Milestone2.run();
                break;

            case "m3":
                // Milestone3.run();
                break;

            case "m4":
                // Milestone4.run();
                break;

            default:
                System.out.println("Unknown milestone.");
        }
    }
}