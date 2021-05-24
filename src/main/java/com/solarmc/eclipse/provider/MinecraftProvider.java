package com.solarmc.eclipse.provider;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.solarmc.eclipse.json.VersionJson;
import com.solarmc.eclipse.util.GradleUtils;
import com.solarmc.eclipse.util.IOUtils;
import com.solarmc.eclipse.util.minecraft.MinecraftVersionManifest;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public class MinecraftProvider {

    private static final Gson GSON = new Gson();
    private static final String VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private final Project project;
    private final String version;

    private Path vanillaJar;

    public MinecraftVersionManifest.Version mcVer;
    public VersionJson.MinecraftVersionInfo versionInfo;

    public MinecraftProvider(String minecraftVersion, Project project) {
        if (minecraftVersion == null) {
            throw new RuntimeException("No Minecraft version was specified. (null)");
        }

        this.version = minecraftVersion;
        this.project = project;
    }

    public void retrieveFiles(Path vanillaJar) {
        try {
            this.vanillaJar = vanillaJar;

            File versionManifest = new File(GradleUtils.getCacheFolder(project), "version_manifest.json");
            File versionJson = new File(GradleUtils.getCacheFolder(project), "minecraft-" + version + "-info.json");

            IOUtils.downloadIfChanged(new URL(VERSION_MANIFEST), versionManifest, project.getLogger());
            String versionManifestContent = Files.asCharSource(versionManifest, StandardCharsets.UTF_8).read();
            MinecraftVersionManifest mcVerManifest = GSON.fromJson(versionManifestContent, MinecraftVersionManifest.class);
            for (MinecraftVersionManifest.Version mcVer : mcVerManifest.minecraftVersions) {
                if (mcVer.version.equals(version)) {
                    this.mcVer = mcVer;
                    System.out.println("found minecraft " + version);
                    break;
                }
            }
            IOUtils.downloadIfChanged(new URL(mcVer.url), versionJson, project.getLogger());
            try (FileReader reader = new FileReader(versionJson)) {
                versionInfo = GSON.fromJson(reader, VersionJson.MinecraftVersionInfo.class);
            }
            project.getLogger().lifecycle("Downloading Minecraft " + version);
            downloadJars();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void downloadJars() throws IOException {
        IOUtils.downloadIfChanged(new URL(versionInfo.downloads.client.url), vanillaJar.toFile(), project.getLogger());
    }
}