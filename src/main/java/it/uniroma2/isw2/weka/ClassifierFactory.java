package it.uniroma2.isw2.weka;

import it.uniroma2.isw2.enums.BalancingStrategy;
import it.uniroma2.isw2.enums.ClassifierType;
import it.uniroma2.isw2.enums.FeatureSelectionStrategy;
import weka.attributeSelection.GreedyStepwise;
import weka.attributeSelection.WrapperSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the final Weka Classifier for a given combination of
 * ClassifierType × feature-selection × balancing.
 * Feature selection: Wrapper approach using WrapperSubsetEval + GreedyStepwise
 * (forward search). Uses NaiveBayes as the internal classifier and AUC as the
 * evaluation measure, since accuracy is dominated by the majority class on
 * imbalanced data.
 * Balancing: Oversampling via Weka's Resample filter with biasToUniformClass=1.0.
 * Draws minority instances more frequently until the class distribution is
 * approximately balanced.
 * Both filters are applied inside FilteredClassifier so they are fitted
 * exclusively on the training fold of every CV split — never on the test fold.
 * This prevents data leakage.
 * Filter order when both are active: balancing first, then feature selection.
 * Rationale: feature selection should observe the balanced class distribution
 * when computing attribute scores, not the original imbalanced one.
 */

public class ClassifierFactory {

    public static Classifier build(
            ClassifierType classifierType,
            FeatureSelectionStrategy featureSelection,
            BalancingStrategy balancing,
            weka.core.Instances data
    ) {

        Classifier base = switch (classifierType) {

            case RANDOM_FOREST -> {
                RandomForest rf = new RandomForest();
                rf.setNumExecutionSlots(1);
                yield rf;
            }

            case NAIVE_BAYES -> new NaiveBayes();

            case IBK -> new IBk();
        };

        List<Filter> filters = new ArrayList<>();

        if (balancing == BalancingStrategy.OVERSAMPLING) {
            filters.add(buildOversampling(data));
        }

        if (featureSelection == FeatureSelectionStrategy.WRAPPER) {
            filters.add(buildWrapper());
        }

        if (filters.isEmpty())
            return base;

        MultiFilter multi = new MultiFilter();
        multi.setFilters(filters.toArray(new Filter[0]));

        FilteredClassifier fc = new FilteredClassifier();
        fc.setClassifier(base);
        fc.setFilter(multi);

        return fc;
    }

    /**
     * Wrapper feature selection with forward search.
     * WrapperSubsetEval trains NaiveBayes on candidate subsets and scores them
     * by AUC (more sensitive than accuracy on imbalanced data).
     * GreedyStepwise(forward) starts from no attributes and greedily adds
     * the one that improves AUC the most at each step.
     * Note: setOptions() must be called before setClassifier() — Weka's
     * OptionHandler contract resets the internal classifier in setOptions().
     */

    private static Filter buildWrapper() {

        WrapperSubsetEval eval = new WrapperSubsetEval();

        try {
            eval.setOptions(new String[]{"-E", "AUC"});
        } catch (Exception e) {
            throw new IllegalStateException("Cannot configure WrapperSubsetEval", e);
        }

        eval.setClassifier(new NaiveBayes());

        GreedyStepwise search = new GreedyStepwise();

        search.setSearchBackwards(false); // forward search

        AttributeSelection as = new AttributeSelection();

        as.setEvaluator(eval);
        as.setSearch(search);

        return as;
    }


    /**
     * Oversampling via Resample with biasToUniformClass=1.0.
     * sampleSizePercent is set so the output is approximately twice
     * the majority class count, giving each class roughly equal representation.
     */

    private static Filter buildOversampling(weka.core.Instances data) {

        int[] counts =
                data.attributeStats(data.classIndex()).nominalCounts;

        int majority = Math.max(counts[0], counts[1]);

        double percent = 2.0 * majority / data.numInstances() * 100;

        Resample r = new Resample();

        r.setBiasToUniformClass(1);

        r.setNoReplacement(false);

        r.setSampleSizePercent(percent);

        return r;
    }
}
