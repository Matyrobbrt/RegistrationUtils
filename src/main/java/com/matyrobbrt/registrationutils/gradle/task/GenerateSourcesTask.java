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
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.*;
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
