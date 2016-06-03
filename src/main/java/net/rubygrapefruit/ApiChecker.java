package net.rubygrapefruit;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarFile;

/**
 * TODO: Inherited supertypes
 * TODO: Inherited methods
 * TODO: Modifiers
 * TODO: Type parameters
 * TODO: Checked exceptions
 * TODO: Fields
 */
public class ApiChecker {
    public static void main(String[] args) throws IOException {
        new ApiChecker().run(args);
    }

    private void run(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("USAGE: <dist-before> <dist-after");
        }

        File before = new File(args[0], "lib");
        File after = new File(args[1], "lib");
        System.out.println("Comparing " + before + " to " + after);

        ClassSet classesBefore = new ClassSet();
        show(before, classesBefore);

        ClassSet classesAfter = new ClassSet();
        show(after, classesAfter);

        diff(classesBefore, classesAfter);
    }

    private void diff(ClassSet classesBefore, ClassSet classesAfter) {
        System.out.println();
        System.out.println("==== DIFF ====");
        System.out.println();

        Map<String, ClassDetails> addedClasses = new TreeMap<>(classesAfter.getApiClasses());
        addedClasses.keySet().removeAll(classesBefore.getApiClasses().keySet());
        for (ClassDetails classDetails : addedClasses.values()) {
            System.out.println("ADDED: " + classDetails);
        }

        Map<String, ClassDetails> removedClasses = new TreeMap<>(classesBefore.getApiClasses());
        removedClasses.keySet().removeAll(classesAfter.getApiClasses().keySet());
        for (ClassDetails classDetails : removedClasses.values()) {
            System.out.println("REMOVED: " + classDetails);
        }

        Set<String> retainedClasses = new TreeSet<>(classesAfter.getApiClasses().keySet());
        retainedClasses.retainAll(classesBefore.getApiClasses().keySet());
        for (String name : retainedClasses) {
            ClassDetails before = classesBefore.get(name);
            ClassDetails after = classesAfter.get(name);
            if (!before.getSuperClass().equals(after.getSuperClass())) {
                System.out.println("DIFFERENT SUPER CLASS: " + after);
            }
            if (!before.getInterfaces().equals(after.getInterfaces())) {
                System.out.println("DIFFERENT INTERFACES: " + after);
                diff(before.getInterfaces(), after.getInterfaces());
            }
            if (!before.getMethods().equals(after.getMethods())) {
                System.out.println("DIFFERENT METHODS: " + after);
                diff(before.getMethods(), after.getMethods());
            }
        }

        System.out.println();
    }

    private <T> void diff(Set<T> before, Set<T> after) {
        for (T t : before) {
            if (!after.contains(t)) {
                System.out.println("  * Removed: " + t);
            }
        }
        for (T t : after) {
            if (!before.contains(t)) {
                System.out.println("  * Added: " + t);
            }
        }
    }

    private void show(File directory, ClassSet classes) throws IOException {
        System.out.println();
        System.out.println("==== Inspecting " + directory + " ====");
        System.out.println();
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(String.format("Directory %s does not exist", directory));
        }
        for (File file : directory.listFiles()) {
            if (file.isFile()) {
                showJar(file, classes);
            }
        }

        classes.getApiClasses().values().forEach(details -> {
            System.out.println(String.format("* class: %s", details));
            System.out.println(String.format("  * superclass: %s", details.getSuperClass()));
            for (ClassDetails superType : details.getInterfaces()) {
                System.out.println(String.format("  * interface: %s", superType));
            }
            for (MethodDetails method : details.getMethods()) {
                System.out.println(String.format("  * method: %s", method));
            }
        });
    }

    private void showJar(File file, ClassSet classes) throws IOException {
        JarFile jarFile = new JarFile(file);
        try {
            jarFile.stream().filter(
                    entry -> !entry.isDirectory() && entry.getName().startsWith("org/gradle") && entry.getName()
                            .endsWith(".class")).forEach(entry -> {
                try {
                    InputStream inputStream = jarFile.getInputStream(entry);
                    try {
                        ClassReader reader = new ClassReader(inputStream);
                        ClassDetails classDetails = classes.get(reader.getClassName());
                        classDetails.setSuperClass(classes.get(reader.getSuperName()));
                        for (String name : reader.getInterfaces()) {
                            classDetails.addInterface(classes.get(name));
                        }
                        reader.accept(new ClassVisitor(Opcodes.ASM5) {
                            @Override
                            public void visit(int version, int access, String name, String signature, String superName,
                                              String[] interfaces) {
                            }

                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                                             String[] exceptions) {
                                classDetails.addMethod(name, desc);
                                return super.visitMethod(access, name, desc, signature, exceptions);
                            }
                        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    } finally {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            jarFile.close();
        }
    }

    static class ClassSet {
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

        public Map<String, ClassDetails> getApiClasses() {
            return apiClasses;
        }
    }
}
