package com.matyrobbrt.registrationutils.fabric;

import com.matyrobbrt.registrationutils.ArgumentTypeHelper;
import com.matyrobbrt.registrationutils.RegistrationProvider;
import com.matyrobbrt.registrationutils.RegistryObject;
import com.mojang.brigadier.arguments.ArgumentType;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

@ApiStatus.Internal
public class FabricArgumentTypeHelper implements ArgumentTypeHelper {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> RegistryObject<I> register(RegistrationProvider<ArgumentTypeInfo<?, ?>> provider, String name, Class<A> clazz, Supplier<I> serializer) {
        final ResourceLocation loc = new ResourceLocation(provider.getModId(), name);
        final I ser = serializer.get();
        ArgumentTypeRegistry.registerArgumentType(loc, clazz, ser);

        if (provider instanceof FabricRegistrationFactory.InternalFabricHelper helper) {
            return helper.create(loc, ser);
        }
        return new RegistryObject<>() {
            final ResourceKey<I> key = (ResourceKey<I>) ResourceKey.create(Registries.COMMAND_ARGUMENT_TYPE, loc);
            final Holder<I> holder = (Holder<I>) BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getHolder(ResourceKey.create(Registries.COMMAND_ARGUMENT_TYPE, loc))
                    .orElseThrow();

            @Override
            public ResourceKey<I> getResourceKey() {
                return key;
            }

            @Override
            public ResourceLocation getId() {
                return loc;
            }

            @Override
            public I get() {
                return ser;
            }

            @Override
            public Holder<I> asHolder() {
                return holder;
            }
        };
    }
}
