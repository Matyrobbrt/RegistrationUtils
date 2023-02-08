package com.matyrobbrt.registrationutils;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.Util;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;

import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * A helper used to register custom {@link ArgumentTypeInfo Argument Types}.
 * <p>
 * Example usage:
 * <pre>{@code
 * public static final RegistrationProvider<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES = RegistrationProvider.get(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, "modid");
 * public static final RegistryObject<SingletonArgumentInfo<CustomArgument>> CUSTOM_ARGUMENT = ArgumentTypeHelper.INSTANCE.register(ARGUMENT_TYPES, "custom", CustomArgument.class, () -> SingletonArgumentInfo.contextFree(CustomArgument::new));
 *
 * // The purpose of this method is to be called in the mod's constructor, in order to assure that the class is loaded, and that objects can be registered.
 * public static void loadClass(){}
 * }</pre>
 */
public interface ArgumentTypeHelper {

    /**
     * The singleton helper instance. Will be different on each platform.
     */
    ArgumentTypeHelper INSTANCE = Util.make(() -> {
        final var loader = ServiceLoader.load(ArgumentTypeHelper.class);
        final var it = loader.iterator();
        if (!it.hasNext()) {
            throw new RuntimeException("No ArgumentTypeHelper was found on the classpath!");
        } else {
            final ArgumentTypeHelper helper = it.next();
            if (it.hasNext()) {
                throw new RuntimeException("More than one ArgumentTypeHelper was found on the classpath!");
            }
            return helper;
        }
    });

    /**
     * Registers an {@link ArgumentTypeInfo}.
     *
     * @param provider   the provider to register to
     * @param name       the name of the argument
     * @param clazz      the class of the argument
     * @param serializer the argument serializer
     * @param <A>        the type of the argument
     * @param <T>        the argument template type
     * @param <I>        the argument serializer type
     * @return a wrapper containing the lazy registered object. <strong>Calling get too early on the wrapper might result in crashes!</strong>
     */
    <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> RegistryObject<I> register(
            RegistrationProvider<ArgumentTypeInfo<?, ?>> provider,
            String name, Class<A> clazz,
            Supplier<I> serializer
    );
}
