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

import net.minecraftforge.artifactural.api.artifact.Artifact;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.api.artifact.ArtifactType;
import net.minecraftforge.artifactural.api.repository.ArtifactProvider;
import net.minecraftforge.artifactural.base.artifact.StreamableArtifact;

import java.nio.file.Files;
import java.nio.file.Path;

public record RegArtifactProvider(String group, Path dir) implements ArtifactProvider<ArtifactIdentifier> {

    @Override
    public Artifact getArtifact(ArtifactIdentifier info) {
        if (!info.getGroup().equals(group)) {
            // System.out.println("Requested " + info + " could not provide...");
            return Artifact.none(); // We only want ours
        }
        final Path file;
        if (info.getClassifier() == null) {
            file = dir.resolve(info.getName() + "-" + info.getVersion() + "." + info.getExtension());
        } else {
            file = dir.resolve(info.getName() + "-" + info.getVersion() + "-" + info.getClassifier() + "." + info.getExtension());
        }
        // System.out.println("Requested " + info + " at " + file);
        if (!Files.exists(file)) {
            return Artifact.none();
        }
        ArtifactType type = ArtifactType.OTHER;
        if (info.getClassifier() != null && info.getClassifier().endsWith("sources"))
            type = ArtifactType.SOURCE;
        else if ("jar".equals(info.getExtension()))
            type = ArtifactType.BINARY;
        return StreamableArtifact.ofFile(info, type, file.toFile());
    }
}
