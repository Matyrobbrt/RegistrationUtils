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

package com.matyrobbrt.registrationutils.gradle.holderreg;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class HolderScanner {
    private final Set<String> foundClasses = new HashSet<>();

    private final String registrationProvider;
    private final String registryHolderType;
    private final Logger logger;

    public HolderScanner(Logger logger, String pkg) {
        this.logger = logger;
        pkg = pkg.replace('.', '/');
        registrationProvider = Type.getType("L" + pkg + "/RegistrationProvider;").getDescriptor();
        registryHolderType = Type.getType("L" + pkg + "/RegistryHolder;").getInternalName();
    }

    @SuppressWarnings("ALL")
    public boolean processClass(Path path) throws IOException {
        final var cr = new ClassReader(Files.readAllBytes(path));
        if (Arrays.asList(cr.getInterfaces()).contains(registryHolderType)) {
            return false;
        }
        boolean found = false;
        ClassNode clazz = new ClassNode(Opcodes.ASM9);
        cr.accept(clazz, 0);
        for (final var node : clazz.fields) {
            if (node.desc.equals(registrationProvider) && Modifier.isStatic(node.access)) {
                found = true;
                break;
            }
        }
        if (found) {
            foundClasses.add(clazz.name.replace('/', '.'));
            final var cw = new ClassWriter(Opcodes.ASM9);
            clazz.interfaces.add(registryHolderType);
            clazz.methods.stream()
                    .filter(m -> m.name.equals("<init>"))
                    .forEach(n -> n.access = changeAccess(n.access));
            clazz.accept(cw);
            logger.trace("Transforming class {}: adding RegistryHolder interface", clazz.name);
            Files.write(path, cw.toByteArray());
        }
        return found;
    }

    public Collection<String> getFoundClasses() {
        return foundClasses;
    }

    public static int changeAccess(final int access) {
        return access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
    }
}
