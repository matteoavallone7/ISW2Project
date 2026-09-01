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
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SpreadSubsample;
import weka.filters.supervised.instance.SMOTE;

import java.util.ArrayList;
import java.util.List;



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

        switch (balancing) {
            case OVERSAMPLING  -> filters.add(buildOversampling(data));
            case UNDERSAMPLING -> filters.add(buildUndersampling());
            case SMOTE         -> filters.add(buildSmote(data));
            case NONE          -> { /* no filter */ }
        }

        if (featureSelection == FeatureSelectionStrategy.WRAPPER) {
            filters.add(buildWrapper());
        }

        if (filters.isEmpty()) return base;

        MultiFilter multi = new MultiFilter();
        multi.setFilters(filters.toArray(new Filter[0]));

        FilteredClassifier fc = new FilteredClassifier();
        fc.setClassifier(base);
        fc.setFilter(multi);

        return fc;
    }



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

    /**
     * Undersampling via SpreadSubsample.
     * distributionSpread=1.0 means uniform class distribution:
     * majority class is reduced to match minority class count.
     * On your dataset this discards 85% of instances — expect
     * higher recall and lower precision vs oversampling.
     */
    private static Filter buildUndersampling() {
        SpreadSubsample ss = new SpreadSubsample();
        ss.setDistributionSpread(1.0);
        return ss;
    }

    private static Filter buildSmote(Instances data) {
        int[] counts  = data.attributeStats(data.classIndex()).nominalCounts;
        int majority  = Math.max(counts[0], counts[1]);
        int minority  = Math.min(counts[0], counts[1]);
        double percent = (double)(majority - minority) / minority * 100.0;
        percent = Math.max(100.0, percent); // SMOTE minimum is 100%

        SMOTE smote = new SMOTE();
        smote.setPercentage(percent);
        smote.setNearestNeighbors(5);
        return smote;
    }

}
