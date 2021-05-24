package com.solarmc.eclipse.util;

import com.solarmc.eclipse.EclipseGradleExtension;
import org.gradle.api.Project;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class GradleUtils {

    public static Path getCacheFile(Project project, EclipseGradleExtension extension, String fileName) {
        return Paths.get(getCacheFolder(project, extension.version.vanillaVersion) + File.separator + fileName);
    }

    public static Path getCacheFolder(Project project, String version) {
        return Paths.get(getCacheFolder(project) + File.separator + version);
    }

    public static String getCacheFolder(Project project) {
        return project.getGradle().getGradleUserHomeDir() + "/caches/solarmc-eclipse";
    }

    public static EclipseGradleExtension getExtension(Project project) {
        return project.getExtensions().getByType(EclipseGradleExtension.class);
    }

    public static Path generateTmpFile(Project project) {
        return Paths.get(project.getGradle().getGradleUserHomeDir() + "/tmp/" + UUID.randomUUID());
    }
}
