package net.rubygrapefruit;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
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
        JarOutputStream jarFile = new JarOutputStream(new FileOutputStream(outFile));
        try {
            closure.setDelegate(new LibraryFixture(jarFile));
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.call();
        } finally {
            jarFile.close();
        }
        return this;
    }

    public class LibraryFixture {
        private final JarOutputStream jarFile;

        public LibraryFixture(JarOutputStream jarFile) {
            this.jarFile = jarFile;
        }

        public void source(String className, String classText) throws IOException {
            File sourceFile = new File(installDir, "tmp/" + className + ".java");
            File outputDir = new File(installDir, "tmp/" + className);
            outputDir.mkdirs();
            sourceFile.getParentFile().mkdirs();
            DefaultGroovyMethods.setText(sourceFile, classText);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
            JavaCompiler.CompilationTask compileTask = compiler.getTask(null, fileManager, null, Arrays.asList("-d", outputDir.getAbsolutePath()), null, fileManager.getJavaFileObjects(sourceFile));
            compileTask.call();

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
