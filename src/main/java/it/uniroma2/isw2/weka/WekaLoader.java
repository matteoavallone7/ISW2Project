package it.uniroma2.isw2.weka;

import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.util.logging.Logger;

public class WekaLoader {

    private static final Logger LOGGER = Logger.getLogger(WekaLoader.class.getName());

    public Instances load(String csv) throws Exception {

        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csv));

        Instances data = loader.getDataSet();

        LOGGER.info(() ->
                "Loaded " + data.numInstances()
                        + " instances, "
                        + data.numAttributes()
                        + " attributes from "
                        + csv);


        // Remove non-predictive string columns by name: Version, VersionIndex, File Name
        removeAttributeByName(data, "Version");
        removeAttributeByName(data, "VersionIndex");
        removeAttributeByName(data, "File Name");

        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }
        // Set the last attribute (Buggy) as the class
        if (!data.classAttribute().isNominal()) {
            throw new IllegalStateException("Buggy must be a nominal attribute.");
        }

        LOGGER.info(() ->
                "After cleanup: " + data.numAttributes()
                        + " attributes, class = "
                        + data.classAttribute().name());


        return data;
    }


    private void removeAttributeByName(Instances data, String name) {
        int idx = data.attribute(name) != null
                ? data.attribute(name).index() : -1;
        if (idx >= 0) data.deleteAttributeAt(idx);
    }

}
