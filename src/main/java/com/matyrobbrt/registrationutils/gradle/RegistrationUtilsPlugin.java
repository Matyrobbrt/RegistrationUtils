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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;

public class RegistrationUtilsPlugin implements Plugin<Project> {

    public static final String VERSION;
    static {
        final String ver = RegistrationUtilsPlugin.class.getPackage().getImplementationVersion();
        VERSION = ver == null ? "dev" : ver;
    }

    public static final String CACHE_FOLDER = "registrationutils";

    public static final String CONFIGURATION_NAME = "registrationUtils";

    @Override
    public void apply(Project project) {
        final RegistrationUtilsExtension ext = project.getExtensions().create(RegistrationUtilsExtension.NAME, RegistrationUtilsExtension.class, project);
        project.afterEvaluate($$ -> {
            ext.getProjects().forEach(sub -> {
                final Project proj = sub.project.get();
                final RegExtension reg = proj.getExtensions().create(ext.extensionName.get(), RegExtension.class, project, proj, ext, sub);
                if (ext.addsDependencies()) {
                    final Configuration regUtilsConfig = proj.getConfigurations().maybeCreate(CONFIGURATION_NAME);
                    regUtilsConfig.getDependencies().add(reg.common());

                    final Configuration compConfig = proj.getConfigurations().findByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);
                    if (compConfig != null) {
                        compConfig.extendsFrom(regUtilsConfig);
                    }

                    final Configuration testCompConfig = proj.getConfigurations().findByName(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME);
                    if (testCompConfig != null) {
                        testCompConfig.extendsFrom(regUtilsConfig);
                    }

                    if (sub.type.get() != RegistrationUtilsExtension.SubProject.Type.COMMON) {
                        final Configuration runtimeClasspathConfig = proj.getConfigurations().findByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
                        if (runtimeClasspathConfig != null) {
                            runtimeClasspathConfig.extendsFrom(regUtilsConfig);
                        }
                    }
                }
            });
        });
    }
}
