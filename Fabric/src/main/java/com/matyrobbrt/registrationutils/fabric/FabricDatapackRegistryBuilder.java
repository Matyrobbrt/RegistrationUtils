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
import com.matyrobbrt.registrationutils.registries.DatapackRegistry;
import com.matyrobbrt.registrationutils.registries.DatapackRegistryBuilder;
import com.matyrobbrt.registrationutils.util.DatapackRegistryGenerator;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@ParametersAreNonnullByDefault
public class FabricDatapackRegistryBuilder<T> implements DatapackRegistryBuilder<T> {
    private static final Unsafe UNSAFE;

    private static final Field WORLDGEN_REGISTRIES;
    private static final Field SYNCHRONIZED_REGISTRIES;
    private static final Field NETWORKABLE_REGISTRIES;
    private static final long offset$WORLDGEN_REGISTRIES;
    private static final long offset$SYNCHRONIZED_REGISTRIES;
    private static final long offset$NETWORKABLE_REGISTRIES;

    public static final Set<ResourceLocation> OWNED_REGISTRIES = new HashSet<>();

    static {
        try {
            final var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);

            WORLDGEN_REGISTRIES = Stream.of(RegistryDataLoader.class.getDeclaredFields())
                    .filter(fld -> Modifier.isStatic(fld.getModifiers()) && fld.getType() == List.class)
                    .filter(fld -> getStaticOrNull(fld) == RegistryDataLoader.WORLDGEN_REGISTRIES)
                    .findFirst().orElseThrow();

            offset$WORLDGEN_REGISTRIES = UNSAFE.staticFieldOffset(WORLDGEN_REGISTRIES);

            SYNCHRONIZED_REGISTRIES = Stream.of(RegistryDataLoader.class.getDeclaredFields())
                    .filter(fld -> Modifier.isStatic(fld.getModifiers()) && fld.getType() == List.class)
                    .filter(fld -> getStaticOrNull(fld) == RegistryDataLoader.SYNCHRONIZED_REGISTRIES)
                    .findFirst().orElseThrow();

            offset$SYNCHRONIZED_REGISTRIES = UNSAFE.staticFieldOffset(SYNCHRONIZED_REGISTRIES);

            NETWORKABLE_REGISTRIES = Stream.of(RegistrySynchronization.class.getDeclaredFields())
                    .filter(it -> it.getType() == Set.class).findFirst().orElseThrow();
            offset$NETWORKABLE_REGISTRIES = UNSAFE.staticFieldOffset(NETWORKABLE_REGISTRIES);
        } catch (Exception ex) {
            throw new RuntimeException("Barf!", ex);
        }
    }

    private final ResourceKey<Registry<T>> key;
    private Codec<T> elementCodec;
    private @Nullable Codec<T> networkCodec;
    private @Nullable RegistrySetBuilder.RegistryBootstrap<T> bootstrap;

    private FabricDatapackRegistryBuilder(ResourceKey<Registry<T>> key) {
        this.key = Objects.requireNonNull(key, "registry key must not be null");
    }

    @Override
    public DatapackRegistryBuilder<T> withElementCodec(@NotNull Codec<T> codec) {
        this.elementCodec = Objects.requireNonNull(codec, "element codec must not be null");
        return this;
    }

    @Override
    public DatapackRegistryBuilder<T> withNetworkCodec(@Nullable Codec<T> codec) {
        this.networkCodec = codec;
        return this;
    }

    @Override
    public DatapackRegistryBuilder<T> withBootstrap(@Nullable RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        this.bootstrap = bootstrap;
        return this;
    }

    @Override
    public DatapackRegistry<T> build() {
        Objects.requireNonNull(elementCodec, "element codec must not be null");

        OWNED_REGISTRIES.add(key.location());

        try {
            registerWithFAPI();
        } catch (Exception cnfe) {
            try {
                final List<RegistryDataLoader.RegistryData<?>> mutableCopy = new ArrayList<>(RegistryDataLoader.WORLDGEN_REGISTRIES);
                mutableCopy.add(new RegistryDataLoader.RegistryData<>(
                        key, elementCodec, false
                ));
                UNSAFE.putObject(RegistryDataLoader.class, offset$WORLDGEN_REGISTRIES, List.copyOf(mutableCopy));

                if (networkCodec != null) {
                    final List<RegistryDataLoader.RegistryData<?>> mutableNetwork = new ArrayList<>(RegistryDataLoader.SYNCHRONIZED_REGISTRIES);
                    mutableNetwork.add(new RegistryDataLoader.RegistryData<>(
                            key, networkCodec, false
                    ));
                    UNSAFE.putObject(RegistryDataLoader.class, offset$SYNCHRONIZED_REGISTRIES, List.copyOf(mutableNetwork));

                    final Set<ResourceKey<? extends Registry<?>>> networkable = new HashSet<>(RegistrySynchronization.NETWORKABLE_REGISTRIES);
                    networkable.add(key);
                    UNSAFE.putObject(RegistrySynchronization.class, offset$NETWORKABLE_REGISTRIES, Set.copyOf(networkable));
                }
            } catch (Throwable throwable) {
                throw new RuntimeException("Could not register datapack registry: ", throwable);
            }
        }

        return new DatapackRegistry<>() {
            @Override
            public ResourceKey<Registry<T>> key() {
                return key;
            }

            @Override
            public DataProvider.Factory<DataProvider> bootstrapDataGenerator(CompletableFuture<HolderLookup.Provider> lookupProvider) {
                return packOutput -> new DatapackRegistryGenerator(packOutput, lookupProvider, registryData -> registryData.key() == key());
            }

            @Override
            public void addToSet(RegistrySetBuilder builder) {
                builder.add(key, bootstrap == null ? ctx -> {} : bootstrap);
            }

            @Override
            public Registry<T> get(RegistryAccess registryAccess) {
                return registryAccess.registryOrThrow(key);
            }
        };
    }

    private void registerWithFAPI() {
        if (networkCodec == null) {
            DynamicRegistries.register(key, elementCodec);
        } else {
            DynamicRegistries.registerSynced(key, elementCodec, networkCodec);
        }
    }

    private static Object getStaticOrNull(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @AutoService($Factory.class)
    public static final class Factory implements $Factory {

        @Override
        public <T> DatapackRegistryBuilder<T> newBuilder(ResourceKey<Registry<T>> key) {
            return new FabricDatapackRegistryBuilder<>(key);
        }
    }
}
