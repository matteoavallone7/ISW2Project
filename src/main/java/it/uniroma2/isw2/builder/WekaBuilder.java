package it.uniroma2.isw2.builder;

import it.uniroma2.isw2.enums.BalancingStrategy;
import it.uniroma2.isw2.enums.ClassifierType;
import it.uniroma2.isw2.enums.FeatureSelectionStrategy;
import it.uniroma2.isw2.model.Result;
import it.uniroma2.isw2.weka.CSVExporter;
import it.uniroma2.isw2.weka.ClassifierFactory;
import it.uniroma2.isw2.weka.Evaluator;
import it.uniroma2.isw2.weka.WekaLoader;
import weka.classifiers.Classifier;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class WekaBuilder {

    private static final Evaluator evaluator = new Evaluator(new NpofB20Service());
    private static final Logger LOGGER = Logger.getLogger(WekaBuilder.class.getName());

    public static void run(String csvPath, String outputDir) throws Exception {

        WekaLoader loader = new WekaLoader();
        Instances data = loader.load(csvPath);
        int positiveClass = data.classAttribute().indexOfValue("Yes");

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        LOGGER.info("Running with " + threads + " worker threads.");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Result>> futures = new ArrayList<>();

        for (ClassifierType classifier : ClassifierType.values()) {

            for (FeatureSelectionStrategy fs : FeatureSelectionStrategy.values()) {
                for (BalancingStrategy bal : BalancingStrategy.values()) {
                    futures.add(pool.submit(() -> {
                        LOGGER.info(() -> "Running "
                                        + classifier
                                        + " | FS="
                                        + fs
                                        + " | BAL="
                                        + bal);

                        Classifier model = ClassifierFactory.build(classifier, fs, bal, data);
                        double[] metrics = evaluator.evaluate(data, model, positiveClass);

                        return new Result(
                                classifier,
                                fs,
                                bal,
                                metrics[0],
                                metrics[1],
                                metrics[2],
                                metrics[3],
                                metrics[4]);
                    }));
                }
            }
        }

        pool.shutdown();

        List<Result> results = new ArrayList<>();

        try {
            for (Future<Result> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    LOGGER.severe("Combination failed: " + e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.severe("Interrupted while waiting for result");
                    break;
                }
            }
        } finally {
            if (!pool.isTerminated()) pool.shutdownNow();
        }

        CSVExporter.save(results, outputDir + "/OPENJPA_classifier.csv");
        LOGGER.info("Milestone 2 completed.");
    }

}
