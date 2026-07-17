package it.uniroma2.isw2.utils;

import it.uniroma2.isw2.model.CorrelationResult;
import it.uniroma2.isw2.model.PredictionSummary;
import it.uniroma2.isw2.model.PreventionSummary;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

public class CSVUtil {

    public void savePred(
            List<PredictionSummary> rows,
            String file) throws Exception {

        try(PrintWriter out =
                    new PrintWriter(file)){

            out.println(
                    "Dataset,Instances,Actual_Buggy,Estimated_Buggy");

            for(PredictionSummary r : rows){

                out.printf(
                        "%s,%d,%d,%d%n",
                        r.getDataset(),
                        r.getInstances(),
                        r.getActualBuggy(),
                        r.getEstimatedBuggy());
            }
        }
    }

    public void savePrev(
            PreventionSummary p,
            String file) throws Exception {

        try(PrintWriter out =
                    new PrintWriter(file)){

            out.println("Statistic,Value");

            out.printf("Preventable,%d%n",
                    p.getPreventable());

            out.printf(Locale.US,
                    "Proportion,%.4f%n",
                    p.getProportion());

            out.printf(Locale.US,
                    "AmongPredicted,%.4f%n",
                    p.getAmongPredicted());
        }

    }

    public void saveCorrelation(
            List<CorrelationResult> results,
            String file) throws Exception {

        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {

            out.println("Feature,NSmells,Defectiveness");

            for (CorrelationResult r : results) {

                out.printf(Locale.US,
                        "%s,%.2f,%.2f%n",
                        r.getFeature(),
                        r.getSmellCorrelation(),
                        r.getDefectCorrelation());
            }
        }
    }

}
