package it.uniroma2.isw2.whatif;

import it.uniroma2.isw2.model.PredictionSummary;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;

public class PredictionService {

    public PredictionSummary predict(
            String name,
            Instances data,
            Classifier classifier,
            int positive) throws Exception {

        int estimated = 0;
        int actual = 0;

        for(Instance inst : data){

            if(inst.classValue()==positive)
                actual++;

            if(classifier.classifyInstance(inst)==positive)
                estimated++;
        }

        return new PredictionSummary(
                name,
                data.numInstances(),
                actual,
                estimated);
    }

}
