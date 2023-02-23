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

package com.matyrobbrt.registrationutils.registries;

import com.matyrobbrt.registrationutils.util.$InternalRegUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Function;

/**
 * A helper used to register custom datapack registries.
 *
 * @see #INSTANCE
 */
@ParametersAreNonnullByDefault
public interface DatapackRegistryHelper {
    /**
     * The singleton helper instance. Will be different on each platform.
     */
    DatapackRegistryHelper INSTANCE = $InternalRegUtils.getOneAndOnlyService(DatapackRegistryHelper.class);

    /**
     * Create and register a new datapack registry.
     *
     * @param key          the key of the registry. This will be used for determining the path of the registry folder. On Forge,
     *                     the path will be {@code data/<datapack_namespace>/key_namespace/key_path/}, whereas
     *                     on Fabric it will be {@code data/<datapack_namespace>/key_path/}, so you should mixin
     *                     into {@link net.minecraft.resources.RegistryDataLoader#registryDirPath(ResourceLocation)}
     *                     to make sure your registry's path is the same on Fabric.
     * @param elementCodec the codec used to decode registry elements
     * @param networkCodec the codec used to sync registry elements to clients. If {@code null}, the registry will
     *                     not be synced to clients, so it will not be accessible on the logical client
     * @param <T>          the type of the registry
     * @return a function used to get the registry from a {@link RegistryAccess} ({@link RegistryAccess#registryOrThrow(ResourceKey)}). This value may be ignored.
     */
    <T> Function<RegistryAccess, Registry<T>> createRegistry(
            ResourceKey<Registry<T>> key,
            Codec<T> elementCodec, @Nullable Codec<T> networkCodec
    );
}
