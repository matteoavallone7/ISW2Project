package it.uniroma2.isw2.model;

import it.uniroma2.isw2.enums.BalancingStrategy;
import it.uniroma2.isw2.enums.ClassifierType;
import it.uniroma2.isw2.enums.FeatureSelectionStrategy;

public class Result {

    private final ClassifierType classifier;
    private final FeatureSelectionStrategy featureSelection;
    private final BalancingStrategy balancing;

    private final double precision;
    private final double recall;
    private final double auc;
    private final double kappa;
    private final double npofb20;

    public Result(
            ClassifierType classifier,
            FeatureSelectionStrategy featureSelection,
            BalancingStrategy balancing,
            double precision,
            double recall,
            double auc,
            double kappa,
            double npofb20) {

        this.classifier = classifier;
        this.featureSelection = featureSelection;
        this.balancing = balancing;
        this.precision = precision;
        this.recall = recall;
        this.auc = auc;
        this.kappa = kappa;
        this.npofb20 = npofb20;
    }

    public ClassifierType getClassifier() {
        return classifier;
    }

    public FeatureSelectionStrategy getFeatureSelection() {
        return featureSelection;
    }

    public BalancingStrategy getBalancing() {
        return balancing;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getAuc() {
        return auc;
    }

    public double getKappa() {
        return kappa;
    }

    public double getNpofb20() {
        return npofb20;
    }
}
