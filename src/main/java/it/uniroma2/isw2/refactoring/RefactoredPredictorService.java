package it.uniroma2.isw2.refactoring;

import it.uniroma2.isw2.enums.BalancingStrategy;
import it.uniroma2.isw2.enums.ClassifierType;
import it.uniroma2.isw2.enums.FeatureSelectionStrategy;
import it.uniroma2.isw2.weka.ClassifierFactory;
import it.uniroma2.isw2.weka.WekaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RefactoredPredictorService {

    private static final Logger log =
            LoggerFactory.getLogger(RefactoredPredictorService.class);

    private final ClassifierType classifierType;
    private final FeatureSelectionStrategy featureSelection;
    private final BalancingStrategy balancing;

    public RefactoredPredictorService(
            ClassifierType classifierType,
            FeatureSelectionStrategy featureSelection,
            BalancingStrategy balancing) {

        this.classifierType = classifierType;
        this.featureSelection = featureSelection;
        this.balancing = balancing;
    }

    /**
     * Trains the classifier on the original dataset and predicts
     * bugginess for the refactored versions.
     */
    public void annotateWithPredictions(
            List<Map<String, String>> rows,
            String trainingCsv) throws Exception {

        // ---------------------------------------------------------
        // 1. Load original dataset
        // ---------------------------------------------------------
        WekaLoader loader = new WekaLoader();

        Instances trainingData =
                loader.load(trainingCsv);

        // ---------------------------------------------------------
        // 2. Build classifier
        // ---------------------------------------------------------
        Classifier classifier =
                ClassifierFactory.build(
                        classifierType,
                        featureSelection,
                        balancing,
                        trainingData
                );

        // ---------------------------------------------------------
        // 3. Train classifier
        // ---------------------------------------------------------
        classifier.buildClassifier(trainingData);

        log.info(
                "Trained {} | FS={} | BAL={} | instances={}",
                classifierType,
                featureSelection,
                balancing,
                trainingData.numInstances()
        );

        // ---------------------------------------------------------
        // 4. Predict every Cx row
        // ---------------------------------------------------------
        for (Map<String, String> row : rows) {

            double[] values =
                    buildInstanceValues(row, trainingData);

            DenseInstance instance =
                    new DenseInstance(1.0, values);

            instance.setDataset(trainingData);

            // Prediction: No / Yes
            double prediction =
                    classifier.classifyInstance(instance);

            String predicted =
                    trainingData.classAttribute()
                            .value((int) prediction);

            row.put("Buggy", predicted);

        }

        log.info(
                "Predictions generated for {} refactored instances",
                rows.size()
        );
    }

    private double[] buildInstanceValues(
            Map<String, String> row,
            Instances trainingData) {

        double[] values =
                new double[trainingData.numAttributes()];

        for (int i = 0;
             i < trainingData.numAttributes();
             i++) {

            // Buggy is the class attribute.
            // It is unknown for refactored versions.
            if (i == trainingData.classIndex()) {
                values[i] = Utils.missingValue();
                continue;
            }

            String attributeName =
                    trainingData.attribute(i).name();

            String value =
                    row.getOrDefault(attributeName, "0");

            try {
                values[i] =
                        value.isBlank()
                                ? 0.0
                                : Double.parseDouble(value);

            } catch (NumberFormatException e) {
                values[i] = 0.0;
            }
        }

        return values;
    }
}