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

package com.matyrobbrt.registrationutils.specialised;

import com.matyrobbrt.registrationutils.RegistrationProvider;
import com.matyrobbrt.registrationutils.RegistryObject;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A specialised {@link RegistrationProvider registration provider} for blocks.
 */
public interface BlockRegistrationProvider extends RegistrationProvider<Block> {

    /**
     * {@return a {@link BlockRegistrationProvider} for the given {@code modId}}
     */
    static BlockRegistrationProvider get(String modId) {
        return Factory.INSTANCE.block(modId);
    }

    /**
     * Registers a block.
     *
     * @param name  the name of the block
     * @param block a supplier of the block to register
     * @param <B>   the type of the block
     * @return a wrapper containing the lazy registered block. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    <B extends Block> BlockRegistryObject<B> register(String name, Supplier<? extends B> block);

    /**
     * Registers a simple block.
     *
     * @param name       the name of the block
     * @param properties the properties of the block
     * @param <B>        the type of the block
     * @return a wrapper containing the lazy registered block. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    default <B extends Block> BlockRegistryObject<B> register(String name, BlockBehaviour.Properties properties) {
        return register(name, properties);
    }

    /**
     * Registers a block.
     *
     * @param name       the name of the block
     * @param func       a factory for the new block. The factory should not cache the created block.
     * @param properties the properties of the block
     * @param <B>        the type of the block
     * @return a wrapper containing the lazy registered block. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    default <B extends Block> BlockRegistryObject<B> register(String name, BlockBehaviour.Properties properties, Function<BlockBehaviour.Properties, ? extends B> func) {
        return register(name, () -> func.apply(properties));
    }
}
