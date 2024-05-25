package com.matyrobbrt.registrationutils.gradle.task;

import com.matyrobbrt.registrationutils.gradle.RegistrationUtilsExtension;
import com.matyrobbrt.registrationutils.gradle.RegistrationUtilsPlugin;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class GenerateArtifactTask extends DefaultTask {

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Input
    public abstract Property<String> getInitialGroup();

    @Input
    public abstract Property<String> getTargetGroup();

    @Input
    public abstract Property<String> getResource();

    @Inject
    public GenerateArtifactTask() {
        getInitialGroup().convention("com.matyrobbrt.registrationutils");
        getOutputJar().convention(getProject().getLayout().getBuildDirectory().file(getResource().map(r -> "registrationutils/" + r + "-" + RegistrationUtilsPlugin.VERSION + ".jar")));
    }

    @TaskAction
    public void run() {
        Path sourcesInPath = getTemporaryDir().toPath().resolve("sources");
        if (Files.exists(sourcesInPath)) {
            getFileSystemOperations().delete(spec -> spec.delete(sourcesInPath));
        }
        final boolean res = RelocateResourceTask.relocate(
                getLogger(),
                RelocateResourceTask.getResourceDir(getResource().get() + "-sources.zip"),
                sourcesInPath,
                getInitialGroup().get(),
                getTargetGroup().get()
        );
        if (!res) {
            throw new RuntimeException("Relocation failed!");
        }

        {
            try {
                final Path finishedTemp = getTemporaryDir().toPath().resolve("relocated.jar");
                Files.createDirectories(finishedTemp.getParent());
                Files.deleteIfExists(finishedTemp);
                final Path jar = getTemporaryDir().toPath().resolve("original.jar");
                Files.createDirectories(jar.getParent());
                Files.deleteIfExists(jar);
                Files.copy(Objects.requireNonNull(RegistrationUtilsExtension.class.getResourceAsStream("/" + getResource().get() + ".zip")), jar, StandardCopyOption.REPLACE_EXISTING);
                final JarRelocator relocator = new JarRelocator(jar.toFile(), finishedTemp.toFile(), Collections.singleton(new Relocation(getInitialGroup().get(), getTargetGroup().get())));
                relocator.run();

                final Pattern groupPattern = Pattern.compile(getInitialGroup().get().replace(".", "\\."));
                final Pattern groupPatternND = Pattern.compile(getTargetGroup().get().replace('.', '/'));

                try (JarOutputStream out = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(getOutputJar().get().getAsFile())))) {
                    // Now we gotta rename META-INFs or (mixin) jsons
                    Set<String> included = new HashSet<>();
                    try (JarFile in = new JarFile(finishedTemp.toFile())) {
                        for (Enumeration<JarEntry> entries = in.entries(); entries.hasMoreElements(); ) {
                            final JarEntry entry = entries.nextElement();

                            final String name = entry.getName();
                            final InputStream is = in.getInputStream(entry);
                            included.add(name);

                            final boolean isService = name.startsWith("META-INF/services/");
                            final boolean isJson = name.endsWith(".json");
                            if (!(isService || isJson) || entry.isDirectory()) {
                                out.putNextEntry(entry);
                                IOUtils.copy(is, out);
                                out.closeEntry();
                                continue;
                            }

                            if (isService) {
                                String serviceName = name.substring(18);
                                final Matcher nameMatcher = groupPattern.matcher(serviceName);
                                if (!nameMatcher.find()) {
                                    out.putNextEntry(entry);
                                    IOUtils.copy(is, out);
                                    out.closeEntry();
                                    continue;
                                }
                                serviceName = nameMatcher.replaceAll(getTargetGroup().get());
                                String content = RelocateResourceTask.readBytes(is).toString();
                                content = groupPattern.matcher(content).replaceAll(getTargetGroup().get());

                                final JarEntry newEntry = new JarEntry("META-INF/services/" + serviceName);
                                out.putNextEntry(newEntry);
                                out.write(content.getBytes(StandardCharsets.UTF_8));
                                out.closeEntry();
                            } else {
                                String content = RelocateResourceTask.readBytes(is).toString();
                                content = groupPattern.matcher(content).replaceAll(getTargetGroup().get());
                                content = groupPatternND.matcher(content).replaceAll(getTargetGroup().get().replace('.', '/'));

                                final JarEntry newEntry = new JarEntry(name);
                                out.putNextEntry(newEntry);
                                out.write(content.getBytes(StandardCharsets.UTF_8));
                                out.closeEntry();
                            }
                        }
                    }
                    // And we include any sources we've not included
                    try (var files = Files.walk(sourcesInPath)) {
                        files.filter(Files::isRegularFile).forEach(p -> {
                            final String name = sourcesInPath.relativize(p).toString().replace('\\', '/');
                            if (included.contains(name)) {
                                return;
                            }
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
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
