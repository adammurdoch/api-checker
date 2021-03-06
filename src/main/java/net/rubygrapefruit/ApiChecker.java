package net.rubygrapefruit;

import org.objectweb.asm.*;

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
 * TODO: Changes in modifiers (visibility, static, abstract, etc) for classes, inner classes, methods, fields
 * TODO: Change from type (class, interface, annotation, enum, etc)
 * TODO: Skip private methods
 * TODO: Changes in type parameters for types, methods, exceptions
 * TODO: Changes in checked exceptions
 * TODO: Changes in inherited fields, ignore non-visible fields
 * TODO: Changes in annotations
 * TODO: Internal classes reachable from public API
 * TODO: Classes reachable from public API but not visible
 * TODO: Incubating/deprecated changes
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

        Map<String, ClassDetails> addedClasses = new TreeMap<>(classesAfter.getVisibleApiClasses());
        addedClasses.keySet().removeAll(classesBefore.getVisibleApiClasses().keySet());
        for (ClassDetails classDetails : addedClasses.values()) {
            diffListener.classAdded(classDetails);
        }

        Map<String, ClassDetails> removedClasses = new TreeMap<>(classesBefore.getVisibleApiClasses());
        removedClasses.keySet().removeAll(classesAfter.getVisibleApiClasses().keySet());
        for (ClassDetails classDetails : removedClasses.values()) {
            diffListener.classRemoved(classDetails);
        }

        Set<String> retainedClasses = new TreeSet<>(classesAfter.getVisibleApiClasses().keySet());
        retainedClasses.retainAll(classesBefore.getVisibleApiClasses().keySet());
        for (String name : retainedClasses) {
            ClassDetails before = classesBefore.get(name);
            ClassDetails after = classesAfter.get(name);
            DiffCollector diffCollector = new DiffCollector(before, after, diffListener);
            if (!before.getSuperClass().equals(after.getSuperClass())) {
                diffCollector.changed();
                diffListener.superClassChanged(before, after);
            }
            if (!before.getInterfaces().equals(after.getInterfaces())) {
                diffCollector.changed();
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
            if (!before.getVisibleMethods().equals(after.getVisibleMethods())) {
                diffCollector.changed();
                for (MethodDetails method : before.getVisibleMethods()) {
                    if (!after.getVisibleMethods().contains(method)) {
                        diffListener.methodRemoved(before, after, method);
                    }
                }
                for (MethodDetails method : after.getVisibleMethods()) {
                    if (!before.getVisibleMethods().contains(method)) {
                        diffListener.methodAdded(before, after, method);
                    }
                }
            }
            if (!before.getVisibleFields().equals(after.getVisibleFields())) {
                diffCollector.changed();
                for (FieldDetails field : before.getVisibleFields()) {
                    if (!after.getVisibleFields().contains(field)) {
                        diffListener.fieldRemoved(before, after, field);
                    }
                }
                for (FieldDetails field : after.getVisibleFields()) {
                    if (!before.getVisibleFields().contains(field)) {
                        diffListener.fieldAdded(before, after, field);
                    }
                }
            }
            diffCollector.done();
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
        inspectDir(classes, libDir);

        File pluginsDir = new File(libDir, "plugins");
        if (!pluginsDir.isDirectory()) {
            throw new IllegalArgumentException( String.format("Distribution %s does not contain a lib/plugins/ directory", distroDir));
        }
        inspectDir(classes, pluginsDir);

        classes.resolveSuperTypes();

        classes.getVisibleApiClasses().values().forEach(details -> {
            System.out.println(String.format("* class: %s", details));
            System.out.println(String.format("  * superclass: %s", details.getSuperClass()));
            for (ClassDetails superType : details.getInterfaces()) {
                System.out.println(String.format("  * interface: %s", superType));
            }
            for (MethodDetails method : details.getMethods()) {
                System.out.println(String.format("  * method: %s", method));
            }
            for (FieldDetails field : details.getFields()) {
                System.out.println(String.format("  * field: %s", field));
            }
        });
    }

    private void inspectDir(ClassSet classes, File libDir) throws IOException {
        for (File file : libDir.listFiles()) {
            if (file.isFile()) {
                inspectJar(file, classes);
            }
        }
    }

    private void inspectJar(File file, ClassSet classes) throws IOException {
        JarFile jarFile = new JarFile(file);
        try {
            jarFile.stream().filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class")).forEach(entry -> {
                try {
                    InputStream inputStream = jarFile.getInputStream(entry);
                    try {
                        ClassReader reader = new ClassReader(inputStream);
                        ClassDetails classDetails = classes.get(reader.getClassName());
                        classDetails.setAccess(reader.getAccess());
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
                                classDetails.addDeclaredMethod(access, name, desc);
                                return null;
                            }

                            @Override
                            public FieldVisitor visitField(int access, String name, String desc, String signature,
                                                           Object value) {
                                classDetails.addDeclaredField(access, name, desc);
                                return null;
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

    private static class DiffCollector {
        final DiffListener listener;
        final ClassDetails before;
        final ClassDetails after;
        boolean changed;

        public DiffCollector(ClassDetails after, ClassDetails before, DiffListener listener) {
            this.after = after;
            this.before = before;
            this.listener = listener;
        }

        void changed() {
            if (!changed) {
                listener.classChanged(before, after);
                changed = true;
            }
        }

        void done() {
            if (!changed) {
                listener.classUnchanged(after);
            }
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
        public void classChanged(ClassDetails before, ClassDetails after) {
            System.out.println();
            System.out.println("CHANGED: " + after);
        }

        @Override
        public void superClassChanged(ClassDetails before, ClassDetails after) {
            System.out.println("  * super class changed: was: " + before.getSuperClass() + ", now: " + after.getSuperClass());
        }

        @Override
        public void interfaceAdded(ClassDetails before, ClassDetails after, ClassDetails addedInterface) {
            System.out.println("  * interface added: " + addedInterface);
        }

        @Override
        public void interfaceRemoved(ClassDetails before, ClassDetails after, ClassDetails removedInterface) {
            System.out.println("  * interface removed: " + removedInterface);
        }

        @Override
        public void methodAdded(ClassDetails before, ClassDetails after, MethodDetails addedMethod) {
            System.out.println("  * method added: " + addedMethod);
        }

        @Override
        public void methodRemoved(ClassDetails before, ClassDetails after, MethodDetails removedMethod) {
            System.out.println("  * method removed: " + removedMethod);
        }

        @Override
        public void fieldAdded(ClassDetails before, ClassDetails after, FieldDetails addedField) {
            System.out.println("  * field added: " + addedField);
        }

        @Override
        public void fieldRemoved(ClassDetails before, ClassDetails after, FieldDetails removedField) {
            System.out.println("  * field removed: " + removedField);
        }
    }
}
