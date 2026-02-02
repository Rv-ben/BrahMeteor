package skyline.brahmeteor.eventHandlers

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import skyline.brahmeteor.blocks.MeteorBlock
import skyline.brahmeteor.entities.FallingMeteor
import skyline.brahmeteor.registry.ModBlocks.METEOR_BLOCK

class WorldMeteorSpawner {

    companion object {
        const val tickMod = 100
        val random : RandomSource = RandomSource.create()

        fun Spawn(server: MinecraftServer) : InteractionResult {

            if (server.tickCount % tickMod != 0){
                return InteractionResult.SUCCESS
            }

            server.playerList.players.forEach {
                player -> spawnMeteorRandomMeteorAroundPlayer(player, random)
            }

            return InteractionResult.SUCCESS
        }

        fun spawnMeteorRandomMeteorAroundPlayer(player: Player, randomSource: RandomSource) {

            val randomPosAroundPlayer = randomPositionFromBlock(player.blockPosition(), randomSource)

            val state = createRandomMeteor(randomSource)
            val fallingMeteor = FallingMeteor(player.level(), randomPosAroundPlayer, state)

            player.level().addFreshEntity(fallingMeteor)
        }

        fun randomPositionFromBlock(pos: BlockPos, randomSource: RandomSource) : BlockPos {
            val randomPosAroundBlockPos = pos.offset(
                randomSource.nextInt(-100, 100),
                randomSource.nextInt(50, 100),
                randomSource.nextInt(-100, 100)
            )

            return randomPosAroundBlockPos
        }

        fun createRandomMeteor(randomSource: RandomSource) : BlockState {
            val size = randomSource.nextInt(1, 6) // 1..5 inclusive
            val state = METEOR_BLOCK.defaultBlockState().setValue(MeteorBlock.SIZE, size)

            return state
        }
    }
}