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
import com.matyrobbrt.registrationutils.gradle.task.RelocateResourceTask;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder;
import net.minecraftforge.artifactural.base.repository.SimpleRepository;
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.ModuleLibrary;
import org.gradle.testfixtures.ProjectBuilder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

public class RegExtension {

    public static final String NAME = "reg";
    public static final String JAR_NAME = "regutils";
    public static final String FORCE_GENERATION_PROPERTY = "regForceGeneration";

    private final Project project;
    private final RegistrationUtilsExtension.SubProject config;
    private final Path cachePath;
    private final String group;

    private final Path commonSourcesIn;
    private final JarTask commonSourcesJar;

    private final Path typeSourcesIn;
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
        commonSourcesIn = cachePath.resolve("sources").resolve(RegistrationUtilsPlugin.VERSION).resolve("common").toAbsolutePath();
        typeSourcesIn = cachePath.resolve("sources").resolve(RegistrationUtilsPlugin.VERSION).resolve(type).toAbsolutePath();

        project.getPluginManager().apply(JavaPlugin.class);
        if (root.getExtensions().getByType(RegistrationUtilsExtension.class).addsDependencies()) {
            project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, new Action<Jar>() {
                @Override
                public void execute(Jar jar) {
                    configureJarTask(jar);
                }
            });
        }

        if (config.type.get() != RegistrationUtilsExtension.SubProject.Type.COMMON && root.getExtensions().getByType(RegistrationUtilsExtension.class).transformsHolderLoading()) {
            // Can't use a lambda here, see: https://docs.gradle.org/7.4/userguide/validation_problems.html#implementation_unknown
            project.getTasks().named(JavaPlugin.CLASSES_TASK_NAME, t -> t.doLast(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    handleTransformation(project.getBuildDir().toPath().resolve("classes/java/main"));
                }
            }));
        }
        final var internal = (ProjectInternal) ProjectBuilder.builder()
                .withName("reg_" + project.getName())
                .withProjectDir(cachePath.resolve("projects").resolve(project.getName()).toFile())
                .build();

        internal.evaluate();

        this.commonSourcesJar = internal.getTasks().create("regCommonSourcesJar", JarTask.class, task -> {
            task.from(commonSourcesIn);
            task.getDestinationDirectory().set(cachePath.toFile());
            task.getArchiveBaseName().set(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION);
            task.getArchiveClassifier().set("sources");
        });

        this.typeSourcesJar = internal.getTasks().create("regTypeSourcesJar", JarTask.class, task -> {
            task.from(typeSourcesIn);
            task.getDestinationDirectory().set(cachePath.toFile());
            task.getArchiveBaseName().set(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION + "-" + type);
            task.getArchiveClassifier().set("sources");
        });
    }

    public void configureJarTask(AbstractCopyTask task) {
        task.from(project.zipTree(getJarPath(RegistrationUtilsExtension.SubProject.Type.COMMON, null))).exclude("META-INF/MANIFEST.MF");
        if (config.type.get() != RegistrationUtilsExtension.SubProject.Type.COMMON) {
            task.doFirst(t -> loaderSpecific());
            task.from(project.zipTree(getJarPath(config.type.get(), null))).exclude("META-INF/MANIFEST.MF");
        }
    }

    @SuppressWarnings("ALL")
    public Dependency common() {
        final var dep = project.getDependencies().create(dependencyNotation(null));
        maybeCreateJar(null, commonSourcesIn, "common", "com.matyrobbrt.registrationutils", commonSourcesJar);
        return dep;
    }

    @SuppressWarnings("ALL")
    public Dependency loaderSpecific() {
        final var type = config.type.get();
        if (type == RegistrationUtilsExtension.SubProject.Type.COMMON)
            return common();
        final var dep = project.getDependencies().create(dependencyNotation(type));
        maybeCreateJar(type, typeSourcesIn, type.toString(), "com.matyrobbrt.registrationutils", typeSourcesJar);
        return dep;
    }

    /**
     * @return the path of the created jar
     */
    @ParametersAreNonnullByDefault
    private Path maybeCreateJar(
            @Nullable RegistrationUtilsExtension.SubProject.Type type,
            Path sourcesInPath,
            String resource,
            String inGroup,
            @Nullable JarTask sourcesJarTask
    ) {
        final var jarPath = getJarPath(type, null);
        if (Files.exists(jarPath) && (!project.hasProperty(FORCE_GENERATION_PROPERTY) || project.getGradle().getStartParameter().isRefreshDependencies())) {
            attachSources(type);
            return jarPath;
        }

        // Let's create the deps
        {
            // Start with relocating the jar
            try {
                deleteDir(sourcesInPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            final var res = RelocateResourceTask.relocate(
                    project.getLogger(),
                    RelocateResourceTask.getResourceDir(resource + "-sources.zip"),
                    sourcesInPath,
                    inGroup,
                    group
            );
            if (!res) {
                throw new RuntimeException("Relocation failed!");
            }
        }

        {
            try {
                final String jarName = (type == null ? "common" : type.toString()) + "-" + RegistrationUtilsPlugin.VERSION + ".jar";
                final var finishedTemp = cachePath.resolve("comp").resolve(jarName);
                Files.createDirectories(finishedTemp.getParent());
                Files.deleteIfExists(finishedTemp);
                final var jar = cachePath.resolve("befcompiled").resolve(jarName);
                Files.createDirectories(jar.getParent());
                Files.deleteIfExists(jar);
                Files.copy(Objects.requireNonNull(RegistrationUtilsExtension.class.getResourceAsStream("/" + resource + ".zip")), jar, StandardCopyOption.REPLACE_EXISTING);
                final var relocator = new JarRelocator(jar.toFile(), finishedTemp.toFile(), Collections.singleton(new Relocation(inGroup, group)));
                relocator.run();

                final var groupPattern = Pattern.compile(inGroup.replace(".", "\\."));

                // Now we gotta rename META-INFs
                try (JarOutputStream out = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarPath.toFile())))) {
                    try (JarFile in = new JarFile(finishedTemp.toFile())) {
                        for (Enumeration<JarEntry> entries = in.entries(); entries.hasMoreElements(); ) {
                            final var entry = entries.nextElement();

                            final var name = entry.getName();
                            final var is = in.getInputStream(entry);
                            if (!name.startsWith("META-INF/services/") || entry.isDirectory()) {
                                out.putNextEntry(entry);
                                is.transferTo(out);
                                out.closeEntry();
                                continue;
                            }

                            var serviceName = name.substring(18);
                            final var nameMatcher = groupPattern.matcher(serviceName);
                            if (!nameMatcher.find()) {
                                out.putNextEntry(entry);
                                is.transferTo(out);
                                out.closeEntry();
                                continue;
                            }
                            serviceName = nameMatcher.replaceAll(group);
                            var content = RelocateResourceTask.readBytes(is).toString();
                            content = groupPattern.matcher(content).replaceAll(group);

                            final var newEntry = new JarEntry("META-INF/services/" + serviceName);
                            out.putNextEntry(newEntry);
                            out.write(content.getBytes(StandardCharsets.UTF_8));
                            out.closeEntry();
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        {
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

    private void handleTransformation(Path classesOut) {
        final var mainClassName = config.mainClass.get();
        final var mainClassP = mainClassName.replace('.', '/') + ".class";
        final var mainClassOut = classesOut.resolve(mainClassP).toAbsolutePath();
        final var scanner = new HolderScanner(project.getLogger(), group);
        try {
            Files.walkFileTree(classesOut, new SimpleFileVisitor<>() {
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
            final var servicesFile = classesOut.resolve("META-INF/services/" + (group.length() < 1 ? "" : group + ".") + "RegistryHolder");
            Files.deleteIfExists(servicesFile);
            Files.createDirectories(servicesFile.getParent());
            Files.write(servicesFile.toAbsolutePath(), scanner.getFoundClasses());

            // Now, we need to transform the main class
            if (!Files.exists(mainClassOut)) {
                throw new RuntimeException("Could not find main class " + mainClassName);
            }
            final var cr = new ClassReader(Files.readAllBytes(mainClassOut));
            final ClassNode clazz = new ClassNode(Opcodes.ASM9);
            cr.accept(clazz, 0);
            if (config.type.get().mainClassHolderTransformer.transform(clazz, config.modInitMethod.get(), group)) {
                final var cw = new ClassWriter(Opcodes.ASM9);
                clazz.accept(cw);
                project.getLogger().trace("Transforming main mod class {}: adding registry class static init in mod initialization", clazz.name);
                Files.write(mainClassOut, cw.toByteArray());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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
