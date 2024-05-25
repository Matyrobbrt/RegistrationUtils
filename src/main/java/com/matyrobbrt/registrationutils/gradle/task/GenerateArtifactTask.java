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
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class GenerateArtifactTask extends GenerateTask {

    @Inject
    public GenerateArtifactTask() {
        getInitialGroup().convention("com.matyrobbrt.registrationutils");
        getOutputJar().convention(getProject().getLayout().getBuildDirectory().file(getResource().map(r -> "registrationutils/" + r + "-" + RegistrationUtilsPlugin.VERSION + ".jar")));
    }

    @TaskAction
    public void run() {
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
                            String content = readBytes(is).toString();
                            content = groupPattern.matcher(content).replaceAll(getTargetGroup().get());

                            final JarEntry newEntry = new JarEntry("META-INF/services/" + serviceName);
                            out.putNextEntry(newEntry);
                            out.write(content.getBytes(StandardCharsets.UTF_8));
                            out.closeEntry();
                        } else {
                            String content = readBytes(is).toString();
                            content = groupPattern.matcher(content).replaceAll(getTargetGroup().get());
                            content = groupPatternND.matcher(content).replaceAll(getTargetGroup().get().replace('.', '/'));

                            final JarEntry newEntry = new JarEntry(name);
                            out.putNextEntry(newEntry);
                            out.write(content.getBytes(StandardCharsets.UTF_8));
                            out.closeEntry();
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean relocate(Logger logger, ZipInputStream input, Path output, String fromGroup, String toGroup) {
        final Pattern regex = Pattern.compile(fromGroup.replace(".", "\\."));
        try {
            forEachEntry(input, (name, data) -> {
                final FileData fileData = FileData.create(name);
                if (fileData.fileName.equals("regutils.refmap.json")) {
                    final String str = data.toString();
                    Files.writeString(output.resolve(fileData.directory.isEmpty() ? fileData.fileName : fileData.directory + "/" + fileData.fileName),
                            str.replace(fromGroup.replace('.', '/'), toGroup.replace('.', '/')));
                    return;
                }

                final String relocatedName = regex.matcher(fileData.fileName).replaceAll(toGroup);
                final String pkg = fileData.directory.replace('/', '.');
                Path path = output.resolve(fileData.directory.isEmpty() ? relocatedName : regex.matcher(pkg).replaceAll(toGroup).replace('.', '/') + "/" + relocatedName);
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.deleteIfExists(path);
                if (fileData.fileName.endsWith(".class")) { // Don't process class files
                    path = output.resolve(name);
                    Files.copy(new ByteArrayInputStream(data.toByteArray()), path, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.writeString(path, regex.matcher(data.toString()).replaceAll(toGroup), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                }
            });
        } catch (IOException e) {
            logger.error("Exception trying to relocate resource: ", e);
            return false;
        }
        return true;
    }

    private static ByteArrayOutputStream readBytes(InputStream stream) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int len;
        while ((len = stream.read()) > 0) {
            bos.write(len);
        }
        return bos;
    }

    static ZipInputStream getResourceDir(String zip) {
        final InputStream stream = Objects.requireNonNull(RegistrationUtilsPlugin.class.getResourceAsStream("/" + zip));
        return new ZipInputStream(stream);
    }

    private static <X extends Exception> void forEachEntry(ZipInputStream stream, ThrowingBiConsumer<String, ByteArrayOutputStream, X> action) throws X, IOException {
        ZipEntry entry;
        while ((entry = stream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                try (final ByteArrayOutputStream bos = readBytes(stream)) {
                    action.accept(entry.getName(), bos);
                }
            }
        }
    }

    private interface ThrowingBiConsumer<A, B, X extends Exception> {
        void accept(A a, B b) throws X;
    }

    private static class FileData {
        public final String fileName, directory;
        public FileData(String fileName, String directory) {
            this.fileName = fileName;
            this.directory = directory;
        }

        public static FileData create(String name) {
            final String[] split = name.split("/");
            final String fileName = split[split.length - 1];
            final String dir = split.length == 1 ? "" : String.join("/", Arrays.asList(split).subList(0, split.length - 1));
            return new FileData(fileName, dir);
        }
    }
}
