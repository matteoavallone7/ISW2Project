package it.uniroma2.isw2.weka;

import it.uniroma2.isw2.builder.NpofB20Service;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

import java.util.Random;
import java.util.logging.Logger;

/**
 * Runs 10-fold cross-validation (repeated 10 times) and returns the five metrics.
 * Metrics returned (indices 0–4):
 *   0 – Precision  (positive/buggy class)
 *   1 – Recall     (positive/buggy class)
 *   2 – AUC        (area under ROC, positive class)
 *   3 – Kappa      (Cohen's kappa, overall)
 *   4 – NPofB20    (bugs found in top 20% by predicted probability)
 * NaN values (produced when no instance is predicted as positive) are
 * replaced with 0.0 to avoid propagating NaN into the output CSV.
 */
public class Evaluator {

    private final NpofB20Service np;
    private static final int NUM_FOLDS = 10;
    private static final int RUNS = 10;
    private static final Logger LOGGER = Logger.getLogger(Evaluator.class.getName());

    public Evaluator(NpofB20Service np) {
        this.np = np;
    }

    public double[] evaluate(Instances data, Classifier classifier, int positiveClass) throws Exception {


        double sumPrecision = 0, sumRecall = 0, sumAuc = 0,
                sumKappa = 0, sumNpofB = 0;

        for(int run = 0; run < RUNS; run++){

            LOGGER.info("Run " + (run + 1) + "/" + RUNS);
            Evaluation eval = new Evaluation(data);
            // crossValidateModel handles its own copy of data, randomisation,
            // stratification, and fold construction internally.
            // Each fold: FilteredClassifier fits balancing + FS on training fold only,
            // then applies only FS transform on the test instance.
            eval.crossValidateModel(classifier, data, NUM_FOLDS, new Random(run));

            double precision = safe(eval.precision(positiveClass));
            double recall    = safe(eval.recall(positiveClass));
            double auc       = safe(eval.areaUnderROC(positiveClass));
            double kappa     = safe(eval.kappa());
            double npofB     = np.compute(eval, positiveClass);

            sumPrecision += precision;
            sumRecall    += recall;
            sumAuc       += auc;
            sumKappa     += kappa;
            sumNpofB     += npofB;

        }

        return new double[]{
                sumPrecision / RUNS,
                sumRecall / RUNS,
                sumAuc / RUNS,
                sumKappa / RUNS,
                sumNpofB / RUNS
        };
    }

    private double safe(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? 0.0 : v;
    }
}
