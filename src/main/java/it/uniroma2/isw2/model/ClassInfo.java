package it.uniroma2.isw2.model;

public class ClassInfo {

    public final String  path;
    public final int     smells;
    public final int     loc;
    public final int     methods;
    public final boolean isInterface;
    public final boolean isAbstract;
    public final boolean isEnum;

    public ClassInfo(String path, int smells, int loc, int methods,
                     boolean isInterface, boolean isAbstract, boolean isEnum) {
        this.path        = path;
        this.smells      = smells;
        this.loc         = loc;
        this.methods      = methods;
        this.isInterface = isInterface;
        this.isAbstract  = isAbstract;
        this.isEnum      = isEnum;
    }

    public int getSmells() { return smells; }
    public int getLoc()    { return loc; }

}
