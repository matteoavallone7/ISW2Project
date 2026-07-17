package it.uniroma2.isw2.model;

public class CorrelationResult {

    private final String feature;
    private final double smellCorrelation;
    private final double defectCorrelation;

    public CorrelationResult(String feature, double smellCorrelation, double defectCorrelation) {
        this.feature = feature;
        this.smellCorrelation = smellCorrelation;
        this.defectCorrelation = defectCorrelation;
    }

    public String getFeature() {
        return feature;
    }

    public double getSmellCorrelation() {
        return smellCorrelation;
    }

    public double getDefectCorrelation() {
        return defectCorrelation;
    }

}
