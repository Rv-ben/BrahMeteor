package skyline.brahmeteor.registry

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import skyline.brahmeteor.Constants
import skyline.brahmeteor.blocks.MeteorBlock
import skyline.brahmeteor.blocks.TurretBlock
import skyline.brahmeteor.items.Meteor
import java.util.function.Function


object ModBlocks {
    lateinit var METEOR_BLOCK: Block
    lateinit var TURRET: Block

    fun initialize() {
        METEOR_BLOCK = register(
            "meteor",
            {p -> MeteorBlock(p)},
            BlockBehaviour.Properties.of().lightLevel { 15 },
            shouldRegisterItem = true,
            blockItemFactory = { block, props -> Meteor(block, props) }
        )

        TURRET = register(
            "turret",
            { p -> TurretBlock(p) },
            BlockBehaviour.Properties.of().strength(3.5f),
            shouldRegisterItem = true,
            blockItemFactory = { block, props -> BlockItem(block, props) }
        )

        // Add to creative tabst
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register { entries ->
            entries.accept(METEOR_BLOCK)
            entries.accept(TURRET)
        }
    }

    private fun register(
        name: String,
        blockFactory: Function<BlockBehaviour.Properties, Block>,
        settings: BlockBehaviour.Properties,
        shouldRegisterItem: Boolean,
        blockItemFactory: (Block, Item.Properties) -> Item = { block, props -> BlockItem(block, props) }
    ): Block {
        // Create a registry key for the block
        val blockKey = keyOfBlock(name)
        // Create the block instance
        val block = blockFactory.apply(settings.setId(blockKey))

        // Sometimes, you may not want to register an item for the block.
        // Eg: if it's a technical block like `minecraft:moving_piston` or `minecraft:end_gateway`
        if (shouldRegisterItem) {
            // Items need to be registered with a different type of registry key, but the ID
            // can be the same.
            val itemKey = keyOfItem(name)

            val blockItem = blockItemFactory(block, Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
            Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem)
        }

        return Registry.register<Block, Block>(BuiltInRegistries.BLOCK, blockKey, block)
    }

    private fun keyOfBlock(name: String): ResourceKey<Block> {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name))
    }

    private fun keyOfItem(name: String): ResourceKey<Item> {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name))
    }
}