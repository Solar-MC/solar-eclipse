package com.solarmc.eclipse.util.lunar;

import com.solarmc.eclipse.util.IOUtils;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;
import org.apache.commons.compress.compressors.CompressorException;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class PatchInfo {
    private static MessageDigest md5Hasher;
    public final Version version;
    public final Map<String, byte[]> classes;
    private final Map<String, byte[]> classHashes;
    public final Map<String, String> classMappings;

    public PatchInfo(Version version, JarFile lunarProdJar) {
        this.version = version;
        this.classes = new HashMap<>();
        this.classHashes = new HashMap<>();
        this.classMappings = new HashMap<>();
        String hashesPath = "patch/" + this.version.name() + "/hashes.ini";
        String patchesPath = "patch/" + this.version.name() + "/patches.txt";
        String mappingsPath = "patch/" + this.version.name() + "/mappings.txt";
        List<String> hashes = toList(IOUtils.getResourceAsStream(lunarProdJar, hashesPath));
        List<String> patches = toList(IOUtils.getResourceAsStream(lunarProdJar, patchesPath));
        List<String> mappings = toList(IOUtils.getResourceAsStream(lunarProdJar, mappingsPath));
        this.loadHashes(hashes);
        this.loadPatches(patches);
        this.loadMappings(mappings);
    }

    private static List<String> toList(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        List<String> lines = reader.lines().collect(Collectors.toList());
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lines;
    }

    private void loadPatches(List<String> patches) {
        System.out.println("Loading Patches");
        for (String patch : patches) {
            int var3 = patch.indexOf(32);
            String var4 = patch.substring(0, var3);
            patch = patch.substring(var3 + 1);
            byte[] var6 = Base64.getDecoder().decode(patch);
            this.classes.put(var4, var6);
        }
        System.out.println("Loaded " + patches.size() + " patches");
    }

    private void loadHashes(List<String> hashes) {
        System.out.println("Loading Hashes");
        String arrayHash = null;
        for (String hash : hashes) {
            if (hash.startsWith("[")) {
                arrayHash = hash.substring(1, hash.length() - 1);
            } else {
                int var4 = hash.indexOf(61);
                String var5 = hash.substring(0, var4);
                if (arrayHash != null && !arrayHash.trim().isEmpty()) {
                    var5 = arrayHash + "." + var5;
                }

                hash = hash.substring(var4 + 1);
                byte[] var7 = Base64.getDecoder().decode(hash);
                this.classHashes.put(var5, var7);
            }
        }
        System.out.println("Loaded " + hashes.size() + " hashes");
    }

    private void loadMappings(List<String> mappings) {
        System.out.println("Loading Mappings");
        for (String mapping : mappings) {
            String[] var2 = mapping.split(" ");
            this.classMappings.put(var2[0], var2[1]);
        }
        System.out.println("Loaded " + mappings.size() + " hashes");
    }

    public final boolean checkHash(String className, byte[] file) {
        String name = className
                .replace(".class", "")
                .replace("/", ".");
        byte[] hash = this.classHashes.get(name);
        if (hash == null) {
            throw new IllegalStateException("Unexpected class " + name);
        } else {
            byte[] var4 = md5Hasher.digest(file);
            return Arrays.equals(hash, var4);
        }
    }

    public Optional<byte[]> getClassBytes(String string, byte[] fileContent) {
        byte[] bytecode = this.classes.get(string.replace("/", "."));
        if (bytecode == null) {
            return Optional.empty();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Patch.patch(fileContent, bytecode, out);
            bytecode = out.toByteArray();
//            new ClassReader(fileContent).accept(visitor, 0);
            return Optional.of(bytecode);
        } catch (CompressorException | InvalidHeaderException | IOException e) {
            e.printStackTrace();
            Optional.empty();
        }

        return Optional.of(fileContent);
    }

    static {
        try {
            md5Hasher = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException var1) {
            var1.printStackTrace();
        }
    }
}
