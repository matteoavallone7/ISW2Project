package it.uniroma2.isw2.enums;

public enum FeatureSelectionStrategy {

    NONE("No"),

    WRAPPER("Wrapper");

    private final String displayName;

    FeatureSelectionStrategy(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

}
