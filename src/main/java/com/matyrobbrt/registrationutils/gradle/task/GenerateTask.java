package com.matyrobbrt.registrationutils.gradle.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;

import javax.inject.Inject;

public abstract class GenerateTask extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @Input
    public abstract Property<String> getInitialGroup();

    @Input
    public abstract Property<String> getTargetGroup();

    @Input
    public abstract Property<String> getResource();
}
