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

import com.matyrobbrt.registrationutils.RegistrationProvider;
import com.matyrobbrt.registrationutils.RegistryObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class FabricRegistrationFactory implements RegistrationProvider.Factory {

    @Override
    public <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> resourceKey, String modId) {
        return new Provider<>(modId, resourceKey);
    }

    @Override
    public <T> RegistrationProvider<T> create(Registry<T> registry, String modId) {
        return new Provider<>(modId, registry);
    }

    private static class Provider<T> implements RegistrationProvider<T> {
        private final String modId;
        private final Registry<T> registry;

        private final Set<RegistryObject<T>> entries = new HashSet<>();
        private final Set<RegistryObject<T>> entriesView = Collections.unmodifiableSet(entries);

        @SuppressWarnings({"unchecked"})
        private Provider(String modId, ResourceKey<? extends Registry<T>> key) {
            this.modId = modId;

            final var reg = Registry.REGISTRY.get(key.location());
            if (reg == null) {
                throw new RuntimeException("Registry with name " + key.location() + " was not found!");
            }
            registry = (Registry<T>) reg;
        }

        private Provider(String modId, Registry<T> registry) {
            this.modId = modId;
            this.registry = registry;
        }

        @Override
        public Registry<T> getRegistry() {
            return registry;
        }

        @Override
        public ResourceKey<? extends Registry<T>> getRegistryKey() {
            return registry.key();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
            final var rl = new ResourceLocation(modId, name);
            final var obj = Registry.register(registry, rl, supplier.get());
            final var ro = new RegistryObject<I>() {
                final ResourceKey<I> key = ResourceKey.create((ResourceKey<? extends Registry<I>>) registry.key(), rl);

                @Override
                public ResourceKey<I> getResourceKey() {
                    return key;
                }

                @Override
                public ResourceLocation getId() {
                    return rl;
                }

                @Override
                public I get() {
                    return obj;
                }

                @Override
                public Holder<I> asHolder() {
                    return (Holder<I>) registry.getOrCreateHolderOrThrow((ResourceKey<T>) this.key);
                }
            };
            entries.add((RegistryObject<T>) ro);
            return ro;
        }

        @Override
        public Collection<RegistryObject<T>> getEntries() {
            return entriesView;
        }

        @Override
        public String getModId() {
            return modId;
        }
    }
}