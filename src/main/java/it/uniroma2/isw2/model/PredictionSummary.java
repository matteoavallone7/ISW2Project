package it.uniroma2.isw2.model;

public class PredictionSummary {

    private final String dataset;
    private final int instances;
    private final int actualBuggy;
    private final int estimatedBuggy;

    public PredictionSummary(
            String dataset,
            int instances,
            int actualBuggy,
            int estimatedBuggy) {

        this.dataset = dataset;
        this.instances = instances;
        this.actualBuggy = actualBuggy;
        this.estimatedBuggy = estimatedBuggy;
    }

    public String getDataset() {
        return dataset;
    }

    public int getInstances() {
        return instances;
    }

    public int getActualBuggy() {
        return actualBuggy;
    }

    public int getEstimatedBuggy() {
        return estimatedBuggy;
    }

}
