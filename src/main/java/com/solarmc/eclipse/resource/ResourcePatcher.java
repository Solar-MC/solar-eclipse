package com.solarmc.eclipse.resource;

import java.io.IOException;
import java.util.jar.JarFile;

public abstract class ResourcePatcher {

    public final ClassLoader classLoader;

    public ResourcePatcher(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public abstract void loadPatches(JarFile resourcesJar) throws IOException;
}
