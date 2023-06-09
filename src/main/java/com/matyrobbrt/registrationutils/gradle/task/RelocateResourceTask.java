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

package com.matyrobbrt.registrationutils.gradle.task;

import com.matyrobbrt.registrationutils.gradle.RegistrationUtilsPlugin;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public abstract class RelocateResourceTask extends DefaultTask implements Runnable {

    @Input
    public abstract Property<ZipInputStream> getInput();
    @Input
    public abstract Property<Path> getOutput();

    @Input
    public abstract Property<String> getFromGroup();
    @Input
    public abstract Property<String> getToGroup();

    @Override
    @TaskAction
    public void run() {
        relocate(getLogger(), getInput().get(), getOutput().get(), getFromGroup().get(), getToGroup().get());
    }

    public static boolean relocate(Logger logger, ZipInputStream input, Path output, String fromGroup, String toGroup) {
        return relocate(logger, input, output, fromGroup, toGroup, (s, f) -> s);
    }

    public static boolean relocate(Logger logger, ZipInputStream input, Path output, String fromGroup, String toGroup, BiFunction<String, FileData, String> extraReplaceFunction) {
        final Pattern regex = Pattern.compile(fromGroup.replace(".", "\\."));
        try {
            forEachEntry(input, (name, data) -> {
                final FileData fileData = FileData.create(name);
                if (fileData.fileName.equals("regutils.refmap.json")) {
                    final String str = data.toString();
                    Files.write(output.resolve(fileData.directory.isEmpty() ? fileData.fileName : fileData.directory + "/" + fileData.fileName),
                            str.replace(fromGroup.replace('.', '/'), toGroup.replace('.', '/')).getBytes(StandardCharsets.UTF_8));
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
                    Files.write(path, extraReplaceFunction.apply(regex.matcher(data.toString()).replaceAll(toGroup), fileData).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                }
            });
        } catch (IOException e) {
            logger.error("Exception trying to relocate resource: ", e);
            return false;
        }
        return true;
    }

    public static byte[] filesZip(Path directory) throws IOException {
        final ByteArrayOutputStream bo = new ByteArrayOutputStream();
        final ZipOutputStream zipOut = new ZipOutputStream(bo);
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                final ZipEntry zipEntry = new ZipEntry(directory.relativize(file).toString());
                zipOut.putNextEntry(zipEntry);
                zipOut.write(Files.readAllBytes(file));
                zipOut.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
        zipOut.close();
        return bo.toByteArray();
    }

    public static <X extends Exception> void forEachEntry(ZipInputStream stream, ThrowingBiConsumer<String, ByteArrayOutputStream, X> action) throws X, IOException {
        ZipEntry entry;
        while ((entry = stream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                try (final ByteArrayOutputStream bos = readBytes(stream)) {
                    action.accept(entry.getName(), bos);
                }
            }
        }
    }

    public static ByteArrayOutputStream readBytes(InputStream stream) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int len;
        while ((len = stream.read()) > 0) {
            bos.write(len);
        }
        return bos;
    }

    public interface ThrowingBiConsumer<A, B, X extends Exception> {
        void accept(A a, B b) throws X;
    }

    public static ZipInputStream getResourceDir(String zip) {
        final InputStream stream = Objects.requireNonNull(RegistrationUtilsPlugin.class.getResourceAsStream("/" + zip));
        return new ZipInputStream(stream);
    }

    public static class FileData {
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
