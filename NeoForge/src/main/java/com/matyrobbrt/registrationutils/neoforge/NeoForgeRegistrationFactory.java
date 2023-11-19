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

package com.matyrobbrt.registrationutils.neoforge;

import com.google.auto.service.AutoService;
import com.google.common.base.Suppliers;
import com.matyrobbrt.registrationutils.RegistrationProvider;
import com.matyrobbrt.registrationutils.RegistryObject;
import com.matyrobbrt.registrationutils.registries.RegistryBuilder;
import com.matyrobbrt.registrationutils.registries.RegistryFeatureType;
import com.matyrobbrt.registrationutils.specialised.BlockRegistrationProvider;
import com.matyrobbrt.registrationutils.specialised.BlockRegistryObject;
import com.matyrobbrt.registrationutils.specialised.ItemRegistrationProvider;
import com.matyrobbrt.registrationutils.specialised.ItemRegistryObject;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@AutoService(RegistrationProvider.Factory.class)
public class NeoForgeRegistrationFactory implements RegistrationProvider.Factory {

    @Override
    public <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> resourceKey, String modId) {
        final var register = DeferredRegister.create(resourceKey, modId);
        final Provider<T> provider = new Provider<>(modId, register);

        final IEventBus bus = getBus(modId);
        register.register(bus);
        bus.addListener(provider::onNewRegistry);

        return provider;
    }

    @Nonnull
    @ApiStatus.Internal
    static IEventBus getBus(String modId) {
        if (modId.equals("minecraft"))
            modId = "forge"; // Defer minecraft namespace to forge bus
        final var containerOpt = ModList.get().getModContainerById(modId);
        if (containerOpt.isEmpty())
            throw new NullPointerException("Cannot find mod container for id " + modId);
        final var modBus = NeoForgeBusGetter.getBus(containerOpt.get());
        if (modBus == null) {
            throw new NullPointerException("Cannot get the mod event bus for the mod container with the mod id of " + modId);
        }
        return modBus;
    }

    @Override
    public ItemRegistrationProvider item(String modId) {
        return new ItemProvider(modId);
    }

    @Override
    public BlockRegistrationProvider block(String modId) {
        return new BlockProvider(modId);
    }

    private static class ItemProvider extends Provider<Item> implements ItemRegistrationProvider {

        private ItemProvider(String modId) {
            super(modId, DeferredRegister.create(BuiltInRegistries.ITEM, modId));
        }

        @Override
        public <I extends Item> ItemRegistryObject<I> register(String name, Supplier<? extends I> supplier) {
            final var obj = new ItemRO<I>(registry.register(name, supplier));
            entries.add(obj);
            return obj;
        }

        private class ItemRO<I extends Item> extends RO<I> implements ItemRegistryObject<I> {

            protected ItemRO(DeferredHolder<Item, I> holder) {
                super(holder);
            }
        }
    }

    private static class BlockProvider extends Provider<Block> implements BlockRegistrationProvider {

        private BlockProvider(String modId) {
            super(modId, DeferredRegister.create(BuiltInRegistries.BLOCK, modId));
        }

        @Override
        public <B extends Block> BlockRegistryObject<B> register(String name, Supplier<? extends B> supplier) {
            final var obj = new BlockRO<B>(registry.register(name, supplier));
            entries.add(obj);
            return obj;
        }

        private class BlockRO<B extends Block> extends RO<B> implements BlockRegistryObject<B> {

            protected BlockRO(DeferredHolder<Block, B> holder) {
                super(holder);
            }
        }
    }

    private static class Provider<T> implements RegistrationProvider<T> {
        protected final String modId;
        protected final DeferredRegister<T> registry;
        private net.neoforged.neoforge.registries.RegistryBuilder<T> regBuilder;

        protected final Set<RegistryObject<T, ? extends T>> entries = new HashSet<>();
        private final Set<RegistryObject<T, ? extends T>> entriesView = Collections.unmodifiableSet(entries);

        private Provider(String modId, DeferredRegister<T> registry) {
            this.modId = modId;
            this.registry = registry;
        }

        private void onNewRegistry(NewRegistryEvent event) {
            if (regBuilder != null) {
                event.create(regBuilder);
            }
        }

        @Override
        public String getModId() {
            return modId;
        }

        @Override
        public ResourceKey<? extends Registry<T>> getRegistryKey() {
            return registry.getRegistryKey();
        }

        @SuppressWarnings("unchecked")
        private final Supplier<Registry<T>> registryInstance = Suppliers.memoize(() -> (Registry<T>) get(BuiltInRegistries.REGISTRY, getRegistryKey()));
        @Override
        public Registry<T> getRegistry() {
            return registryInstance.get();
        }

        @SuppressWarnings("unchecked")
        private static <T> T get(Registry<T> registry, ResourceKey<?> key) {
            return registry.get((ResourceKey<T>) key);
        }

        @Override
        public <I extends T> RegistryObject<T, I> register(String name, Supplier<? extends I> supplier) {
            final var obj = registry.<I>register(name, supplier);
            final var ro = new RO<>(obj);
            entries.add(ro);
            return ro;
        }

        protected class RO<I extends T> implements RegistryObject<T, I> {
            private final DeferredHolder<T, I> holder;

            protected RO(DeferredHolder<T, I> holder) {
                this.holder = holder;
            }

            @Override
            public ResourceKey<T> getResourceKey() {
                return holder.getKey();
            }

            @Override
            public ResourceLocation getId() {
                return holder.getId();
            }

            @Override
            public I get() {
                return holder.get();
            }

            @Override
            public Holder<T> asHolder() {
                return holder;
            }
        };

        @Override
        public Set<RegistryObject<T, ? extends T>> getEntries() {
            return entriesView;
        }

        @Override
        public RegistryBuilder<T> registryBuilder() {
            return new Builder();
        }

        private final class Builder implements RegistryBuilder<T> {
            private final net.neoforged.neoforge.registries.RegistryBuilder<T> builder = new net.neoforged.neoforge.registries.RegistryBuilder<>(getRegistryKey());
            private final Map<RegistryFeatureType<?>, Object> features = new HashMap<>();

            @Override
            public <X> RegistryBuilder<T> withFeature(RegistryFeatureType<X> type, X value) {
                features.put(type, value);
                return this;
            }

            @Override
            public RegistryBuilder<T> withFeature(RegistryFeatureType<Void> type) {
                return this.withFeature(type, null);
            }

            @Override
            public RegistryBuilder<T> withDefaultValue(String id, Supplier<T> defaultValueSupplier) {
                register(id, defaultValueSupplier);
                return withFeature(RegistryFeatureType.DEFAULTED, new ResourceLocation(modId, id));
            }

            @Override
            public Supplier<Registry<T>> build() {
                configureBuilder();
                Provider.this.regBuilder = builder;
                return Provider.this.registryInstance;
            }

            private void configureBuilder() {
                builder.sync(features.containsKey(RegistryFeatureType.SYNCED));
                if (features.containsKey(RegistryFeatureType.DEFAULTED)) {
                    builder.defaultKey((ResourceLocation) features.get(RegistryFeatureType.DEFAULTED));
                }
            }
        }
    }
}
