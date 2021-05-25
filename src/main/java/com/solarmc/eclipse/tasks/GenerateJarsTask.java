package com.solarmc.eclipse.tasks;

import com.solarmc.eclipse.EclipseGradleExtension;
import com.solarmc.eclipse.EclipsePlugin;
import com.solarmc.eclipse.provider.LunarProvider;
import com.solarmc.eclipse.provider.MinecraftProvider;
import com.solarmc.eclipse.util.GradleUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates Jars and utilities to allow for mapping and running of Lunar Client
 */
public class GenerateJarsTask extends DefaultTask {

    public MinecraftProvider minecraftProvider;
    public LunarProvider lunarProvider;

    public GenerateJarsTask() {
        setGroup(EclipsePlugin.TASK_GROUP);
    }

    @TaskAction
    public void run() throws IOException {
        EclipseGradleExtension extensionInfo = GradleUtils.getExtension(getProject());
        Path vanillaMc = GradleUtils.getCacheFile(getProject(), extensionInfo, "downloads/vanilla.jar");

        this.minecraftProvider = new MinecraftProvider(extensionInfo.version.vanillaVersion, getProject());
        this.minecraftProvider.retrieveFiles(vanillaMc);

        this.lunarProvider = new LunarProvider();
        this.lunarProvider.retrieveFiles(getProject(), extensionInfo, minecraftProvider);
        this.lunarProvider.retrieveIntermediaryDependantFiles(getProject(), extensionInfo, minecraftProvider);
    }
}
