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
            if (!before.superClass.equals(after.superClass)) {
                System.out.println("DIFFERENT SUPER CLASS: " + after);
            }
            if (!before.interfaces.equals(after.interfaces)) {
                System.out.println("DIFFERENT INTERFACES: " + after);
            }
            if (!before.methods.equals(after.methods)) {
                System.out.println("DIFFERENT METHODS: " + after);
            }
        }

        System.out.println();
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
            System.out.println(String.format("  * superclass: %s", details.superClass));
            for (ClassDetails superType : details.interfaces) {
                System.out.println(String.format("  * interface: %s", superType));
            }
            for (MethodDetails method : details.methods) {
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
                        classDetails.superClass = classes.get(reader.getSuperName());
                        for (String name : reader.getInterfaces()) {
                            classDetails.interfaces.add(classes.get(name));
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
                if (details.name.startsWith("org/gradle/logging") && !details.name.contains("/internal/")) {
                    apiClasses.put(name, details);
                }
            }
            return details;
        }

        public Map<String, ClassDetails> getApiClasses() {
            return apiClasses;
        }
    }

    static class ClassDetails implements Comparable<ClassDetails> {
        private final String name;
        private ClassDetails superClass;
        private final Set<ClassDetails> interfaces = new TreeSet<>();
        private final Set<MethodDetails> methods = new HashSet<>();

        public ClassDetails(String name) {
            this.name = name;
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
    }

    static class MethodDetails {
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
        public String toString() {
            return getSignature();
        }

        String getSignature() {
            return name + descriptor;
        }
    }
}
