package com.matyrobbrt.registrationutils.forge;

import com.matyrobbrt.registrationutils.ArgumentTypeHelper;
import com.matyrobbrt.registrationutils.RegistrationProvider;
import com.matyrobbrt.registrationutils.RegistryObject;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

@ApiStatus.Internal
public class ForgeArgumentTypeHelper implements ArgumentTypeHelper {
    @Override
    public <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> RegistryObject<I> register(RegistrationProvider<ArgumentTypeInfo<?, ?>> provider, String name, Class<A> clazz, Supplier<I> serializer) {
        return provider.register(name, () -> ArgumentTypeInfos.registerByClass(clazz, serializer.get()));
    }
}
