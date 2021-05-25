package com.solarmc.eclipse.provider;

import com.google.gson.Gson;
import com.solarmc.eclipse.EclipseGradleExtension;
import com.solarmc.eclipse.bytecode.BytecodeFixer;
import com.solarmc.eclipse.util.GradleUtils;
import com.solarmc.eclipse.util.IOUtils;
import com.solarmc.eclipse.util.RemappingUtils;
import com.solarmc.eclipse.util.SolarClassLoader;
import com.solarmc.eclipse.util.lunar.PatchInfo;
import org.cadixdev.lorenz.MappingSet;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarFile;

/**
 * Fuck You Lunar :)
 */
public class LunarProvider {

    private static final Gson GSON = new Gson();
    private static final String LAUNCHER_URL = "https://api.lunarclientprod.com/launcher/launch";

    public LunarLaunchResponse launchResponse;
    public PatchInfo patchInfo;

    public LunarProvider() throws IOException {
        URL url = new URL(LAUNCHER_URL);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");

        con.setDoOutput(true);
        con.getOutputStream().write(GSON.toJson(new LunarLaunchRequest()).getBytes(StandardCharsets.UTF_8));

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        this.launchResponse = GSON.fromJson(in, LunarLaunchResponse.class);
        if (!this.launchResponse.success) {
            throw new RuntimeException("Launch Response from lunar servers was a failure!");
        }
        con.disconnect();
        in.close();
    }

    public void retrieveUnmappedFiles(Project project, EclipseGradleExtension extension, MinecraftProvider minecraftProvider) throws IOException {
        Logger logger = project.getLogger();

        File hashFile = GradleUtils.getCacheFile(project, extension, getLunarProdArtifact(launchResponse).sha1).toFile();
        System.out.println("Preparing lunar version " + getLunarProdArtifact(launchResponse).sha1);
        System.out.println("===================");

        if (!hashFile.exists()) {
            // If the hash doesnt exist this version of lunar must not already be downloaded.
            logger.info("Downloading Jars");
            for (LunarArtifact lunarArtifact : launchResponse.launchTypeData.artifacts) {
                System.out.println("Downloading " + lunarArtifact.name);
                IOUtils.downloadFile(new URL(lunarArtifact.url), GradleUtils.getCacheFile(project, extension, "downloads/" + lunarArtifact.name).toFile());
            }

            Path lunarProd = GradleUtils.getCacheFile(project, extension, "downloads/lunar-prod-optifine.jar");
            Path lunarLibs = GradleUtils.getCacheFile(project, extension, "downloads/lunar-libs.jar");
            Path optifine = GradleUtils.getCacheFile(project, extension, "downloads/OptiFine.jar");
            Path vanilla = GradleUtils.getCacheFile(project, extension, "downloads/vanilla.jar");

            Path minecraftPatched = GradleUtils.getCacheFile(project, extension, "work/vanilla-patched.jar");
            Path minecraftPatchedUnClassHashed = GradleUtils.getCacheFile(project, extension, "out/vanilla-patched-remapped.jar");

            Path lunarClassHashMappings = GradleUtils.getCacheFile(project, extension, "work/lunarClassHashMappings.tiny");

            Path lunarClassRemapped = GradleUtils.getCacheFile(project, extension, "work/lunar-prod-c-remapped.jar");
            Path lunarOfficial = GradleUtils.getCacheFile(project, extension, "out/lunar-prod-official.jar");
            Path lunarBytecodeFixed = GradleUtils.getCacheFile(project, extension, "work/lunar-prod-official-patched.jar");

            if (!lunarClassRemapped.toFile().getParentFile().exists()) {
                lunarClassRemapped.toFile().getParentFile().mkdirs();
            }

            logger.info("Loading Lunar Patches");
            System.out.println("===================");
            this.patchInfo = new PatchInfo(extension.version, new JarFile(lunarProd.toFile()));

            logger.info("Doing The Irritating Stuff");
            System.out.println("===================");

            // Create the class mappings for mc
            MappingSet lunarClassHashMappingSet = RemappingUtils.createAndWriteMappingSet(patchInfo.classMappings, lunarClassHashMappings, "named", "official");

            // Setup the classpath & ClassLoader
            List<File> setupClasspath = extension.version.getDependencies(minecraftProvider);
            setupClasspath.add(lunarLibs.toFile());
            setupClasspath.add(lunarOfficial.toFile());
            setupClasspath.add(minecraftPatched.toFile());
            SolarClassLoader setupClassLoader = SolarClassLoader.of(setupClasspath, patchInfo, lunarProd);

            // Patch Minecraft & Add Optifine along with it
            Map<String, byte[]> mcClasses = new HashMap<>();

            JarFile vanillaJar = new JarFile(vanilla.toFile());
            JarFile optifineJar = new JarFile(optifine.toFile());
            vanillaJar.stream().forEach(entry -> IOUtils.appendToClassMap(mcClasses, entry, vanillaJar, patchInfo, true));
            optifineJar.stream().forEach(entry -> IOUtils.appendToClassMap(mcClasses, entry, optifineJar, patchInfo, true));
            IOUtils.createJar(mcClasses, minecraftPatched);

            // Patch Lunar's Production jar
            Map<String, byte[]> lClassMap = new HashMap<>();

            JarFile lunarProdJar = new JarFile(lunarProd.toFile());
            lunarProdJar.stream().forEach(entry -> IOUtils.lclassToClass(lClassMap, entry, lunarProdJar, setupClassLoader));
            IOUtils.createJar(lClassMap, lunarClassRemapped);

            // Remap Lunar's Production jar to official
            RemappingUtils.remapJar(
                    lunarClassHashMappingSet,
                    "named",
                    "official",
                    lunarClassRemapped,
                    lunarOfficial
            );

            // Finish Patching Lunar's Production jar
            lClassMap.clear();
            JarFile lunarOfficialJar = new JarFile(lunarOfficial.toFile());
            lunarOfficialJar.stream().forEach(entry -> IOUtils.lclassToClass(lClassMap, entry, lunarOfficialJar, setupClassLoader));
            Map<String, byte[]> lunarClasses = BytecodeFixer.fixBytecode(lClassMap, setupClassLoader);
            IOUtils.createJar(lunarClasses, lunarClassRemapped);

            hashFile.createNewFile();
        }
    }

    private LunarArtifact getLunarProdArtifact(LunarLaunchResponse launchResponse) {
        for (LunarArtifact artifact : launchResponse.launchTypeData.artifacts) {
            if (artifact.name.contains("lunar-prod")) {
                return artifact;
            }
        }
        throw new RuntimeException("Unable to find the lunar production jar");
    }

    public static class LunarLaunchResponse {
        public boolean success;
        public LaunchTypeData launchTypeData;
    }

    public static class LunarLaunchRequest {
        public String hwid = UUID.randomUUID().toString();
        public String os = "linux";
        public String arch = "x64";
        public String launcher_version = "2.6.9";
        public String version = "1.16";
        public String branch = "master";
        public String launch_type = "OFFLINE";
        public String classifier = "optifine";
    }

    public static class LaunchTypeData {
        public String main_class;
        public List<LunarArtifact> artifacts;
    }

    public static class LunarArtifact {
        public String name;
        public String sha1;
        public String url;
        public String type;
    }
}
