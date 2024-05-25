package com.matyrobbrt.registrationutils.gradle.task;

import com.matyrobbrt.registrationutils.gradle.RegistrationUtilsPlugin;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public abstract class GenerateSourcesTask extends GenerateTask {
    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    public GenerateSourcesTask() {
        getInitialGroup().convention("com.matyrobbrt.registrationutils");
        getOutputJar().convention(getProject().getLayout().getBuildDirectory().file(getResource().map(r -> "registrationutils/" + r + "-sources-" + RegistrationUtilsPlugin.VERSION + ".jar")));
    }

    @TaskAction
    public void run() {
        Path sourcesInPath = getTemporaryDir().toPath().resolve("sources");
        if (Files.exists(sourcesInPath)) {
            getFileSystemOperations().delete(spec -> spec.delete(sourcesInPath));
        }
        final boolean res = GenerateArtifactTask.relocate(
                getLogger(),
                GenerateArtifactTask.getResourceDir(getResource().get() + "-sources.zip"),
                sourcesInPath,
                getInitialGroup().get(),
                getTargetGroup().get()
        );
        if (!res) {
            throw new RuntimeException("Relocation failed!");
        }

        try (JarOutputStream out = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(getOutputJar().get().getAsFile())))) {
            try (var files = Files.walk(sourcesInPath)) {
                files.filter(Files::isRegularFile).forEach(p -> {
                    final String name = sourcesInPath.relativize(p).toString().replace('\\', '/');
                    try (InputStream is = Files.newInputStream(p)) {
                        final JarEntry entry = new JarEntry(name);
                        out.putNextEntry(entry);
                        IOUtils.copy(is, out);
                        out.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
