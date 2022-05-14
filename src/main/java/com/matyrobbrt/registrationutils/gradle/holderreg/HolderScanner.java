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
import org.objectweb.asm.Label;
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

import static org.objectweb.asm.Opcodes.*;

public class HolderScanner {
    public static final String INNER_NAME = "RegUtils";

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
            final var cw = new ClassWriter(Opcodes.ASM9);
            if ((clazz.access & ACC_INTERFACE) != 0) {
                // Is an interface, we need to use an inner class
                final var innerName = clazz.name + "$" + INNER_NAME;
                clazz.visitInnerClass(innerName, clazz.name, INNER_NAME, ACC_PUBLIC | Opcodes.ACC_STATIC | ACC_SUPER);

                final var innerCw = new ClassWriter(0);

                innerCw.visit(V17, ACC_PUBLIC | ACC_SUPER, innerName, null, "java/lang/Object", new String[] {registryHolderType});
                {
                    final var methodVisitor = innerCw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    methodVisitor.visitCode();
                    Label label0 = new Label();
                    methodVisitor.visitLabel(label0);
                    methodVisitor.visitLineNumber(7, label0);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    Label label1 = new Label();
                    methodVisitor.visitLabel(label1);
                    methodVisitor.visitLineNumber(8, label1);
                    methodVisitor.visitLdcInsn(Type.getObjectType(clazz.name));
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
                    methodVisitor.visitInsn(POP);
                    Label label2 = new Label();
                    methodVisitor.visitLabel(label2);
                    methodVisitor.visitLineNumber(9, label2);
                    methodVisitor.visitInsn(RETURN);
                    Label label3 = new Label();
                    methodVisitor.visitLabel(label3);
                    methodVisitor.visitLocalVariable("this", "L" + innerName + ";", null, label0, label3, 0);
                    methodVisitor.visitMaxs(1, 1);
                    methodVisitor.visitEnd();
                }
                innerCw.visitEnd();

                Files.write(path.getParent().resolve(path.toString().replace(".class", "") + "$" + INNER_NAME + ".class"), innerCw.toByteArray());

                foundClasses.add(innerName.replace('/', '.'));
                logger.trace("Transforming interface {}: adding inner class {} with RegistryHolder interface", clazz.name, INNER_NAME);
            } else {
                foundClasses.add(clazz.name.replace('/', '.'));
                clazz.interfaces.add(registryHolderType);
                clazz.methods.stream()
                        .filter(m -> m.name.equals("<init>"))
                        .forEach(n -> n.access = changeAccess(n.access));
                logger.trace("Transforming class {}: adding RegistryHolder interface", clazz.name);
            }
            clazz.accept(cw);
            Files.write(path, cw.toByteArray());
        }
        return found;
    }

    public Collection<String> getFoundClasses() {
        return foundClasses;
    }

    public static int changeAccess(final int access) {
        return access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | ACC_PUBLIC;
    }
}
