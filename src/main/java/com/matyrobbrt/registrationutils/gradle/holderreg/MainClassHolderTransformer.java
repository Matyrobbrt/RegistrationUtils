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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Locale;
import java.util.stream.StreamSupport;

// TODO this could be done such way that there could be multiple transformers
public interface MainClassHolderTransformer {

    String LOAD_ALL_METHOD_NAME = "loadAll";
    String LOAD_ALL_METHOD_DESC = "()V";
    String COMPUTE_FLAG = "compute";

    boolean transform(ClassNode clazz, String initMethod, String group);

    record LoadAllHolders(String defaultMethod) implements MainClassHolderTransformer {

        @Override
        public boolean transform(ClassNode clazz, String initMethod, String group) {
            final var type = group.replace('.', '/') + "/RegistryHolder";
            return clazz.methods
                    .stream()
                    .filter(n -> {
                        final var fullMethod = n.name + n.desc;
                        if (initMethod.toLowerCase(Locale.ROOT).equals(COMPUTE_FLAG)) {
                            return fullMethod.equals(defaultMethod);
                        } else {
                            return initMethod.equals(fullMethod);
                        }
                    })
                    .map(mthd -> {
                        final var newInsn = new MethodInsnNode(Opcodes.INVOKESTATIC, type, LOAD_ALL_METHOD_NAME, LOAD_ALL_METHOD_DESC, true);
                        // Basically this entire filter is to make sure `super()` methods are called before `loadAll`
                        StreamSupport.stream(mthd.instructions.spliterator(), false)
                            .filter(i -> i.getOpcode() == Opcodes.INVOKESPECIAL)
                            .filter(MethodInsnNode.class::isInstance)
                            .map(MethodInsnNode.class::cast)
                            .filter(i -> i.owner.equals(clazz.superName))
                            .filter(i -> i.name.equals("<init>"))
                            .findFirst()
                            .ifPresentOrElse(superInsn -> mthd.instructions.insert(superInsn, newInsn), () -> mthd.instructions.insert(newInsn));
                        return true;
                    })
                    .findFirst()
                    .orElse(false);
        }
    }
}
