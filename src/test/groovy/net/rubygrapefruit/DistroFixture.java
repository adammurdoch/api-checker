package net.rubygrapefruit;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.util.ListHashMap;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
            File sourceFile = new File(installDir, "tmp/" + name + "/" + className + ".java");
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
            compileTask.call();

            for (String className : sourceFiles.values()) {
                String filesName = className.replace(".", "/") + ".class";
                jarFile.putNextEntry(new JarEntry(filesName));
                FileInputStream inputStream = new FileInputStream(new File(outputDir, filesName));
                try {
                    DefaultGroovyMethods.leftShift(jarFile, inputStream);
                } finally {
                    inputStream.close();
                }
            }
        }
    }
}
