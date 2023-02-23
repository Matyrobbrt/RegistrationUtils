package com.matyrobbrt.regtest;

import com.matyrobbrt.regtest.registries.DatapackRegistryHelper;
import com.matyrobbrt.regtest.registries.RegistryFeatureType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.client.player.ClientPickBlockApplyCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;

import java.util.function.Function;
import java.util.function.Supplier;

public class RegTest implements ModInitializer {
    private static final RegistrationProvider<MyObject> MY_OBJECTS = RegistrationProvider.get(new ResourceLocation("regtest:myregistry"), "regtest");

    public static final Supplier<Registry<MyObject>> MY_OBJECT_REGISTRY = MY_OBJECTS.registryBuilder()
            .withFeature(RegistryFeatureType.SYNCED)
            .withDefaultValue("default", () -> new MyObject(12))
            .build();

    public static final RegistryObject<MyObject> SOME_OBJECT = MY_OBJECTS.register("yes", () -> new MyObject(14));

    public static final Function<RegistryAccess, Registry<MyObject>> MY_DP_OBJECTS = DatapackRegistryHelper.INSTANCE
            .createRegistry(ResourceKey.createRegistryKey(new ResourceLocation("regtest", "test_dp")), MyObject.CODEC, null);

    public record MyObject(int someInt) {
        public static final Codec<MyObject> CODEC = RecordCodecBuilder.create(in -> in.group(
                Codec.INT.fieldOf("someInt").forGetter(MyObject::someInt)
        ).apply(in, MyObject::new));
    }

    @Override
    public void onInitialize() {
        MY_OBJECT_REGISTRY.get().holders().forEach(holder -> System.out.println("Key: " + holder.key().location() + ", value: " + holder.value()));

        UseItemCallback.EVENT.register((player, world, hand) -> {
            System.out.println("Entries: " + MY_DP_OBJECTS.apply(world.registryAccess()).stream().toList());
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        ClientPickBlockApplyCallback.EVENT.register((player, result, stack) -> {
            System.out.println("Entries: " + MY_DP_OBJECTS.apply(player.level.registryAccess()).stream().toList());
            return stack;
        });
    }
}
