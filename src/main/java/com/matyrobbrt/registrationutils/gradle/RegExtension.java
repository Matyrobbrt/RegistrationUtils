/*
 * This file and all files in subdirectories of the file's parent are provided by the
 * RegistrationUtils Gradle plugin, and are licensed under the MIT license.
 * More info at https://github.com/Matyrobbrt/RegistrationUtils.
 *
 * MIT License
 *
 * Copyright (c) 2022 Matyrobbrt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.matyrobbrt.registrationutils.gradle;

import com.matyrobbrt.registrationutils.gradle.task.RelocateResourceTask;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder;
import net.minecraftforge.artifactural.base.repository.SimpleRepository;
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.ModuleLibrary;
import org.gradle.testfixtures.ProjectBuilder;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class RegExtension {


    public static final String NAME = "reg";
    public static final String JAR_NAME = "regutils";
    public static final String FORCE_GENERATION_PROPERTY = "regForceGeneration";

    private final Project project;
    private final RegistrationUtilsExtension.SubProject config;
    private final Path cachePath;
    private final String group;

    private final Path commonOut;
    private final Path commonIn;
    private final JarTask commonJar;
    private final JarTask commonSourcesJar;

    private final Path typeOut;
    private final Path typeIn;
    private final JarTask typeJar;
    private final JarTask typeSourcesJar;

    public RegExtension(Project root, Project project, RegistrationUtilsExtension parent, RegistrationUtilsExtension.SubProject config) {
        this.project = project;
        this.config = config;
        this.cachePath = root.getBuildDir().toPath().resolve(RegistrationUtilsPlugin.CACHE_FOLDER).toAbsolutePath();
        this.group = parent.group.get();

        int random = ThreadLocalRandom.current().nextInt();
        final var cache = cachePath.resolve("cache");
        final var provider = new RegArtifactProvider(group, cachePath);
        GradleRepositoryAdapter.add(project.getRepositories(), "reg_" + random, cache.toFile(),
            SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(provider))
        );

        final var type = config.type.get().toString();
        commonIn = cachePath.resolve("compile").resolve("in").resolve(RegistrationUtilsPlugin.VERSION).resolve("common").toAbsolutePath();
        commonOut = cachePath.resolve("compile").resolve("out").resolve(RegistrationUtilsPlugin.VERSION).resolve("common").toAbsolutePath();

        typeIn = cachePath.resolve("compile").resolve("in").resolve(RegistrationUtilsPlugin.VERSION).resolve(type);
        typeOut = cachePath.resolve("compile").resolve("out").resolve(RegistrationUtilsPlugin.VERSION).resolve(type);

        project.getPluginManager().apply(JavaPlugin.class);
        project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, task -> {
            task.doFirst(t -> common() /* Makes sure the utils jar exists */);
            task.from(commonOut).exclude("META-INF/MANIFEST.MF");
            if (config.type.get() != RegistrationUtilsExtension.SubProject.Type.COMMON) {
                task.doFirst(t -> loaderSpecific());
                task.from(typeOut).exclude("META-INF/MANIFEST.MF");
            }
        });

        final var internal = ProjectBuilder.builder()
                .withName("reg_" + project.getName())
                .withProjectDir(cachePath.resolve("projects").resolve(project.getName()).toFile())
                .build();

        this.commonJar = internal.getTasks().create("regCommonJar", JarTask.class, commonJar -> {
            commonJar.from(commonOut);
            commonJar.manifest(Manifest::getAttributes);
            commonJar.getArchiveBaseName().set(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION);
            commonJar.getDestinationDirectory().set(cachePath.toFile());
        });
        this.commonSourcesJar = internal.getTasks().create("regCommonSourcesJar", JarTask.class, task -> {
            task.from(commonIn);
            task.getDestinationDirectory().set(cachePath.toFile());
            task.getArchiveBaseName().set(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION);
            task.getArchiveClassifier().set("sources");
        });

        this.typeJar = internal.getTasks().create("regTypeJar", JarTask.class, task -> {
            task.from(typeOut);
            task.manifest(Manifest::getAttributes);
            task.getArchiveBaseName().set(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION);
            task.getArchiveClassifier().set(type);
            task.getDestinationDirectory().set(cachePath.toFile());
        });
        this.typeSourcesJar = internal.getTasks().create("regTypeSourcesJar", JarTask.class, task -> {
            task.from(typeIn);
            task.getDestinationDirectory().set(cachePath.toFile());
            task.getArchiveBaseName().set(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION);
            task.getArchiveClassifier().set(type + "-sources");
        });
    }


    @SuppressWarnings("ALL")
    public Dependency common() {
        final var dep = project.getDependencies().create(dependencyNotation(null));
        maybeMakeCommonJar();
        return dep;
    }

    // TODO: rename to type()
    @SuppressWarnings("ALL")
    public Dependency loaderSpecific() {
        final var type = config.type.get();
        if (type == RegistrationUtilsExtension.SubProject.Type.COMMON)
            return common();
        final var dep = project.getDependencies().create(dependencyNotation(type));

        // We need the common classpath for this, so just so we can make sure it exists
        final var commonPath = maybeMakeCommonJar().toAbsolutePath().toString();
        maybeCreateJar(type, typeIn, typeOut, type + ".zip", typeJar, "com.matyrobbrt.registrationutils", typeSourcesJar, commonPath);
        return dep;
    }

    private Path maybeMakeCommonJar() {
        return maybeCreateJar(null, commonIn, commonOut, "common.zip", commonJar, "com.matyrobbrt.registrationutils", commonSourcesJar);
    }

    /**
     * @return the path of the created jar
     */
    @ParametersAreNonnullByDefault
    private Path maybeCreateJar(
            @Nullable RegistrationUtilsExtension.SubProject.Type type,
            Path inPath,
            Path outPath,
            String resource,
            JarTask jarTask,
            String inGroup,
            @Nullable JarTask sourcesJarTask,
            String... classpath
    ) {
        final var jarPath = getJarPath(type, null);
        if (Files.exists(jarPath) && !project.hasProperty(FORCE_GENERATION_PROPERTY)) {
            attachSources(type);
            return jarPath;
        }

        // Let's create the deps
        {
            // Start with relocating the jar
            try {
                deleteDir(inPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            final var res = RelocateResourceTask.relocate(
                    project.getLogger(),
                    RelocateResourceTask.getResourceDir(resource),
                    inPath,
                    inGroup,
                    group
            );
            if (!res) {
                throw new RuntimeException("Relocation failed!");
            }
        }

        {
            // Next we compile it
            final List<Path> allFiles = new ArrayList<>();
            final List<File> directCopy = new ArrayList<>();
            try {
                deleteDir(outPath);
                Files.createDirectories(outPath);
                Files.walkFileTree(inPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        final var name = file.toString();
                        if (name.endsWith(".java")) {
                            allFiles.add(file.toAbsolutePath());
                        } else {
                            directCopy.add(file.toFile());
                        }
                        return super.visitFile(file, attrs);
                    }
                });
                final var cp = new ArrayList<String>();
                project.getConfigurations().getByName("minecraft").forEach(f -> cp.add(f.toString()));
                cp.addAll(List.of(classpath));
                final var compiler = ToolProvider.getSystemJavaCompiler();
                final var fileManager = compiler.getStandardFileManager(null, Locale.ROOT, null);
                final var fileObjs = fileManager.getJavaFileObjects(allFiles.toArray(Path[]::new));
                compiler.getTask(null, fileManager, null, List.of(
                        "-d", outPath.toString(), "-cp", String.join(";", cp.stream().toList())
                ), null, fileObjs).call();

                for (final var dC : directCopy) {
                    final var dir = outPath.resolve(inPath.relativize(dC.toPath().getParent()));
                    Files.createDirectories(dir);
                    Files.copy(dC.toPath(), dir.resolve(dC.getName()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Exception occurred copying regutils files for compilation!", e, false, true) {};
            }
        }

        {
            // Now let's package it back
            // Compiled
            jarTask.makeJar((e) -> {
                throw new RuntimeException(e);
            });

            // Sources
            if (sourcesJarTask != null) {
                sourcesJarTask.copy();
            }

            attachSources(type);
            return jarPath;
        }
    }

    private void attachSources(RegistrationUtilsExtension.SubProject.Type type) {
        final var jarName = cachePath.relativize(getJarPath(type, null)).toString();
        final var sourcesJar = getJarPath(type, "sources").toAbsolutePath();

        if (project.getPlugins().hasPlugin("eclipse")) {
            final var eclipse = project.getExtensions().getByType(EclipseModel.class);
            eclipse.classpath(cp -> cp.file(file -> file.whenMerged((Classpath classpath) -> classpath.getEntries().stream()
                .filter(Library.class::isInstance)
                .map(Library.class::cast)
                .filter(lib -> lib.getPath().contains(jarName))
                .filter(lib -> lib.getSourcePath() == null)
                .findFirst()
                .ifPresent(lib -> lib.setSourcePath(classpath.fileReference(sourcesJar))))));
        }

        if (project.getPlugins().hasPlugin("idea")) {
            final var idea = project.getPlugins().getPlugin(IdeaPlugin.class);
            idea.getModel().module(mod -> mod.iml(iml -> iml.whenMerged($ -> idea.getModel().getModule().resolveDependencies()
                .stream()
                .filter(ModuleLibrary.class::isInstance)
                .map(ModuleLibrary.class::cast)
                .filter(lib -> lib.getClasses().stream().anyMatch(p -> p.getUrl().contains(jarName)))
                .filter(lib -> lib.getSources().isEmpty())
                .findFirst()
                .ifPresent(lib -> lib.getSources().add(new org.gradle.plugins.ide.idea.model.Path("jar://" + sourcesJar + "!/"))))));
        }
    }

    private String dependencyNotation(RegistrationUtilsExtension.SubProject.Type type) {
        if (type == null) {
            return group + ":" + JAR_NAME + ":" + RegistrationUtilsPlugin.VERSION;
        } else {
            return group + ":" + JAR_NAME + ":" + RegistrationUtilsPlugin.VERSION + ":" + type.name().toLowerCase(Locale.ROOT);
        }
    }

    public Path getJarPath(RegistrationUtilsExtension.SubProject.Type type, String classifier) {
        if (type == null || type == RegistrationUtilsExtension.SubProject.Type.COMMON) {
            return cachePath.resolve(appendClassifier(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION, classifier) + ".jar");
        } else {
            return cachePath.resolve(appendClassifier(appendClassifier(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION, type.name().toLowerCase(Locale.ROOT)), classifier) + ".jar");
        }
    }

    private static String appendClassifier(String str, String classifier) {
        return classifier == null || classifier.isBlank() ? str : str + "-" + classifier;
    }

    @SuppressWarnings("ALL")
    private static void deleteDir(Path path) throws IOException {
        if (!Files.exists(path))
            return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    public static class JarTask extends Jar {
        @Override
        public void copy() {
            super.copy();
        }

        public void makeJar(Consumer<Exception> exception) {
            try {
                copy();
            } catch (Exception i) {
                exception.accept(i);
            }
        }
    }
}
