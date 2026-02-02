package skyline.brahmeteor.entities

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueOutput
import skyline.brahmeteor.blocks.MeteorBlock
import skyline.brahmeteor.registry.ModEntities.METEOR_BLOCK_ENTITY


class MeteorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(METEOR_BLOCK_ENTITY, pos, state) {

    val size: Int
        get() = blockState.getValue(MeteorBlock.SIZE)

    override fun saveAdditional(valueOutput: ValueOutput) {
        super.saveAdditional(valueOutput)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        return saveWithoutMetadata(registryLookup)
    }
}