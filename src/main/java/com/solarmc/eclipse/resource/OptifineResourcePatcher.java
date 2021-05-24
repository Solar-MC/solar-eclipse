package com.solarmc.eclipse.resource;

import com.nothome.delta.ByteBufferSeekableSource;
import com.nothome.delta.GDiffPatcher;
import com.solarmc.eclipse.util.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class OptifineResourcePatcher extends ResourcePatcher {
    private final Map<Pattern, String> mcInjectorRegex;

    public OptifineResourcePatcher(ClassLoader classLoader, JarFile optifineJar) {
        super(classLoader);
        new HashMap<>();
        this.mcInjectorRegex = new LinkedHashMap<>();

        try {
            this.loadPatches(optifineJar);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void loadPatches(JarFile resourcesJar) throws IOException {
        InputStream optifinePatches = IOUtils.getResourceAsStream(resourcesJar, "optifinePatches.cfg");
        if (OptifineResourcePatcher.class.desiredAssertionStatus() && optifinePatches != null) {
            throw new AssertionError("Failed to read optifine resource patch config file.");
        } else {

            String[] patches = new String(IOUtils.readFully(optifinePatches)).split("\n");
            for (String patch : patches) {
                if (!patch.startsWith("#") && patch.contains("=")) {
                    String[] splitPatch = patch.split("=");
                    if (OptifineResourcePatcher.class.desiredAssertionStatus() && splitPatch.length != 2) {
                        throw new AssertionError("Illegal pattern in Optifine patch.cfg: " + patch);
                    }
                    Pattern var7 = Pattern.compile(splitPatch[0].trim());
                    String var8 = splitPatch[1].trim();
                    this.mcInjectorRegex.put(var7, var8);
                }
            }

        }
    }

    public final InputStream patchResource(String resourceName, InputStream resourceIn) throws IOException {
        String patchFile = "patch/" + resourceName + ".xdelta";
        InputStream patchInputStream = resourceName.endsWith(".xdelta") ? null : this.classLoader.getResourceAsStream(patchFile);
        if (resourceIn == null) {
            for (Map.Entry<Pattern, String> patternStringEntry : this.mcInjectorRegex.entrySet()) {
                if ((patternStringEntry.getKey()).matcher(resourceName).matches()) {
                    resourceName = patternStringEntry.getValue();
                    resourceIn = this.classLoader.getResourceAsStream(resourceName);
                    break;
                }
            }
        }

        if (patchInputStream != null && resourceIn != null) {
            byte[] source = IOUtils.readFully(resourceIn);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            new GDiffPatcher().patch(new ByteBufferSeekableSource(source), patchInputStream, output);
            return new ByteArrayInputStream(output.toByteArray());
        }

        return resourceIn;
    }
}
