package it.uniroma2.isw2.whatif;

import it.uniroma2.isw2.model.PreventionSummary;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;

public class PreventionAnalyzer {

    public PreventionSummary analyse(
            Instances before,
            Instances after,
            Classifier classifier,
            int positive) throws Exception {

        int preventable = 0;
        int predictedBefore = 0;

        for(int i=0; i<before.numInstances(); i++){

            Instance bPlus = before.instance(i);
            Instance b = after.instance(i);

            boolean buggyBefore =
                    classifier.classifyInstance(bPlus)==positive;

            boolean buggyAfter =
                    classifier.classifyInstance(b)==positive;

            if(buggyBefore)
                predictedBefore++;

            if(buggyBefore && !buggyAfter)
                preventable++;
        }

        double proportion = (double)preventable / before.numInstances();

        double amongPredicted = predictedBefore==0 ? 0 : (double)preventable / predictedBefore;

        return new PreventionSummary(
                preventable,
                proportion,
                amongPredicted);
    }

}
