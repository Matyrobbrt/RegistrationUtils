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

package com.matyrobbrt.registrationutils.forge;

import com.google.auto.service.AutoService;
import com.matyrobbrt.registrationutils.EntityDataSerializerHelper;
import com.matyrobbrt.registrationutils.RegistrationProvider;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
@ParametersAreNonnullByDefault
@AutoService(EntityDataSerializerHelper.class)
public class ForgeEntityDataSerializerHelper implements EntityDataSerializerHelper {
    private final Map<String, RegistrationProvider<EntityDataSerializer<?>>> byModId = new ConcurrentHashMap<>();

    @Override
    public <T> EntityDataSerializer<T> register(ResourceLocation key, EntityDataSerializer<T> serializer) {
        getProvider(key.getNamespace()).register(key.getPath(), () -> serializer);
        return serializer;
    }

    private RegistrationProvider<EntityDataSerializer<?>> getProvider(String modId) {
        return byModId.computeIfAbsent(modId, theId -> RegistrationProvider.get(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, theId));
    }
}
