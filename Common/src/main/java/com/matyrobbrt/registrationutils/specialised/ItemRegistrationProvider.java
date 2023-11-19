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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A specialised {@link RegistrationProvider registration provider} for items.
 */
public interface ItemRegistrationProvider extends RegistrationProvider<Item> {
    /**
     * {@return a {@link ItemRegistrationProvider} for the given {@code modId}}
     */
    static ItemRegistrationProvider get(String modId) {
        return Factory.INSTANCE.item(modId);
    }

    /**
     * Registers an item.
     *
     * @param name the name of the item
     * @param item a supplier of the item to register
     * @param <I>  the type of the item
     * @return a wrapper containing the lazy registered item. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    <I extends Item> ItemRegistryObject<I> register(String name, Supplier<? extends I> item);

    /**
     * Registers a simple item.
     *
     * @param name the name of the item
     * @return a wrapper containing the lazy registered item. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    default ItemRegistryObject<Item> register(String name) {
        return register(name, new Item.Properties(), Item::new);
    }

    /**
     * Registers an item.
     *
     * @param name       the name of the item
     * @param func       a factory for the new item. The factory should not cache the created item.
     * @param properties the properties for the created item.
     * @param <I>        the type of the item
     * @return a wrapper containing the lazy registered item. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    default <I extends Item> ItemRegistryObject<I> register(String name, Item.Properties properties, Function<Item.Properties, ? extends I> func) {
        return register(name, () -> func.apply(properties));
    }

    /**
     * Registers a simple block item.
     *
     * @param block the block to register this block item for
     * @param <B>   the type of the block
     * @return a wrapper containing the lazy registered item. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    default <B extends Block> ItemRegistryObject<BlockItem> registerBlockItem(RegistryObject<Block, B> block) {
        return registerBlockItem(block, new Item.Properties());
    }

    /**
     * Registers a block item.
     *
     * @param block      the block to register this block item for
     * @param properties the properties for the created item.
     * @param <B>        the type of the block
     * @return a wrapper containing the lazy registered item. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    default <B extends Block> ItemRegistryObject<BlockItem> registerBlockItem(RegistryObject<Block, B> block, Item.Properties properties) {
        return registerBlockItem(block, properties, BlockItem::new);
    }

    /**
     * Registers a block item.
     *
     * @param block      the block to register this block item for
     * @param func       a factory for the new item. The factory should not cache the created item.
     * @param properties the properties for the created item.
     * @param <B>        the type of the block
     * @param <I>        the type of the item
     * @return a wrapper containing the lazy registered item. <strong>Calling {@link RegistryObject#get() get} too early
     * on the wrapper might result in crashes!</strong>
     */
    default <B extends Block, I extends BlockItem> ItemRegistryObject<I> registerBlockItem(RegistryObject<Block, B> block, Item.Properties properties, BiFunction<B, Item.Properties, ? extends I> func) {
        return register(block.getId().getPath(), () -> func.apply(block.get(), properties));
    }
}
