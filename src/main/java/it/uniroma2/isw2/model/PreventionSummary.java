package it.uniroma2.isw2.model;

public class PreventionSummary {

    private final int preventable;
    private final double proportion;
    private final double amongPredicted;

    public PreventionSummary(
            int preventable,
            double proportion,
            double amongPredicted) {

        this.preventable = preventable;
        this.proportion = proportion;
        this.amongPredicted = amongPredicted;
    }

    public int getPreventable() {
        return preventable;
    }

    public double getProportion() {
        return proportion;
    }

    public double getAmongPredicted() {
        return amongPredicted;
    }

}
