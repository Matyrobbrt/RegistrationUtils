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

import com.matyrobbrt.registrationutils.gradle.holderreg.MainClassHolderTransformer;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.Locale;

@SuppressWarnings("unused")
public class RegistrationUtilsExtension extends GroovyObjectSupport {
    public static final String NAME = "registrationUtils";

    protected final Project project;
    protected final NamedDomainObjectContainer<SubProject> projects;
    protected boolean addDependencies = true;
    protected final Property<String> group;
    protected final Property<String> extensionName;
    protected final Property<Boolean> transformHolderLoading;

    @Inject
    public RegistrationUtilsExtension(final Project project) {
        this.project = project;
        this.projects = project.getObjects().domainObjectContainer(SubProject.class, n -> new SubProject(project, n));
        this.group = project.getObjects().property(String.class).convention(project.getGroup().toString());
        this.extensionName = project.getObjects().property(String.class).convention(RegExtension.NAME);
        this.transformHolderLoading = project.getObjects().property(Boolean.class).convention(false);
    }

    public NamedDomainObjectContainer<SubProject> projects(@SuppressWarnings("rawtypes") Closure closure) {
        return projects.configure(closure);
    }

    public NamedDomainObjectContainer<SubProject> getProjects() {
        return projects;
    }

    public void addDependencies(boolean addDependencies) {
        this.addDependencies = addDependencies;
    }
    public void addDependencies() {
        addDependencies(true);
    }

    public void transformHolderLoading(boolean transformHolderLoading) {
        this.transformHolderLoading.set(transformHolderLoading);
    }
    public void transformHolderLoading() {
        transformHolderLoading(true);
    }

    public boolean addsDependencies() {
        return addDependencies;
    }
    public boolean transformsHolderLoading() {
        return transformHolderLoading.get();
    }

    public void group(String group) {
        this.group.set(group);
    }

    public void extensionName(String extensionName) {
        this.extensionName.set(extensionName);
    }

    public static class SubProject extends GroovyObjectSupport implements Serializable {
        private final String name;
        private final Project root;

        public final Property<Type> type;
        final Property<Project> project;
        public final Property<String> mainClass;
        public final Property<String> modInitMethod;

        @Inject
        public SubProject(Project root, String name) {
            this.name = name;
            this.root = root;
            this.type = root.getObjects().property(Type.class).convention(Type.COMMON);
            this.project = root.getObjects().property(Project.class).convention(root.findProject(":" + name));
            mainClass = root.getObjects().property(String.class);
            modInitMethod = root.getObjects().property(String.class).convention(MainClassHolderTransformer.COMPUTE_FLAG);

            try {
                type(name);
            } catch (IllegalArgumentException ignored) {}
        }

        public void type(String type) {
            this.type.set(switch (type.toLowerCase(Locale.ROOT)) {
                case "fabric", "quilt" -> Type.FABRIC; // Currently, quilt is the same as fabric
                case "forge" -> Type.FORGE;
                case "common" -> Type.COMMON;
                default -> throw new IllegalArgumentException("Unknown project type " + type);
            });
        }

        public void mainClass(String mainClass) {
            this.mainClass.set(mainClass);
        }

        public void modInitMethod(String modInitMethod) {
            this.modInitMethod.set(modInitMethod);
        }

        public void project(String name) {
            if (name.toLowerCase(Locale.ROOT).equals("root")) {
                project.set(root);
            } else {
                project.set(root.findProject(name));
            }
        }
        public void project(Project project) {
            this.project.set(project);
        }

        public String getName() {
            return name;
        }

        public enum Type {
            FABRIC(new MainClassHolderTransformer.LoadAllHolders("onInitialize()V")) {
                @Override
                public String toString() {
                    return "fabric";
                }
            },
            FORGE(new MainClassHolderTransformer.LoadAllHolders("<init>()V")) {
                @Override
                public String toString() {
                    return "forge";
                }
            },
            COMMON(null) {
                @Override
                public String toString() {
                    return "common";
                }
            };

            public final MainClassHolderTransformer mainClassHolderTransformer;

            Type(MainClassHolderTransformer mainClassHolderTransformer) {
                this.mainClassHolderTransformer = mainClassHolderTransformer;
            }
        }
    }
}
