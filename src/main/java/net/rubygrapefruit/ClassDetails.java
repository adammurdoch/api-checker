package net.rubygrapefruit;

import org.objectweb.asm.Opcodes;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

class ClassDetails implements Comparable<ClassDetails> {
    private final String name;
    private ClassDetails superClass;
    private boolean resolved;
    private final Set<ClassDetails> interfaces = new TreeSet<>();
    private final Map<String, MethodDetails> methods = new TreeMap<>();
    private int access;

    public ClassDetails(String name) {
        this.name = name;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public Set<ClassDetails> getInterfaces() {
        return interfaces;
    }

    public Set<MethodDetails> getMethods() {
        return new TreeSet<>(methods.values());
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

    public void addDeclaredMethod(String name, String descriptor) {
        MethodDetails methodDetails = new MethodDetails(name, descriptor);
        methods.put(methodDetails.getSignature(), methodDetails);
    }

    public void addInheritedMethods(Iterable<MethodDetails> methods) {
        for (MethodDetails method : methods) {
            if (!this.methods.containsKey(method.getSignature())) {
                this.methods.put(method.getSignature(), method);
            }
        }
    }

    public void addInterface(ClassDetails classDetails) {
        interfaces.add(classDetails);
    }

    public void setAccess(int access) {
        this.access = access;
    }

    public boolean isVisibleOutsidePackage() {
        return (access & 0xff & Opcodes.ACC_PUBLIC) != 0;
    }
}
