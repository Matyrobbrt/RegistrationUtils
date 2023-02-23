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

package com.matyrobbrt.registrationutils.fabric;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableMap;
import com.matyrobbrt.registrationutils.registries.DatapackRegistryHelper;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

@ParametersAreNonnullByDefault
@AutoService(DatapackRegistryHelper.class)
public class FabricDatapackRegistryHelper implements DatapackRegistryHelper {
    private static final Unsafe UNSAFE;
    private static final MethodHandles.Lookup IMPL_LOOKUP;

    private static final Field WORLDGEN_REGISTRIES;
    private static final Field NETWORKABLE_REGISTRIES;
    private static final long offset$WORLDGEN_REGISTRIES;
    private static final long offset$NETWORKABLE_REGISTRIES;
    private static final MethodHandle new$NetworkedRegistryData;

    static {
        try {
            final var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);

            IMPL_LOOKUP = (MethodHandles.Lookup) UNSAFE.getObject(MethodHandles.Lookup.class, UNSAFE.staticFieldOffset(MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP")));

            WORLDGEN_REGISTRIES = Stream.of(RegistryDataLoader.class.getDeclaredFields())
                    .filter(fld -> Modifier.isStatic(fld.getModifiers()) && fld.getType() == List.class)
                    .filter(fld -> getStaticOrNull(fld) == RegistryDataLoader.WORLDGEN_REGISTRIES)
                    .findFirst().orElseThrow();

            offset$WORLDGEN_REGISTRIES = UNSAFE.staticFieldOffset(WORLDGEN_REGISTRIES);

            NETWORKABLE_REGISTRIES = Stream.of(RegistrySynchronization.class.getDeclaredFields())
                    .filter(it -> it.getType() == Map.class).findFirst().orElseThrow();
            offset$NETWORKABLE_REGISTRIES = UNSAFE.staticFieldOffset(NETWORKABLE_REGISTRIES);

            final Class<?> networkedRegistryData = Stream.of(RegistrySynchronization.class.getDeclaredClasses())
                    .filter(Class::isRecord).findFirst().orElseThrow();
            new$NetworkedRegistryData = IMPL_LOOKUP.findConstructor(networkedRegistryData, MethodType.methodType(void.class, ResourceKey.class, Codec.class));
        } catch (Exception ex) {
            throw new RuntimeException("Barf!", ex);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Function<RegistryAccess, Registry<T>> createRegistry(ResourceKey<Registry<T>> key, Codec<T> elementCodec, @Nullable Codec<T> networkCodec) {
        try {
            Objects.requireNonNull(key, "registry key must not be null");
            Objects.requireNonNull(elementCodec, "element codec must not be null");

            final List<RegistryDataLoader.RegistryData<?>> mutableCopy = new ArrayList<>(RegistryDataLoader.WORLDGEN_REGISTRIES);
            mutableCopy.add(new RegistryDataLoader.RegistryData<>(
                    key, elementCodec
            ));
            UNSAFE.putObject(RegistryDataLoader.class, offset$WORLDGEN_REGISTRIES, List.copyOf(mutableCopy));

            if (networkCodec != null) {
                final Object data = new$NetworkedRegistryData.invoke(key, networkCodec);

                Map<ResourceKey<? extends Registry<?>>, Object> registries = (Map<ResourceKey<? extends Registry<?>>, Object>) UNSAFE.getObject(RegistrySynchronization.class, offset$NETWORKABLE_REGISTRIES);
                if (registries == null) {
                    UNSAFE.allocateInstance(RegistrySynchronization.class); // Allocate a new instance of RegistrySynchronization in order to make sure the field is initialised
                    registries = (Map<ResourceKey<? extends Registry<?>>, Object>) UNSAFE.getObject(RegistrySynchronization.class, offset$NETWORKABLE_REGISTRIES);
                }

                final ImmutableMap.Builder<ResourceKey<? extends Registry<?>>, Object> builder = ImmutableMap.builder();
                builder.putAll(registries);
                builder.put(key, data);
                UNSAFE.putObject(RegistrySynchronization.class, offset$NETWORKABLE_REGISTRIES, builder.build());
            }
        } catch (Throwable ex) {
            throw new RuntimeException("Could not register datapack registry: ", ex);
        }
        return registryAccess -> registryAccess.registryOrThrow(key);
    }

    private static Object getStaticOrNull(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
