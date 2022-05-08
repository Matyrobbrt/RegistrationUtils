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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
        final var regex = Pattern.compile(fromGroup.replace(".", "\\."));
        try {
            forEachEntry(input, (name, data) -> {
                final var fileData = FileData.create(name);
                final var relocatedName = regex.matcher(fileData.fileName()).replaceAll(toGroup);
                final var pkg = fileData.directory().replace('/', '.');
                final var path = output.resolve(fileData.directory().isEmpty() ? relocatedName : regex.matcher(pkg).replaceAll(toGroup).replace('.', '/') + "/" + relocatedName);
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.deleteIfExists(path);
                Files.writeString(path, extraReplaceFunction.apply(regex.matcher(data).replaceAll(toGroup), fileData), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            });
        } catch (IOException e) {
            logger.error("Exception trying to relocate resource: ", e);
            return false;
        }
        return true;
    }

    public static <X extends Exception> void forEachEntry(ZipInputStream stream, ThrowingBiConsumer<String, String, X> action) throws X, IOException {
        ZipEntry entry;
        while ((entry = stream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                try (final var bos = new ByteArrayOutputStream()) {
                    int len;
                    while ((len = stream.read()) > 0) {
                        bos.write(len);
                    }
                    action.accept(entry.getName(), bos.toString());
                }
            }
        }
    }

    public interface ThrowingBiConsumer<A, B, X extends Exception> {
        void accept(A a, B b) throws X;
    }

    public static ZipInputStream getResourceDir(String zip) {
        final var stream = Objects.requireNonNull(RegistrationUtilsPlugin.class.getResourceAsStream("/" + zip));
        return new ZipInputStream(stream);
    }

    public record FileData(String fileName, String directory) {
        public static FileData create(String name) {
            final var split = name.split("/");
            final var fileName = split[split.length - 1];
            final var dir = split.length == 1 ? "" : String.join("/", List.of(split).subList(0, split.length - 1));
            return new FileData(fileName, dir);
        }
    }

}
