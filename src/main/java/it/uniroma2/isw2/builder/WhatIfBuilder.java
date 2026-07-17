package it.uniroma2.isw2.builder;

import it.uniroma2.isw2.enums.BalancingStrategy;
import it.uniroma2.isw2.enums.ClassifierType;
import it.uniroma2.isw2.enums.FeatureSelectionStrategy;
import it.uniroma2.isw2.model.CorrelationResult;
import it.uniroma2.isw2.model.PredictionSummary;
import it.uniroma2.isw2.model.PreventionSummary;
import it.uniroma2.isw2.utils.CSVUtil;
import it.uniroma2.isw2.weka.ClassifierFactory;
import it.uniroma2.isw2.weka.WekaLoader;
import it.uniroma2.isw2.whatif.CorrelationService;
import it.uniroma2.isw2.whatif.DatasetSplitter;
import it.uniroma2.isw2.whatif.PredictionService;
import it.uniroma2.isw2.whatif.PreventionAnalyzer;
import weka.classifiers.Classifier;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

public class WhatIfBuilder {

    public static void run(String csv, String outputDir) throws Exception {

        WekaLoader loader = new WekaLoader();
        Instances data = loader.load(csv);

        int positive = data.classAttribute().indexOfValue("Yes");

        DatasetSplitter.Split split = new DatasetSplitter().split(data);

        Classifier classifier = ClassifierFactory.build(
                        ClassifierType.RANDOM_FOREST,
                        FeatureSelectionStrategy.NONE,
                        BalancingStrategy.NONE,
                        data);

        classifier.buildClassifier(split.A());

        PredictionService predictor = new PredictionService();
        List<PredictionSummary> rows = new ArrayList<>();

        rows.add(predictor.predict(
                        "A",
                        split.A(),
                        classifier,
                        positive));

        rows.add(predictor.predict(
                        "B+",
                        split.Bplus(),
                        classifier,
                        positive));

        rows.add(predictor.predict(
                        "B",
                        split.B(),
                        classifier,
                        positive));

        rows.add(predictor.predict(
                        "C",
                        split.C(),
                        classifier,
                        positive));

        new CSVUtil().savePred(rows, outputDir+"/OPENJPA_prediction.csv");

        PreventionSummary prevention = new PreventionAnalyzer().analyse(split.Bplus(),
                                                                        split.B(),
                                                                        classifier,
                                                                        positive);

        new CSVUtil().savePrev(prevention, outputDir+"/OPENJPA_prevention.csv");

        CorrelationService correlationService = new CorrelationService();

        List<CorrelationResult> corr =
                correlationService.compute(split.A());

        new CSVUtil().saveCorrelation(corr,
                outputDir + "/OPENJPA_correlation.csv");
    }

}
