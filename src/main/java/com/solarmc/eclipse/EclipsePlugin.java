package com.solarmc.eclipse;

import com.solarmc.eclipse.tasks.GenerateJarsTask;
import com.solarmc.eclipse.tasks.LaunchEnigmaTask;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;

@NonNullApi
public class EclipsePlugin implements Plugin<Project> {

    public static final String TASK_GROUP = "solar eclipse";

    @Override
    public void apply(Project target) {
        TaskContainer tasks = target.getTasks();
        tasks.register("generateJars", GenerateJarsTask.class);
        tasks.register("launchEnigma", LaunchEnigmaTask.class, task -> task.dependsOn("generateJars"));

        target.getExtensions().create("solarEclipse", EclipseGradleExtension.class, target);
    }
}
