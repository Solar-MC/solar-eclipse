package com.solarmc.eclipse.util.lunar;

import com.solarmc.eclipse.json.VersionJson;
import com.solarmc.eclipse.provider.MinecraftProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public enum Version {
    v1_7("1.7.10", "1.7", true),
    v1_8("1.8.9", "1.8", true),
    v1_12("1.12.2", "1.12", true),
    v1_15("1.15.2", "1.15", true),
    v1_16("1.16.5", "1.16", true);

    public final String vanillaVersion;
    public final String simpleVersion;
    public final boolean optifineAvailable;

    Version(String vanillaVersion, String simpleVersion, boolean optifineAvailable) {
        this.simpleVersion = simpleVersion;
        this.vanillaVersion = vanillaVersion;
        this.optifineAvailable = optifineAvailable;
    }

    public String toString() {
        return this.vanillaVersion;
    }

    public File getVanillaJar() {
        return new File(getVanillaDir(), "versions/" + this.vanillaVersion + "/" + this.vanillaVersion + ".jar");
    }

    public static Version fromString(String vanillaVersion) {
        for (Version version : Version.values()) {
            if (version.vanillaVersion.equals(vanillaVersion)) return version;
            if (version.simpleVersion.equals(vanillaVersion)) return version;
        }
        return getLatest();
    }

    public static Version getLatest() {
        Version[] versions = Version.values();
        return versions[versions.length - 1];
    }

    public List<File> getDependencies(MinecraftProvider minecraftProvider) {
        ArrayList<File> libraries = new ArrayList<>();

        VersionJson.MinecraftVersionInfo versionJson = minecraftProvider.versionInfo;

        byte[] bytes;
        try {
            File vanillaJar = this.getVanillaJar();
            if (!vanillaJar.exists()) {
                vanillaJar.getParentFile().mkdirs();
                bytes = connect(new URL(versionJson.downloads.client.url), 0);
                Files.write(vanillaJar.toPath(), bytes);
            }
        } catch (IOException var10) {
            var10.printStackTrace();
        }

        for (VersionJson.MinecraftLibrary library : versionJson.libraries) {
            try {
                String dependencyName = library.name;
                VersionJson.MinecraftDownloads downloads = library.downloads;
                VersionJson.ArtifactDownload artifact = library.downloads.artifact;
                String libPath = artifact.path;
                String libUrl = artifact.url;
                if (this == v1_7 && libPath.contains("2.9.1")) {
                    libPath = libPath.replaceAll("2.9.1", "2.9.4-nightly-20150209");
                    libUrl = libUrl.replaceAll("2.9.1", "2.9.4-nightly-20150209");
                } else {
                    if (this == v1_8 && libPath.contains("2.9.2-nightly-20140822") || (this == v1_15 || this == v1_16) && libPath.contains("3.2.1")) {
                        continue;
                    }

                    if (this == v1_8 && libPath.contains("jna-3.4.0")) {
                        libPath = libPath.replaceAll("3.4.0", "4.4.0");
                        libUrl = libUrl.replaceAll("3.4.0", "4.4.0");
                    } else if (this == v1_12 && libPath.contains("lwjgl-2.9.2-nightly-20140822") || this == v1_12 && libPath.contains("fastutil-7.1.0")) {
                        continue;
                    }
                }

                System.out.println("Locating dependency \"" + dependencyName + "\": " + libPath);
                File libFile = new File(getVanillaDir(), "libraries/" + libPath);
                if (!(libFile).exists()) {
                    libFile.getParentFile().mkdirs();
                    byte[] libDownload = connect(new URL(libUrl), 0);
                    Files.write(libFile.toPath(), libDownload);
                }

                libraries.add(libFile);
                if (downloads.classifiers != null) {
                    VersionJson.Classifiers classifiers = downloads.classifiers;
                    if ((libPath = System.getProperty("os.name").toLowerCase()).contains("win")) {
                        artifact = classifiers.windowsNatives;
                    } else if (libPath.contains("mac")) {
                        if (this != v1_7 && this != v1_8 && this != v1_12) {
                            artifact = classifiers.macosNatives;
                        } else {
                            artifact = classifiers.osxNatives;
                        }
                    } else {
                        if (!libPath.contains("linux")) {
                            throw new RuntimeException("Failed to determine natives name for OS: " + libPath);
                        }

                        artifact = classifiers.linuxNatives;
                    }

                    String var12 = artifact.path;
                    System.out.println("Finding native \"" + dependencyName + "\": " + var12);
                    dependencyName = artifact.url;
                    File nativeFile = new File(getVanillaDir(), "libraries/" + var12);
                    if (!nativeFile.exists()) {
                        byte[] nativeFileBytes = connect(new URL(dependencyName), 0);
                        Files.write(nativeFile.toPath(), nativeFileBytes);
                    }

                    libraries.add(nativeFile);
                }
            } catch (IllegalStateException | IOException | NullPointerException var11) {
                System.out.println("Couldn't get Minecraft library artifact for " + library.name);
            }
        }

        File lib;
        if (this.ordinal() <= v1_12.ordinal()) {
            System.out.println("Adding dependency FAST UTIL");
            if (!(lib = new File(getVanillaDir(), "libraries/it/unimi/dsi/fastutil/8.2.1/fastutil-8.2.1.jar")).exists()) {
                lib.getParentFile().mkdirs();

                try {
                    bytes = connect(new URL("https://libraries.minecraft.net/it/unimi/dsi/fastutil/8.2.1/fastutil-8.2.1.jar"), 0);
                    Files.write(lib.toPath(), bytes);
                } catch (IOException var9) {
                    throw new RuntimeException("Couldn't download FastUtil on " + this.name(), var9);
                }
            }

            libraries.add(lib);
        }

        if (this == v1_7) {
            System.out.println("Adding dependency JNA");
            if (!(lib = new File(getVanillaDir(), "libraries/net/java/dev/jna/jna/4.4.0/jna-4.4.0.jar")).exists()) {
                lib.getParentFile().mkdirs();

                try {
                    bytes = connect(new URL("https://libraries.minecraft.net/net/java/dev/jna/jna/4.4.0/jna-4.4.0.jar"), 0);
                    Files.write(lib.toPath(), bytes);
                } catch (IOException var8) {
                    throw new RuntimeException("Couldn't download JNA 4.4 on " + this.name(), var8);
                }
            }

            libraries.add(lib);
        }

        return libraries;

    }

    private static byte[] connect(URL url, int integer) {
        if (integer >= 4) {
            return null;
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            //TODO:Come up with better name
            InputStream connectionStream = null;
            //TODO:Maybe a better name?
            boolean tryingConnection = false;

            connection:
            {
                byte[] connectionResult;
                try {
                    tryingConnection = true;
                    //Better name for empty byte
                    byte[] newbyte = new byte[4096];
                    URLConnection connection = url.openConnection();
                    connectionStream = connection.getInputStream();

                    int byteLength;
                    while ((byteLength = connectionStream.read(newbyte)) > 0) {
                        out.write(newbyte, 0, byteLength);
                    }

                    tryingConnection = false;
                    break connection;
                } catch (IOException e) {
                    e.printStackTrace();
                    connectionResult = connect(url, integer + 1);
                    tryingConnection = false;
                } finally {
                    if (tryingConnection) {
                        if (connectionStream != null) {
                            try {
                                connectionStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                    }
                }

                if (connectionStream != null) {
                    try {
                        connectionStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                return connectionResult;
            }

            if (connectionStream != null) {
                try {
                    connectionStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return out.toByteArray();
        }
    }

    private static File getVanillaDir() {
        String osType;
        if ((osType = System.getProperty("os.name").toLowerCase()).contains("win")) {
            return new File(new File(System.getenv("APPDATA")), ".minecraft");
        } else if (osType.contains("mac")) {
            return new File(new File(System.getProperty("user.home")), "Library/Application Support/minecraft");
        } else if (osType.contains("linux")) {
            return new File(new File(System.getProperty("user.home")), ".minecraft/");
        } else {
            throw new RuntimeException("Failed to determine Minecraft directory for OS: " + osType);
        }
    }
}
