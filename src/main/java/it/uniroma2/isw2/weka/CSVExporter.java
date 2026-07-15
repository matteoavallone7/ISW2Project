package it.uniroma2.isw2.weka;

import it.uniroma2.isw2.model.Result;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

public class CSVExporter {

    public static void save(List<Result> results, String filename) throws Exception {

        try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {

            out.println(
                    "Dataset,Classifier,Balancing,Feature Selection,Precision,Recall,AUC,Kappa,NPofB20");

            for (Result r : results) {

                out.printf(
                        Locale.US,
                        "OPENJPA,%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        r.getClassifier().displayName(),
                        r.getBalancing().displayName(),
                        r.getFeatureSelection().displayName(),
                        r.getPrecision(),
                        r.getRecall(),
                        r.getAuc(),
                        r.getKappa(),
                        r.getNpofb20());
            }
        }
    }

}
