package com.solarmc.eclipse.bytecode;

import com.solarmc.eclipse.util.IOUtils;
import com.solarmc.eclipse.util.SolarClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Applies fixes to lunar-prod.jar to allow tooling such as Asm's analyser to work on it
 *
 * @author hydos
 */
public class BytecodeFixer {

    public static void fixLunarBytecode(Map<String, byte[]> classes, JarEntry entry, JarFile jar, SolarClassLoader classLoader) {
        if (!entry.isDirectory()) {
            if (entry.getName().endsWith(".class")) {
                try (InputStream inputStream = jar.getInputStream(entry)) {
                    byte[] bytecode = IOUtils.readFully(inputStream);
                    bytecode = BytecodeFixer.fixBytecode(bytecode, classLoader);
                    classes.put(entry.getName(), bytecode);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to open InputStream for entry " + entry.getName(), e);
                }
            }
        } else {
            IOUtils.handleResource(classes, entry, jar);
        }
    }

    public static Map<String, byte[]> fixBytecode(Map<String, byte[]> classMap, SolarClassLoader classLoader) {
        Map<String, byte[]> fixedClassMap = new HashMap<>();

        for (String key : classMap.keySet()) {
            byte[] bytecode = classMap.get(key);
            bytecode = fixBytecode(bytecode, classLoader);
            fixedClassMap.put(key, bytecode);
        }
        return fixedClassMap;
    }

    /**
     * Uses ASM to visit the bytecode and apply fixes to it
     *
     * @param originalBytecode the borderline broken bytecode
     * @param classLoader      the classloader used for loading resources
     * @return fixed bytecode
     */
    public static byte[] fixBytecode(byte[] originalBytecode, SolarClassLoader classLoader) {
        ClassReader reader = new ClassReader(originalBytecode);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected ClassLoader getClassLoader() {
                return classLoader;
            }
        };
        ClassVisitor visitor = new BytecodeFixerVisitor(writer);
        reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return writer.toByteArray();
    }

    /**
     * Called when the bytecode fixer determines that the code inside the method (if any) is not relevant
     *
     * @return an exception
     */
    public static RuntimeException handleError() {
        return new RuntimeException("The method had no code so here is a placeholder.");
    }
}
