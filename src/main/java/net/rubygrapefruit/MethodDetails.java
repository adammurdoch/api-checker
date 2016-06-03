package net.rubygrapefruit;

public class MethodDetails implements Comparable<MethodDetails> {
    private final int access;
    private final String name;
    private final String descriptor;

    public MethodDetails(int access, String name, String descriptor) {
        this.access = access;
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

    public String getSignature() {
        return name + descriptor;
    }

    public boolean isVisibleOutsidePackage() {
        Visibility visibility = Visibility.fromAccessField(access);
        return visibility == Visibility.Public || visibility == Visibility.Protected;
    }
}
