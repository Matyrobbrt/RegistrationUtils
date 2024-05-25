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

import com.matyrobbrt.registrationutils.gradle.holderreg.HolderScanner;
import com.matyrobbrt.registrationutils.gradle.task.GenerateArtifactTask;
import groovy.json.JsonGenerator;
import groovy.json.JsonSlurper;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.TaskTriggersConfig;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RegExtension {

    public static final String NAME = "reg";
    public static final String JAR_NAME = "regutils";
    public static final JsonSlurper PARSER = new JsonSlurper();
    public static final JsonGenerator GENERATOR = new JsonGenerator.Options().build();
    public static final String MIXINS_JSON = "regutils.mixins.json";
    private static final String AFTER_SYNC_TASK = "regutilsIdeSync";

    private final Project project;
    private final RegistrationUtilsExtension.SubProject config;
    private final String group;
    private final TaskProvider<Task> ideSync;

    public RegExtension(Project root, Project project, RegistrationUtilsExtension parent, RegistrationUtilsExtension.SubProject config) {
        this.project = project;
        this.config = config;
        this.group = parent.group.get();
        ideSync = project.getTasks().register(AFTER_SYNC_TASK);

        setupIdea();

        // Register it now
        joinedJarTask(config.type.get());

        project.getPluginManager().apply(JavaPlugin.class);
        if (root.getExtensions().getByType(RegistrationUtilsExtension.class).addsDependencies()) {
            //noinspection Convert2Lambda
            project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, new Action<>() {
                @Override
                public void execute(Jar jar) {
                    configureJarTask(jar);
                }
            });
        }

        if (config.type.get() != RegistrationUtilsExtension.SubProject.Type.COMMON && root.getExtensions().getByType(RegistrationUtilsExtension.class).transformsHolderLoading()) {
            // Can't use a lambda here, see: https://docs.gradle.org/7.4/userguide/validation_problems.html#implementation_unknown
            //noinspection Convert2Lambda
            project.getTasks().named(JavaPlugin.CLASSES_TASK_NAME, t -> t.doLast(new Action<>() {
                @Override
                public void execute(Task task) {
                    handleTransformation(project.getBuildDir().toPath().resolve("classes/java/main"));
                }
            }));
        }
    }

    private void setupIdea() {
        project.getPluginManager().apply("org.jetbrains.gradle.plugin.idea-ext");
        IdeaModel model = project.getExtensions().getByType(IdeaModel.class);
        var project = model.getProject();
        ProjectSettings settings = ((ExtensionAware) project).getExtensions().getByType(ProjectSettings.class);
        TaskTriggersConfig taskTriggers = ((ExtensionAware) settings).getExtensions().getByType(TaskTriggersConfig.class);
        taskTriggers.afterSync(ideSync);
    }

    public void configureJarTask(Object task) {
        configureJarTask(task, false);
    }

    public void configureSourcesJarTask(Object task) {
        configureJarTask(task, true);
    }

    public void configureJarTask(Object task, boolean sources) {
        AbstractCopyTask tsk;
        if (task instanceof String) {
            final String str = (String) task;
            final Task maybeTask = project.getTasks().getByName(str);
            if (maybeTask instanceof AbstractCopyTask) {
                tsk = (AbstractCopyTask) maybeTask;
            } else
                throw new RuntimeException("Cannot configure task of type " + maybeTask + " in order to include Reg");
        } else if (task instanceof AbstractCopyTask) {
            tsk = (AbstractCopyTask) task;
        } else {
            throw new RuntimeException("Cannot find task " + task);
        }
        final RegistrationUtilsExtension.SubProject.Type type = config.type.get();
        if (type == RegistrationUtilsExtension.SubProject.Type.COMMON) {
            tsk.dependsOn(commonJarTask());
            tsk.from(project.zipTree(commonJarTask().get().getOutputJar()), spec -> {
                spec.exclude("**/*MANIFEST.MF");
                spec.exclude("*mod.json");
                if (sources) {
                    spec.exclude("**/*.class");
                } else {
                    spec.exclude("**/*.java");
                }
            });
        } else {
            tsk.dependsOn(joinedJarTask(type));
            tsk.from(project.zipTree(joinedJarTask(type).get().getArchiveFile()), spec -> {
                spec.exclude("**/*MANIFEST.MF");
                spec.exclude("*mod.json");
                tsk.dependsOn(joinedJarTask(type));
                if (sources) {
                    spec.exclude("**/*.class");
                } else {
                    spec.exclude("**/*.java");
                }
            });

            if (type == RegistrationUtilsExtension.SubProject.Type.FABRIC && tsk instanceof AbstractArchiveTask jar) {
                //noinspection Convert2Lambda
                tsk.doLast(new Action<>() {
                    @Override
                    public void execute(Task task) {
                        try (final FileSystem fs = FileSystems.newFileSystem(jar.getArchiveFile().get().getAsFile().toPath(), (ClassLoader) null)) {
                            final Path fmj = fs.getPath("fabric.mod.json");
                            if (Files.exists(fmj)) {
                                final Map map = (Map) PARSER.parse(fmj);
                                final Object mixins = map.computeIfAbsent("mixins", o -> new ArrayList<>());
                                if (mixins instanceof List list) {
                                    list.add(MIXINS_JSON);
                                } else {
                                    map.put("mixins", List.of(MIXINS_JSON));
                                }
                                Files.write(fmj, GENERATOR.toJson(map).getBytes(StandardCharsets.UTF_8));
                            }

                            final Path qmj = fs.getPath("quilt.mod.json");
                            if (Files.exists(qmj)) {
                                final Map map = (Map) PARSER.parse(qmj);
                                final Object mixins = map.computeIfAbsent("mixin", o -> new ArrayList<>());
                                if (mixins instanceof List list) {
                                    list.add(MIXINS_JSON);
                                } else {
                                    map.put("mixin", List.of(MIXINS_JSON));
                                }
                                Files.write(qmj, GENERATOR.toJson(map).getBytes(StandardCharsets.UTF_8));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public Dependency common() {
        return regDependency("common");
    }

    @SuppressWarnings("UnusedReturnValue")
    public Dependency loaderSpecific() {
        final RegistrationUtilsExtension.SubProject.Type type = config.type.get();
        return regDependency(type.toString());
    }

    private Dependency regDependency(String type) {
        final ConfigurableFileCollection files = project.files();
        TaskProvider<GenerateArtifactTask> task = jarTaskFor(type);
        files.builtBy(task);
        files.from(task.map(GenerateArtifactTask::getOutputJar));
        return project.getDependencies().create(files);
    }

    private TaskProvider<GenerateArtifactTask> commonJarTask() {
        return jarTaskFor("common");
    }

    private TaskProvider<GenerateArtifactTask> loaderSpecificJarTask() {
        return jarTaskFor(config.type.get().toString());
    }

    private TaskProvider<GenerateArtifactTask> jarTaskFor(String type) {
        TaskProvider<GenerateArtifactTask> task;
        try {
            task = project.getTasks().named(type +"RegJar", GenerateArtifactTask.class);
        } catch (UnknownDomainObjectException ignored) {
            task = project.getTasks().register(type +"RegJar", GenerateArtifactTask.class, t -> {
                t.getTargetGroup().set(group);
                t.getResource().set(type);
            });
            TaskProvider<GenerateArtifactTask> finalTask = task;
            ideSync.configure(t -> t.dependsOn(finalTask));
        }
        return task;
    }

    @SuppressWarnings("unused")
    public Dependency joined() {
        final RegistrationUtilsExtension.SubProject.Type type = config.type.get();
        if (type == RegistrationUtilsExtension.SubProject.Type.COMMON)
            return common();

        TaskProvider<Jar> joinedJar = joinedJarTask(type);
        final ConfigurableFileCollection files = project.files();
        files.builtBy(joinedJar);
        files.from(joinedJar.map(Jar::getArchiveFile));
        return project.getDependencies().create(files);
    }

    private @NotNull TaskProvider<Jar> joinedJarTask(RegistrationUtilsExtension.SubProject.Type type) {
        TaskProvider<Jar> joinedJar;
        try {
            joinedJar = project.getTasks().named("joinedRegJar", Jar.class);
        } catch (UnknownDomainObjectException ignored) {
            common();
            loaderSpecific();
            joinedJar = project.getTasks().register("joinedRegJar", Jar.class, t -> {
                t.dependsOn(common(), loaderSpecific());
                t.getArchiveBaseName().set(JAR_NAME + "-joined-" + type);
                t.getArchiveVersion().set(RegistrationUtilsPlugin.VERSION);
                t.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("registrationutils"));
                t.from(project.zipTree(commonJarTask().get().getOutputJar()));
                t.from(project.zipTree(loaderSpecificJarTask().get().getOutputJar()));
                t.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
                t.dependsOn(commonJarTask(), loaderSpecificJarTask());
            });
            TaskProvider<Jar> finalJoinedJar = joinedJar;
            ideSync.configure(t -> t.dependsOn(finalJoinedJar));
        }
        return joinedJar;
    }

    private void handleTransformation(Path classesOut) {
        final String mainClassName = config.mainClass.get();
        final String mainClassP = mainClassName.replace('.', '/') + ".class";
        final Path mainClassOut = classesOut.resolve(mainClassP).toAbsolutePath();
        final HolderScanner scanner = new HolderScanner(project.getLogger(), group);
        try {
            Files.walkFileTree(classesOut, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    file = file.toAbsolutePath();
                    if (mainClassOut.equals(file)) {
                        return FileVisitResult.CONTINUE;
                    } else if (file.toString().endsWith(".class")) {
                        scanner.processClass(file);
                    }
                    return super.visitFile(file, attrs);
                }
            });

            // Let's add stuff to meta-inf
            final Path servicesFile = classesOut.resolve("META-INF/services/" + (group.length() < 1 ? "" : group + ".") + "RegistryHolder");
            Files.deleteIfExists(servicesFile);
            Files.createDirectories(servicesFile.getParent());
            Files.write(servicesFile.toAbsolutePath(), scanner.getFoundClasses());

            // Now, we need to transform the main class
            if (!Files.exists(mainClassOut)) {
                throw new RuntimeException("Could not find main class " + mainClassName);
            }
            final ClassReader cr = new ClassReader(Files.readAllBytes(mainClassOut));
            final ClassNode clazz = new ClassNode(Opcodes.ASM9);
            cr.accept(clazz, 0);
            if (config.type.get().mainClassHolderTransformer.transform(clazz, config.modInitMethod.get(), group)) {
                final ClassWriter cw = new ClassWriter(Opcodes.ASM9);
                clazz.accept(cw);
                project.getLogger().trace("Transforming main mod class {}: adding registry class static init in mod initialization", clazz.name);
                Files.write(mainClassOut, cw.toByteArray());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    }
}
