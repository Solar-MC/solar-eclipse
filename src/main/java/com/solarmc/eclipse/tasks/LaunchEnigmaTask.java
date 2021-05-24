package com.solarmc.eclipse.tasks;

import com.solarmc.eclipse.EclipsePlugin;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public class LaunchEnigmaTask extends DefaultTask {

    public LaunchEnigmaTask() {
        setGroup(EclipsePlugin.TASK_GROUP);
    }


    @TaskAction
    public void run() throws IOException {

    }
}
