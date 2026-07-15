package it.uniroma2.isw2.enums;

public enum ClassifierType {

    RANDOM_FOREST("RandomForest"),
    NAIVE_BAYES("NaiveBayes"),
    IBK("IBk");

    private final String displayName;

    ClassifierType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

}
