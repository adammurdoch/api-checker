package net.rubygrapefruit;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;

/**
 * TODO: Changes in inherited supertypes
 * TODO: Changes in modifiers (visibility, static, abstract, etc)
 * TODO: Change from type (class, interface, annotation, enum, etc)
 * TODO: Skip private methods
 * TODO: Changes in type parameters for types, methods, exceptions
 * TODO: Changes in checked exceptions
 * TODO: Changes in fields
 * TODO: Changes in annotations
 * TODO: Internal classes reachable from public API
 * TODO: contents of `lib/plugins`
 * TODO: report on moved classes, rather than add + remove
 * TODO: report on change to method parameters and return types, rather than add + remove
 */
public class ApiChecker {
    private final File before;
    private final File after;
    private final DiffListener diffListener;

    public ApiChecker(File before, File after, DiffListener diffListener) {
        this.before = before;
        this.after = after;
        this.diffListener = diffListener;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("USAGE: <dist-before> <dist-after");
        }

        File before = new File(args[0]);
        File after = new File(args[1]);
        new ApiChecker(before, after, new DiffReporter()).run();
    }

    public void run() throws IOException {
        System.out.println("Comparing " + before + " to " + after);

        ClassSet classesBefore = new ClassSet();
        inspect(before, classesBefore);

        ClassSet classesAfter = new ClassSet();
        inspect(after, classesAfter);

        diff(classesBefore, classesAfter);
    }

    private void diff(ClassSet classesBefore, ClassSet classesAfter) {
        System.out.println();
        System.out.println("==== DIFF ====");
        System.out.println();

        Map<String, ClassDetails> addedClasses = new TreeMap<>(classesAfter.getApiClasses());
        addedClasses.keySet().removeAll(classesBefore.getApiClasses().keySet());
        for (ClassDetails classDetails : addedClasses.values()) {
            diffListener.classAdded(classDetails);
        }

        Map<String, ClassDetails> removedClasses = new TreeMap<>(classesBefore.getApiClasses());
        removedClasses.keySet().removeAll(classesAfter.getApiClasses().keySet());
        for (ClassDetails classDetails : removedClasses.values()) {
            diffListener.classRemoved(classDetails);
        }

        Set<String> retainedClasses = new TreeSet<>(classesAfter.getApiClasses().keySet());
        retainedClasses.retainAll(classesBefore.getApiClasses().keySet());
        for (String name : retainedClasses) {
            ClassDetails before = classesBefore.get(name);
            ClassDetails after = classesAfter.get(name);
            boolean changed = false;
            if (!before.getSuperClass().equals(after.getSuperClass())) {
                diffListener.superClassChanged(before, after);
                changed = true;
            }
            if (!before.getInterfaces().equals(after.getInterfaces())) {
                changed = true;
                for (ClassDetails interfaceDetails : before.getInterfaces()) {
                    if (!after.getInterfaces().contains(interfaceDetails)) {
                        diffListener.interfaceRemoved(before, after, interfaceDetails);
                    }
                }
                for (ClassDetails interfaceDetails : after.getInterfaces()) {
                    if (!before.getInterfaces().contains(interfaceDetails)) {
                        diffListener.interfaceAdded(before, after, interfaceDetails);
                    }
                }
            }
            if (!before.getMethods().equals(after.getMethods())) {
                changed = true;
                for (MethodDetails method : before.getMethods()) {
                    if (!after.getMethods().contains(method)) {
                        diffListener.methodRemoved(before, after, method);
                    }
                }
                for (MethodDetails method : after.getMethods()) {
                    if (!before.getMethods().contains(method)) {
                        diffListener.methodAdded(before, after, method);
                    }
                }
            }
            if (changed) {
                diffListener.classChanged(before, after);
            } else {
                diffListener.classUnchanged(after);
            }
        }

        System.out.println();
    }

    private void inspect(File distroDir, ClassSet classes) throws IOException {
        if (!distroDir.isDirectory()) {
            throw new IllegalArgumentException(String.format("Directory %s does not exist", distroDir));
        }

        System.out.println();
        System.out.println("==== Inspecting " + distroDir + " ====");
        System.out.println();
        File libDir = new File(distroDir, "lib");
        if (!libDir.isDirectory()) {
            throw new IllegalArgumentException( String.format("Distribution %s does not contain a lib/ directory", distroDir));
        }

        for (File file : libDir.listFiles()) {
            if (file.isFile()) {
                showJar(file, classes);
            }
        }
        classes.resolveSuperTypes();

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
                                classDetails.addDeclaredMethod(name, desc);
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

    private static class DiffReporter extends DiffListener {
        @Override
        public void classAdded(ClassDetails details) {
            System.out.println("ADDED: " + details);
        }

        @Override
        public void classRemoved(ClassDetails details) {
            System.out.println("REMOVED: " + details);
        }

        @Override
        public void superClassChanged(ClassDetails before, ClassDetails after) {
            System.out.println("SUPER CLASS CHANGED: " + after + ", was: " + before.getSuperClass() + ", now: " + after.getSuperClass());
        }

        @Override
        public void interfaceAdded(ClassDetails before, ClassDetails after, ClassDetails addedInterface) {
            System.out.println("INTERFACE ADDED: " + after + ": " + addedInterface);
        }

        @Override
        public void interfaceRemoved(ClassDetails before, ClassDetails after, ClassDetails removedInterface) {
            System.out.println("INTERFACE REMOVED: " + after + ": " + removedInterface);
        }

        @Override
        public void methodAdded(ClassDetails before, ClassDetails after, MethodDetails addMethod) {
            System.out.println("METHOD ADDED: " + after + ": " + addMethod);
        }

        @Override
        public void methodRemoved(ClassDetails before, ClassDetails after, MethodDetails removedMethod) {
            System.out.println("METHOD REMOVED: " + after + ": " + removedMethod);
        }
    }
}
