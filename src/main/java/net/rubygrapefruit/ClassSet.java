package net.rubygrapefruit;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

class ClassSet {
    private final Map<String, ClassDetails> classes = new HashMap<>();
    private final Map<String, ClassDetails> apiClasses = new TreeMap<>();

    public ClassDetails get(String name) {
        ClassDetails details = classes.get(name);
        if (details == null) {
            details = new ClassDetails(name);
            classes.put(name, details);
            if (details.getName().startsWith("org/gradle/logging") && !details.getName().contains("/internal/")) {
                apiClasses.put(name, details);
            }
        }
        return details;
    }

    public void resolveSuperTypes() {
        for (ClassDetails details : classes.values()) {
            resolveSuperTypes(details);
        }
    }

    private void resolveSuperTypes(ClassDetails details) {
        if (details.isResolved()) {
            return;
        }
        if (details.getSuperClass() != null) {
            resolveSuperTypes(details.getSuperClass());
            details.addInheritedMethods(details.getSuperClass().getMethods());
        }
        for (ClassDetails interfaceDetails : details.getInterfaces()) {
            resolveSuperTypes(interfaceDetails);
            details.addInheritedMethods(interfaceDetails.getMethods());
        }
        details.setResolved(true);
    }

    public Map<String, ClassDetails> getApiClasses() {
        return apiClasses;
    }
}
