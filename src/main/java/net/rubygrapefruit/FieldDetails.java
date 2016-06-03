package net.rubygrapefruit;

public class FieldDetails implements Comparable<FieldDetails> {
    private final String name;
    private final String descriptor;
    private final int access;

    public FieldDetails(int access, String name, String descriptor) {
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
    }

    @Override
    public int compareTo(FieldDetails o) {
        return getSignature().compareTo(o.getSignature());
    }

    public String getSignature() {
        return name + ' ' + descriptor;
    }

    @Override
    public String toString() {
        return getSignature();
    }

    public String getName() {
        return name;
    }
}
