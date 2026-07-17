package it.uniroma2.isw2.whatif;

import weka.core.Instance;
import weka.core.Instances;

public class DatasetSplitter {

    public record Split(
            Instances A,
            Instances Bplus,
            Instances B,
            Instances C) {
    }

    public Split split(Instances original) {

        Instances A = new Instances(original);

        Instances Bplus = new Instances(original,0);
        Instances C = new Instances(original,0);

        int smellIndex = original.attribute("Smells").index();

        for (Instance inst : original) {

            if(inst.value(smellIndex) > 0)
                Bplus.add(inst);

            else
                C.add(inst);
        }

        Instances B = new Instances(Bplus);

        for (Instance inst : B)
            inst.setValue(smellIndex,0);

        return new Split(A,Bplus,B,C);
    }

}

