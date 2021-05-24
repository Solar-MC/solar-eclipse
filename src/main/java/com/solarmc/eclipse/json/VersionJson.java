package com.solarmc.eclipse.json;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class VersionJson {
    public static class MinecraftVersionInfo {
        @SerializedName("downloads")
        public ClientDownloads downloads;

        @SerializedName("libraries")
        public List<MinecraftLibrary> libraries;
    }

    public static class MinecraftDownloads {
        @SerializedName("artifact")
        public ArtifactDownload artifact;

        @SerializedName("classifiers")
        public Classifiers classifiers;
    }

    public static class MinecraftLibrary {
        @SerializedName("downloads")
        public MinecraftDownloads downloads;

        @SerializedName("name")
        public String name;
    }

    public static class ArtifactDownload {
        @SerializedName("path")
        public String path;

        @SerializedName("sha1")
        public String sha1;

        @SerializedName("size")
        public String size;

        @SerializedName("url")
        public String url;
    }

    public static class Classifiers {
        @SerializedName("natives-linux")
        public ArtifactDownload linuxNatives;

        @SerializedName("natives-macos")
        public ArtifactDownload macosNatives;

        @SerializedName("natives-osx")
        public ArtifactDownload osxNatives;

        @SerializedName("natives-windows")
        public ArtifactDownload windowsNatives;

        @SerializedName("javadoc")
        public ArtifactDownload javadoc;

        @SerializedName("sources")
        public ArtifactDownload sources;
    }

    public static class ClientDownloads {
        @SerializedName("client")
        public ArtifactDownload client;

        @SerializedName("client_mappings")
        public ArtifactDownload clientMappings;

        @SerializedName("server")
        public ArtifactDownload server;

        @SerializedName("server_mappings")
        public ArtifactDownload serverMappings;
    }
}
