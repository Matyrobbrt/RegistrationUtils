package com.matyrobbrt.regtest;

import com.matyrobbrt.regtest.registries.DatapackRegistry;
import com.matyrobbrt.regtest.registries.RegistryFeatureType;
import com.matyrobbrt.regtest.specialised.BlockRegistrationProvider;
import com.matyrobbrt.regtest.specialised.ItemRegistrationProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.client.player.ClientPickBlockApplyCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Supplier;

public class RegTest implements ModInitializer {
    private static final RegistrationProvider<MyObject> MY_OBJECTS = RegistrationProvider.get(new ResourceLocation("regtest:myregistry"), "regtest");

    public static final Supplier<Registry<MyObject>> MY_OBJECT_REGISTRY = MY_OBJECTS.registryBuilder()
            .withFeature(RegistryFeatureType.SYNCED)
            .withDefaultValue("default", () -> new MyObject(12))
            .build();

    public static final RegistryObject<MyObject, MyObject> SOME_OBJECT = MY_OBJECTS.register("yes", () -> new MyObject(14));

    public static final ResourceKey<Registry<MyObject>> MY_DP_OBJECTS_KEY = ResourceKey.createRegistryKey(new ResourceLocation("regtest", "test_dp"));
    public static final DatapackRegistry<MyObject> MY_DP_OBJECTS = DatapackRegistry.<MyObject>builder(new ResourceLocation("regtest", "test_dp"))
            .withElementCodec(MyObject.CODEC)
            .withNetworkCodec(MyObject.CODEC)
            .withBootstrap(bootstapContext -> bootstapContext
                    .register(ResourceKey.create(MY_DP_OBJECTS_KEY, new ResourceLocation("yes")), new MyObject(14)))
            .build();

    public record MyObject(int someInt) {
        public static final Codec<MyObject> CODEC = RecordCodecBuilder.create(in -> in.group(
                Codec.INT.fieldOf("someInt").forGetter(MyObject::someInt)
        ).apply(in, MyObject::new));
    }

    @Override
    public void onInitialize() {
        MY_OBJECT_REGISTRY.get().holders().forEach(holder -> System.out.println("Key: " + holder.key().location() + ", value: " + holder.value()));

        UseItemCallback.EVENT.register((player, world, hand) -> {
            System.out.println("Entries: " + MY_DP_OBJECTS.get(world.registryAccess()).holders().toList());
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        ClientPickBlockApplyCallback.EVENT.register((player, result, stack) -> {
            System.out.println("Entries: " + MY_DP_OBJECTS.get(player.level().registryAccess()).holders().toList());
            return stack;
        });

        final var abcBlock = BlockRegistrationProvider.get("testmod").register("abc", BlockBehaviour.Properties.copy(Blocks.SAND), Block::new);
        final var items = ItemRegistrationProvider.get("testmod");
        final var myBi = items.registerBlockItem(abcBlock, new Item.Properties()
                .rarity(Rarity.EPIC));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> entries.accept(myBi));
    }
}
