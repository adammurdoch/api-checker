package net.rubygrapefruit;

import java.util.Set;
import java.util.TreeSet;

class ClassDetails implements Comparable<ClassDetails> {
    private final String name;
    private ClassDetails superClass;
    private final Set<ClassDetails> interfaces = new TreeSet<>();
    private final Set<MethodDetails> methods = new TreeSet<>();

    public ClassDetails(String name) {
        this.name = name;
    }

    public Set<ClassDetails> getInterfaces() {
        return interfaces;
    }

    public Set<MethodDetails> getMethods() {
        return methods;
    }

    public String getName() {
        return name;
    }

    public ClassDetails getSuperClass() {
        return superClass;
    }

    public void setSuperClass(ClassDetails superClass) {
        this.superClass = superClass;
    }

    @Override
    public boolean equals(Object obj) {
        ClassDetails other = (ClassDetails) obj;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name.replace("/", ".");
    }

    @Override
    public int compareTo(ClassDetails o) {
        return name.compareTo(o.name);
    }

    public void addMethod(String name, String descriptor) {
        methods.add(new MethodDetails(name, descriptor));
    }

    public void addInterface(ClassDetails classDetails) {
        interfaces.add(classDetails);
    }
}
