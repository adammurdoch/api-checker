package net.rubygrapefruit;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ClassDetails implements Comparable<ClassDetails> {
    private final String name;
    private ClassDetails superClass;
    private boolean resolved;
    private final Set<ClassDetails> interfaces = new TreeSet<>();
    private final Map<String, MethodDetails> methods = new TreeMap<>();
    private final Set<MethodDetails> visibleMethods = new TreeSet<>();
    private final Map<String, FieldDetails> fields = new TreeMap<>();
    private final Set<FieldDetails> visibleFields = new TreeSet<>();
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

    public Set<MethodDetails> getVisibleMethods() {
        return visibleMethods;
    }

    public Set<FieldDetails> getFields() {
        return new TreeSet<>(fields.values());
    }

    public Set<FieldDetails> getVisibleFields() {
        return visibleFields;
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

    public void addDeclaredMethod(int access, String name, String descriptor) {
        MethodDetails methodDetails = new MethodDetails(access, name, descriptor);
        doAdd(methodDetails);
    }

    public void addInheritedMethods(Iterable<MethodDetails> methods) {
        for (MethodDetails method : methods) {
            if (!this.methods.containsKey(method.getSignature())) {
                doAdd(method);
            }
        }
    }

    private void doAdd(MethodDetails methodDetails) {
        methods.put(methodDetails.getSignature(), methodDetails);
        if (methodDetails.isVisibleOutsidePackage()) {
            visibleMethods.add(methodDetails);
        }
    }

    public void addInterface(ClassDetails classDetails) {
        interfaces.add(classDetails);
    }

    public void setAccess(int access) {
        this.access = access;
    }

    public boolean isVisibleOutsidePackage() {
        return Visibility.fromAccessField(access) == Visibility.Public;
    }

    public void addDeclaredField(int access, String name, String descriptor) {
        FieldDetails field = new FieldDetails(access, name, descriptor);
        fields.put(field.getName(), field);
        if (field.isVisibleOutsidePackage()) {
            visibleFields.add(field);
        }
    }
}
