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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.Objects;

public class RegistrationUtilsPlugin implements Plugin<Project> {

    public static final String VERSION = RegistrationUtilsPlugin.class.getPackage().getImplementationVersion();
    public static final String CACHE_FOLDER = "registrationutils";

    @Override
    public void apply(Project project) {
        final var ext = project.getExtensions().create(RegistrationUtilsExtension.NAME, RegistrationUtilsExtension.class, project);
        project.afterEvaluate($$ -> {
            ext.getProjects().forEach(sub -> {
                final var proj = sub.project.get();
                final var reg = proj.getExtensions().create(ext.extensionName.get(), RegExtension.class, project, proj, ext, sub);
                // Force project evaluation
                ((ProjectInternal) proj).evaluate();
                if (ext.addsDependencies()) {
                    final var type = sub.type.get();
                    final var implConfiguration = proj.getConfigurations().findByName("implementation");
                    sub.configuration.getOrElse(Objects.requireNonNull(implConfiguration, "Default implementation configuration was not found! Consider setting a custom one!")).getDependencies()
                            .add(reg.common());

                    if (type != RegistrationUtilsExtension.SubProject.Type.COMMON) {
                        final var runtimeConfiguration = proj.getConfigurations().findByName("runtimeOnly");
                        sub.configuration.getOrElse(Objects.requireNonNull(runtimeConfiguration, "Default runtimeOnly configuration was not found! Consider setting a custom runtime configuration!")).getDependencies()
                                .add(reg.loaderSpecific());
                    }
                }
            });
        });
        ((ProjectInternal) project).evaluate(); // We need to force project evaluation
    }
}
