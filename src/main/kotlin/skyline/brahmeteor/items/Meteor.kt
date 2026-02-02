package skyline.brahmeteor.items

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import skyline.brahmeteor.eventHandlers.WorldMeteorSpawner

class Meteor(block: Block, properties: Properties) : BlockItem(block, properties) {

    override fun use(level: Level, player: Player, interactionHand: InteractionHand): InteractionResult {

        if (!level.isClientSide) {
            WorldMeteorSpawner.spawnMeteorRandomMeteorAroundPlayer(player, WorldMeteorSpawner.random)
        }

        return InteractionResult.SUCCESS
    }
}