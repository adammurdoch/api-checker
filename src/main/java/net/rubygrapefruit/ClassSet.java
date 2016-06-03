package net.rubygrapefruit;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class ClassSet {
    private final Map<String, ClassDetails> classes = new HashMap<>();
    private final Map<String, ClassDetails> apiClasses = new TreeMap<>();

    public ClassDetails get(String name) {
        ClassDetails details = classes.get(name);
        if (details == null) {
            details = new ClassDetails(name);
            classes.put(name, details);
        }
        return details;
    }

    private boolean isPublicApiType(ClassDetails details) {
        return details.getName().startsWith("org/gradle/")
                && !details.getName().contains("/internal/")
                && !details.getName().startsWith("org/gradle/launcher/")
                && !details.getName().startsWith("org/gradle/gradleplugin/")
                && !details.getName().startsWith("org/gradle/listener/")
                && !details.getName().startsWith("org/gradle/initialization/");
    }

    public void resolveSuperTypes() {
        for (ClassDetails details : classes.values()) {
            resolveSuperTypes(details);
            if (isPublicApiType(details) && details.isVisibleOutsidePackage()) {
                apiClasses.put(details.getName(), details);
            }
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

    /**
     * Classes that make up the public API
     */
    public Map<String, ClassDetails> getVisibleApiClasses() {
        return apiClasses;
    }
}
