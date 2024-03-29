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

import com.google.common.collect.Lists;
import com.matyrobbrt.registrationutils.gradle.holderreg.HolderScanner;
import com.matyrobbrt.registrationutils.gradle.task.RelocateResourceTask;
import groovy.json.JsonGenerator;
import groovy.json.JsonSlurper;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder;
import net.minecraftforge.artifactural.base.repository.SimpleRepository;
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
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
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class RegExtension {

    public static final String NAME = "reg";
    public static final String JAR_NAME = "regutils";
    public static final String FORCE_GENERATION_PROPERTY = "regForceGeneration";
    public static final JsonSlurper PARSER = new JsonSlurper();
    public static final JsonGenerator GENERATOR = new JsonGenerator.Options().build();

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
        this.group = parent.group.get();
        this.cachePath = root.getBuildDir().toPath().resolve(RegistrationUtilsPlugin.CACHE_FOLDER)
                .resolve(Utils.getStringFromSHA256(group)).toAbsolutePath();

        int random = ThreadLocalRandom.current().nextInt();
        final Path cache = cachePath.resolve("cache");
        final RegArtifactProvider provider = new RegArtifactProvider(group, cachePath);
        GradleRepositoryAdapter.add(project.getRepositories(), "reg_" + random, cache.toFile(),
                SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(provider))
        );

        final String type = config.type.get().toString();
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
        final ProjectInternal internal = (ProjectInternal) ProjectBuilder.builder()
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
            task.getArchiveBaseName().set(JAR_NAME + "-" + type + "-" + RegistrationUtilsPlugin.VERSION);
            task.getArchiveClassifier().set("sources");
        });
    }

    public void configureJarTask(Object task) {
        configureJarTask(task, null);
    }

    public void configureSourcesJarTask(Object task) {
        configureJarTask(task, "sources");
    }

    public void configureJarTask(Object task, @Nullable String classifier) {
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
        try {
            final Path extDir = cachePath.resolve("ext_" + tsk.getName()).resolve(RegistrationUtilsPlugin.VERSION).resolve(config.type.get().toString());
            deleteDir(extDir);
            Files.createDirectories(extDir.getParent());
            // TODO find a better solution here
            tsk.doFirst(new Action<Task>() {
                @Override
                public void execute(Task t) {
                    common();
                    loaderSpecific();
                    try {
                        final Predicate<String> pred = f -> {
                            f = f.trim();
                            return !f.endsWith("MANIFEST.MF") && !f.contains("mod.json");
                        };
                        extractSubDir(getJarPath(RegistrationUtilsExtension.SubProject.Type.COMMON, classifier), extDir, pred);
                        if (config.type.get() != RegistrationUtilsExtension.SubProject.Type.COMMON) {
                            extractSubDir(getJarPath(config.type.get(), classifier), extDir, pred);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Exception trying to add reg to jar: ", e);
                    }
                }
            });

            if (config.type.get() == RegistrationUtilsExtension.SubProject.Type.FABRIC && tsk instanceof AbstractArchiveTask) {
                final AbstractArchiveTask jar = (AbstractArchiveTask) tsk;
                final String configName = "regutils-" + Utils.getAlphaNumericString(7) + "-" + group.replace('.', '-');
                final String mixinsConfig = configName + ".mixins.json";
                tsk.from(extDir, spec -> spec.rename("regutils.mixins.json", mixinsConfig)
                        .rename("regutils.refmap.json", configName + ".refmap.json"));

                tsk.doLast(new Action<Task>() {
                    @Override
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    public void execute(Task task) {
                        try (final FileSystem fs = FileSystems.newFileSystem(jar.getArchiveFile().get().getAsFile().toPath(), (ClassLoader) null)) {
                            final Path configPath = fs.getPath(mixinsConfig);
                            if (Files.exists(configPath)) {
                                final String str = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                                Files.write(configPath, str.replace("regutils.refmap.json", configName + ".refmap.json").getBytes(StandardCharsets.UTF_8));
                            }

                            final Path fmj = fs.getPath("fabric.mod.json");
                            if (Files.exists(fmj)) {
                                final Map map = (Map) PARSER.parse(fmj);
                                final Object mixins = map.computeIfAbsent("mixins", o -> new ArrayList<>());
                                if (mixins instanceof List) {
                                    ((List) mixins).add(mixinsConfig);
                                } else {
                                    map.put("mixins", Lists.newArrayList(mixins, mixinsConfig));
                                }
                                Files.write(fmj, GENERATOR.toJson(map).getBytes(StandardCharsets.UTF_8));
                            }

                            final Path qmj = fs.getPath("quilt.mod.json");
                            if (Files.exists(qmj)) {
                                final Map map = (Map) PARSER.parse(qmj);
                                final Object mixins = map.computeIfAbsent("mixin", o -> new ArrayList<>());
                                if (mixins instanceof List) {
                                    ((List) mixins).add(mixinsConfig);
                                } else {
                                    map.put("mixin", Lists.newArrayList(mixins, mixinsConfig));
                                }
                                Files.write(qmj, GENERATOR.toJson(map).getBytes(StandardCharsets.UTF_8));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } else {
                tsk.from(extDir, spec -> spec.exclude("regutils.mixins.json", "regutils.refmap.json"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void extractSubDir(Path zipFile, Path outputPath, Predicate<String> predicate)
            throws IOException {
        // Open the file
        try (final ZipFile file = new ZipFile(zipFile.toFile())) {
            // Get file entries
            final Enumeration<? extends ZipEntry> entries = file.entries();

            // Iterate over entries
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                // Make sure we want the file
                if (predicate.test(entry.getName()) && !entry.isDirectory()) {
                    // Create the file
                    final InputStream is = file.getInputStream(entry);
                    final BufferedInputStream bis = new BufferedInputStream(is);
                    final Path uncompressedFilePath = outputPath.resolve(entry.getName());
                    Files.createDirectories(uncompressedFilePath.getParent());
                    final OutputStream fileOutput = Files.newOutputStream(uncompressedFilePath);
                    while (bis.available() > 0) {
                        fileOutput.write(bis.read());
                    }
                    fileOutput.close();
                }
            }
        }
    }

    @SuppressWarnings("ALL")
    public Dependency common() {
        final Dependency dep = project.getDependencies().create(dependencyNotation(null));
        maybeCreateJar(null, commonSourcesIn, "common", "com.matyrobbrt.registrationutils", commonSourcesJar);
        return dep;
    }

    @SuppressWarnings("ALL")
    public Dependency loaderSpecific() {
        final RegistrationUtilsExtension.SubProject.Type type = config.type.get();
        if (type == RegistrationUtilsExtension.SubProject.Type.COMMON)
            return common();
        final Dependency dep = project.getDependencies().create(dependencyNotation(type));
        maybeCreateJar(type, typeSourcesIn, type.toString(), "com.matyrobbrt.registrationutils", typeSourcesJar);
        return dep;
    }

    @SuppressWarnings("unused")
    public Dependency joined() {
        final RegistrationUtilsExtension.SubProject.Type type = config.type.get();
        if (type == RegistrationUtilsExtension.SubProject.Type.COMMON)
            return common();

        final Dependency dep = project.getDependencies().create(group + ":" + JAR_NAME + "-joined-" + type + ":" + RegistrationUtilsPlugin.VERSION);
        common(); // Make sure common exists
        final Path commonJarPath = getJarPath(null, null);
        loaderSpecific(); // Make sure type exists
        final Path typeJarPath = getJarPath(type, null);
        final Path outPath = cachePath.resolve(JAR_NAME + "-joined-" + type + "-" + RegistrationUtilsPlugin.VERSION + ".jar");

        if (Files.exists(outPath) && !(project.hasProperty(FORCE_GENERATION_PROPERTY) || project.getGradle().getStartParameter().isRefreshDependencies())) {
            return dep;
        }

        try {
            Files.deleteIfExists(outPath);
            Files.createFile(outPath);
            try (final JarFile commonJar = new JarFile(commonJarPath.toFile());
                 final JarFile typeJar = new JarFile(typeJarPath.toFile());
                 final JarOutputStream out = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outPath.toFile())))) {
                copyEntries(null, commonJar, out);
                copyEntries(commonJar, typeJar, out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return dep;
    }

    @ParametersAreNonnullByDefault
    private static void copyEntries(@Nullable JarFile other, JarFile in, JarOutputStream out) throws IOException {
        for (final Enumeration<JarEntry> entries = in.entries(); entries.hasMoreElements(); ) {
            final JarEntry entry = entries.nextElement();
            if (other == null || other.getEntry(entry.getName()) == null) {
                // Doesn't exist already
                out.putNextEntry(entry);
                IOUtils.copy(in.getInputStream(entry), out);
                out.closeEntry();
            }
        }
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
        final Path jarPath = getJarPath(type, null);
        if (Files.exists(jarPath) && !(project.hasProperty(FORCE_GENERATION_PROPERTY) || project.getGradle().getStartParameter().isRefreshDependencies())) {
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
            final boolean res = RelocateResourceTask.relocate(
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
                final Path finishedTemp = cachePath.resolve("comp").resolve(jarName);
                Files.createDirectories(finishedTemp.getParent());
                Files.deleteIfExists(finishedTemp);
                final Path jar = cachePath.resolve("befcompiled").resolve(jarName);
                Files.createDirectories(jar.getParent());
                Files.deleteIfExists(jar);
                Files.copy(Objects.requireNonNull(RegistrationUtilsExtension.class.getResourceAsStream("/" + resource + ".zip")), jar, StandardCopyOption.REPLACE_EXISTING);
                final JarRelocator relocator = new JarRelocator(jar.toFile(), finishedTemp.toFile(), Collections.singleton(new Relocation(inGroup, group)));
                relocator.run();

                final Pattern groupPattern = Pattern.compile(inGroup.replace(".", "\\."));
                final Pattern groupPatternND = Pattern.compile(inGroup.replace('.', '/'));

                // Now we gotta rename META-INFs or (mixin) jsons
                try (JarOutputStream out = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarPath.toFile())))) {
                    try (JarFile in = new JarFile(finishedTemp.toFile())) {
                        for (Enumeration<JarEntry> entries = in.entries(); entries.hasMoreElements(); ) {
                            final JarEntry entry = entries.nextElement();

                            final String name = entry.getName();
                            final InputStream is = in.getInputStream(entry);

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
                                serviceName = nameMatcher.replaceAll(group);
                                String content = RelocateResourceTask.readBytes(is).toString();
                                content = groupPattern.matcher(content).replaceAll(group);

                                final JarEntry newEntry = new JarEntry("META-INF/services/" + serviceName);
                                out.putNextEntry(newEntry);
                                out.write(content.getBytes(StandardCharsets.UTF_8));
                                out.closeEntry();
                            } else {
                                String content = RelocateResourceTask.readBytes(is).toString();
                                content = groupPattern.matcher(content).replaceAll(group);
                                content = groupPatternND.matcher(content).replaceAll(group.replace('.', '/'));

                                final JarEntry newEntry = new JarEntry(name);
                                out.putNextEntry(newEntry);
                                out.write(content.getBytes(StandardCharsets.UTF_8));
                                out.closeEntry();
                            }
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
        final String jarName = cachePath.relativize(getJarPath(type, null)).toString();
        final Path sourcesJar = getJarPath(type, "sources").toAbsolutePath();

        if (project.getPlugins().hasPlugin("eclipse")) {
            final EclipseModel eclipse = project.getExtensions().getByType(EclipseModel.class);
            eclipse.classpath(cp -> cp.file(file -> file.whenMerged((Classpath classpath) -> classpath.getEntries().stream()
                    .filter(Library.class::isInstance)
                    .map(Library.class::cast)
                    .filter(lib -> lib.getPath().contains(jarName))
                    .filter(lib -> lib.getSourcePath() == null)
                    .findFirst()
                    .ifPresent(lib -> lib.setSourcePath(classpath.fileReference(sourcesJar))))));
        }

        if (project.getPlugins().hasPlugin("idea")) {
            final IdeaPlugin idea = project.getPlugins().getPlugin(IdeaPlugin.class);
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
            return group + ":" + JAR_NAME + "-" + type + ":" + RegistrationUtilsPlugin.VERSION;
        }
    }

    public Path getJarPath(RegistrationUtilsExtension.SubProject.Type type, String classifier) {
        String actualClassifier = classifier == null || classifier.isEmpty() ? "" : "-" + classifier;
        if (type == null || type == RegistrationUtilsExtension.SubProject.Type.COMMON) {
            return cachePath.resolve(JAR_NAME + "-" + RegistrationUtilsPlugin.VERSION + actualClassifier + ".jar");
        } else {
            return cachePath.resolve(JAR_NAME + "-" + type + "-" + RegistrationUtilsPlugin.VERSION + actualClassifier + ".jar");
        }
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

        public void makeJar(Consumer<Exception> exception) {
            try {
                copy();
            } catch (Exception i) {
                exception.accept(i);
            }
        }
    }
}
