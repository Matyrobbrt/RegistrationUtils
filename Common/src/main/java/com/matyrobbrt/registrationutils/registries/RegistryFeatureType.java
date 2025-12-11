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

import com.google.common.collect.MapMaker;
import net.minecraft.resources.Identifier;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Represents a feature a registry may have. <br>
 * Registry features are interned and ID-based, and can have arguments, unless they are of the {@link Void} type.
 *
 * @param <X> the type of the feature value
 * @see #get(Identifier, Class)
 * @see #getNoArgs(Identifier)
 * @see RegistryBuilder#withFeature(RegistryFeatureType, Object)
 */
public final class RegistryFeatureType<X> {
    private static final ConcurrentMap<Identifier, RegistryFeatureType<?>> VALUES = new MapMaker().weakValues().makeMap();

    /**
     * When a registry has this feature, its IDs will be synced between servers and clients. Works on both loaders.
     */
    public static final RegistryFeatureType<Void> SYNCED = getNoArgs(Identifier.withDefaultNamespace("synced"));

    /**
     * When a registry has this feature, it will be a {@link net.minecraft.core.DefaultedRegistry}. Works on both loaders. <br>
     * This feature accepts an argument of type {@link Identifier} which is the ID of the default value.
     *
     * @see RegistryBuilder#withDefaultValue(String, Supplier)
     */
    public static final RegistryFeatureType<Identifier> DEFAULTED = get(Identifier.withDefaultNamespace("defaulted"), Identifier.class);

    private final Identifier id;
    private final Class<X> argumentType;

    private RegistryFeatureType(Identifier id, Class<X> argumentType) {
        this.id = id;
        this.argumentType = argumentType;
    }

    @SuppressWarnings("unchecked")
    public static <X> RegistryFeatureType<X> get(Identifier id, Class<X> argumentType) {
        return (RegistryFeatureType<X>) VALUES.computeIfAbsent(id, identifier -> new RegistryFeatureType<>(identifier, argumentType));
    }

    public static RegistryFeatureType<Void> getNoArgs(Identifier id) {
        return get(id, Void.class);
    }

    public Identifier getId() {
        return id;
    }

    public Class<X> getArgumentType() {
        return argumentType;
    }
}
