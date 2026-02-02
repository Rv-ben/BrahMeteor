package skyline.brahmeteor.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import skyline.brahmeteor.Constants
import java.util.function.Function

object ModItems {

    fun initialize() {
    }

    fun <GenericItem : Item> register(
        name: String,
        itemFactory: Function<Item.Properties?, GenericItem>,
        settings: Item.Properties
    ): GenericItem {
        // Create the item key.
        val itemKey =
            ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name))

        // Create the item instance.
        val item = itemFactory.apply(settings.setId(itemKey))

        // Register the item.
        Registry.register(BuiltInRegistries.ITEM, itemKey, item)

        return item
    }
}