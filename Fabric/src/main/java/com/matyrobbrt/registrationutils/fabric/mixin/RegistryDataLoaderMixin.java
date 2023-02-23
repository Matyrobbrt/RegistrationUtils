package com.matyrobbrt.registrationutils.fabric.mixin;

import com.matyrobbrt.registrationutils.fabric.FabricDatapackRegistryHelper;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {
    @Inject(at = @At("HEAD"), method = "registryDirPath", cancellable = true)
    private static void regutils$customRegistry(ResourceLocation resourceLocation, CallbackInfoReturnable<String> cir) {
        if (FabricDatapackRegistryHelper.OWNED_REGISTRIES.contains(resourceLocation)) {
            cir.setReturnValue(resourceLocation.getNamespace() + "/" + resourceLocation.getPath());
        }
    }
}
