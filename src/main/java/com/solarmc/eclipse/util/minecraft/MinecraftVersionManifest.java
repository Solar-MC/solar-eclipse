package com.solarmc.eclipse.util.minecraft;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class MinecraftVersionManifest {
    @SerializedName("versions")
    public List<Version> minecraftVersions;

    public static class Version {
        @SerializedName("id")
        public String version;

        @SerializedName("url")
        public String url;
    }
}
