package com.matyrobbrt.regtest;

import com.matyrobbrt.regtest.registries.RegistryFeatureType;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public class RegTest implements ModInitializer {
    private static final RegistrationProvider<MyObject> MY_OBJECTS = RegistrationProvider.get(new ResourceLocation("regtest:myregistry"), "regtest");

    public static final Supplier<Registry<MyObject>> MY_OBJECT_REGISTRY = MY_OBJECTS.registryBuilder()
            .withFeature(RegistryFeatureType.SYNCED)
            .withDefaultValue("default", () -> new MyObject(12))
            .build();

    public static final RegistryObject<MyObject> SOME_OBJECT = MY_OBJECTS.register("yes", () -> new MyObject(14));

    public record MyObject(int someInt) {}

    @Override
    public void onInitialize() {
        MY_OBJECT_REGISTRY.get().holders().forEach(holder -> System.out.println("Key: " + holder.key().location() + ", value: " + holder.value()));
        System.exit(0);
    }
}
