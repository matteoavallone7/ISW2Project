package it.uniroma2.isw2.builder;

import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.evaluation.Prediction;

import java.util.ArrayList;
import java.util.List;



public class NpofB20Service {

    public double compute(Evaluation eval, int positiveClass) {

        List<double[]> predictions = new ArrayList<>();

        for (Prediction p : eval.predictions()) {

            NominalPrediction np = (NominalPrediction)p;
            predictions.add(new double[]{
                    np.distribution()[positiveClass],
                    np.actual()
            });
        }

        if (predictions.isEmpty())
            return 0.0;

        // Sort by descending predicted probability of being buggy
        predictions.sort((a,b)->Double.compare(b[0],a[0]));

        int top = (int)Math.ceil(predictions.size()*0.20);

        long bugs = predictions.stream().filter(x->x[1]==positiveClass).count();

        if(bugs==0)
            return 0;

        long found = predictions.subList(0,top).stream().filter(x->x[1]==positiveClass).count();

        return (double) found / bugs;
    }
}
