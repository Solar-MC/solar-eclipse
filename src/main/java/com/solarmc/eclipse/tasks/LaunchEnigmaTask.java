package com.solarmc.eclipse.tasks;

import com.solarmc.eclipse.EclipseGradleExtension;
import com.solarmc.eclipse.EclipsePlugin;
import com.solarmc.eclipse.util.GradleUtils;
import cuchaz.enigma.gui.Main;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class LaunchEnigmaTask extends DefaultTask {

    public LaunchEnigmaTask() {
        setGroup(EclipsePlugin.TASK_GROUP);
    }


    @TaskAction
    public void run() throws IOException {
        EclipseGradleExtension extension = GradleUtils.getExtension(getProject());
        Path lunarIntermediary = GradleUtils.getCacheFile(getProject(), extension, "out/lunar-prod-intermediary.jar");
        File slapMappingsDir = new File(getProject().getProjectDir(), "mappings");

        Main.main(new String[]{
                "-jar", lunarIntermediary.toString(),
                "-mappings", slapMappingsDir.toString()
        });
    }
}
