package net.rubygrapefruit;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.util.ListHashMap;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class DistroFixture {
    private final File installDir;
    private final File libDir;

    public DistroFixture(File installDir) {
        this.installDir = installDir;
        libDir = new File(installDir, "lib");
        libDir.mkdirs();
    }

    public File getInstallDir() {
        return installDir;
    }

    public DistroFixture lib(String name, Closure closure) throws IOException {
        File outFile = new File(libDir, name + "-7.0.jar");
        LibraryFixture libraryFixture = new LibraryFixture(name);
        closure.setDelegate(libraryFixture);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        JarOutputStream jarFile = new JarOutputStream(new FileOutputStream(outFile));
        try {
            libraryFixture.build(jarFile);
        } finally {
            jarFile.close();
        }
        return this;
    }

    public class LibraryFixture {
        private final String name;
        private final Map<File, String> sourceFiles = new ListHashMap<>();

        public LibraryFixture(String name) {
            this.name = name;
        }

        public void source(String className, String classText) throws IOException {
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String baseName = className.substring(className.lastIndexOf('.') + 1, className.length());
            File sourceFile = new File(installDir, "tmp/" + name + "/" + packageName + "/" + baseName + ".java");
            sourceFile.getParentFile().mkdirs();
            DefaultGroovyMethods.setText(sourceFile, classText);
            sourceFiles.put(sourceFile, className);
        }

        private void build(JarOutputStream jarFile) throws IOException {
            if (sourceFiles.isEmpty()) {
                return;
            }

            File outputDir = new File(installDir, "tmp/" + name);
            outputDir.mkdirs();

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
            JavaCompiler.CompilationTask compileTask = compiler.getTask(null, fileManager, null, Arrays.asList("-d", outputDir.getAbsolutePath()), null, fileManager.getJavaFileObjects(sourceFiles.keySet().toArray(new File[0])));
            boolean ok = compileTask.call();
            if (!ok) {
                throw new IllegalArgumentException("Could not compile source files");
            }
            Files.walkFileTree(outputDir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = outputDir.toPath().relativize(file).toString();
                    jarFile.putNextEntry(new JarEntry(entryName));
                    InputStream inputStream = Files.newInputStream(file);
                    try {
                        DefaultGroovyMethods.leftShift(jarFile, inputStream);
                    } finally {
                        inputStream.close();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
