package com.solarmc.eclipse.util;

import com.solarmc.eclipse.resource.OptifineResourcePatcher;
import com.solarmc.eclipse.resource.ResourcePatcher;
import com.solarmc.eclipse.util.lunar.PatchInfo;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

public class SolarClassLoader extends URLClassLoader {

    private final PatchInfo info;
    private final ResourcePatcher patcher;

    public SolarClassLoader(URL[] classpath, ClassLoader parent, PatchInfo info, JarFile optifineJar) {
        super(classpath, parent);
        this.info = info;
        this.patcher = info.version.optifineAvailable ? new OptifineResourcePatcher(this, optifineJar) : null;
    }

    public static SolarClassLoader of(List<File> classpath, PatchInfo info, Path optifineJar) {
        URL[] urls = classpath.stream().map(file -> {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException("Couldn't convert file to URL: " + file.getAbsolutePath(), e);
            }
        }).toArray(URL[]::new);
        try {
            return new SolarClassLoader(urls, info.getClass().getClassLoader(), info, new JarFile(optifineJar.toFile()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to turn optifine path into jar", e);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        name = this.info.classMappings.getOrDefault(name, name);
        InputStream stream = super.getResourceAsStream(name);

        if (name.endsWith(".class") && name.contains("lwjgl")) {
            String rawName = name.substring(0, name.length() - ".class".length());

            if (stream == null) {
                return super.getResourceAsStream(rawName + ".lclass");
            } else {
                byte[] originalBytes;

                try {
                    originalBytes = IOUtils.readFully(stream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                if (!this.info.checkHash(name, originalBytes)) {
                    if (name.contains("org/lwjgl/system")) {
                        System.err.println("Class didn't pass hash check: " + name);
                    } else {
                        throw new RuntimeException("Class didn't pass hash check: " + name);
                    }
                }
                return new ByteArrayInputStream(originalBytes);
            }
        }
        if (name.endsWith(".class") | name.endsWith(".lclass")) {
            return stream;
        } else {
            return tryOptifineInputStream(name, stream);
        }
    }

    private InputStream tryOptifineInputStream(String resource, InputStream in) {
        try {
            return ((OptifineResourcePatcher) patcher).patchResource(resource, in);
        } catch (IOException e) {
            e.printStackTrace();
            return in;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        name = this.info.classMappings.getOrDefault(name, name);

        String location = name.replace('.', '/') + ".class";
        String replacementLocation = name.replace('.', '/') + ".lclass";

        byte[] bytecode;
        try {
            byte[] replacementBytecode = IOUtils.readFully(getResourceAsStream(replacementLocation));

            if (replacementBytecode != null) {
                bytecode = replacementBytecode;
            } else {
                byte[] vanillaBytes = IOUtils.readFully(getResourceAsStream(location));
                if (vanillaBytes == null) {
                    vanillaBytes = IOUtils.readFully(super.getResourceAsStream(location));
                }
                bytecode = vanillaBytes;
            }

        } catch (IOException e) {
            throw new ClassNotFoundException("Could not find class " + name, e);
        }

        if (bytecode == null) {
            System.err.println("Bytecode is for class " + name + " is null (Missing Dependency?)");
            return null;
        }

        bytecode = RemappingUtils.remap(info, bytecode);
        return defineClass(name, bytecode, 0, bytecode.length);
    }
}
