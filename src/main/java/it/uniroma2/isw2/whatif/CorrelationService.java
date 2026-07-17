package it.uniroma2.isw2.whatif;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import it.uniroma2.isw2.model.CorrelationResult;

public class CorrelationService {

    public List<CorrelationResult> compute(Instances data) {

        List<CorrelationResult> results = new ArrayList<>();

        int smellIndex = data.attribute("Smells").index();

        double[] smells = column(data, smellIndex);
        double[] buggy = column(data, data.classIndex());

        SpearmansCorrelation corr = new SpearmansCorrelation();

        for (int i = 0; i < data.numAttributes(); i++) {

            if (i == smellIndex)
                continue;

            if (i == data.classIndex())
                continue;

            if (!data.attribute(i).isNumeric())
                continue;

            double[] values = column(data, i);

            double smellCorr = corr.correlation(values, smells);
            double defectCorr = corr.correlation(values, buggy);

            results.add(new CorrelationResult(
                            data.attribute(i).name(),
                            smellCorr,
                            defectCorr));
        }

        results.sort(Comparator.comparingDouble(
                        (CorrelationResult r) -> Math.abs(r.getSmellCorrelation()))
                .reversed());

        return results;
    }

    private double[] column(Instances data, int index) {

        double[] values = new double[data.numInstances()];

        for (int i = 0; i < data.numInstances(); i++) {
            values[i] = data.instance(i).value(index);
        }

        return values;
    }
}
