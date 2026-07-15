package it.uniroma2.isw2.enums;

public enum BalancingStrategy {

    NONE("No"),
    OVERSAMPLING("OverSampling");

    private final String displayName;

    BalancingStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

}
