package net.rubygrapefruit;

class MethodDetails implements Comparable<MethodDetails> {
    private final String name;
    private final String descriptor;

    public MethodDetails(String name, String descriptor) {
        this.name = name;
        this.descriptor = descriptor;
    }

    @Override
    public boolean equals(Object obj) {
        MethodDetails other = (MethodDetails) obj;
        return name.equals(other.name) && descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ descriptor.hashCode();
    }

    @Override
    public int compareTo(MethodDetails o) {
        return getSignature().compareTo(o.getSignature());
    }

    @Override
    public String toString() {
        return getSignature();
    }

    String getSignature() {
        return name + descriptor;
    }
}
