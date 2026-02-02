package skyline.brahmeteor.blocks

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import skyline.brahmeteor.entities.MeteorBlockEntity

class MeteorBlock(settings: BlockBehaviour.Properties) : BaseEntityBlock(settings) {

    companion object {
        // Keep this small to avoid massive shape caches.
        // Size is a SCALE factor applied around block center (8,8,8 in voxel units).
        val SIZE: IntegerProperty = IntegerProperty.create("size", 1, 5)
    }

    init {
        // Ensure manually placed blocks default to size=1.
        registerDefaultState(stateDefinition.any().setValue(SIZE, 1))
    }

    // Block entity renderer handles visuals (scaled).
    override fun getRenderShape(state: BlockState): RenderShape {
        // 1.21.11 only has MODEL/INVISIBLE. We want to suppress normal block rendering and
        // rely on the block entity renderer.
        return RenderShape.INVISIBLE
    }

    override fun getShape(
        state: BlockState,
        world: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return getVoxelShape(state.getValue(SIZE))
    }

    fun getVoxelShape(size: Int) : VoxelShape {
        val s = size.toDouble()
        fun scaledBox(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): VoxelShape {
            // Match renderer: translate(0.5)->scale(size)->translate(-0.5)
            // In voxel units, center is (8,8,8).
            fun sc(v: Double): Double = 8.0 + (v - 8.0) * s
            return box(sc(x1), sc(y1), sc(z1), sc(x2), sc(y2), sc(z2))
        }

        return Shapes.or(
            // Core vertical column
            scaledBox(4.0, 2.0, 4.0, 12.0, 14.0, 12.0),
            // Horizontal expansion (X axis)
            scaledBox(2.0, 4.0, 4.0, 14.0, 12.0, 12.0),
            // Horizontal expansion (Z axis)
            scaledBox(4.0, 4.0, 2.0, 12.0, 12.0, 14.0)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState>) {
        builder.add(SIZE)
    }

    override fun codec(): MapCodec<out BaseEntityBlock> {
        return  simpleCodec(::MeteorBlock )
    }

    override fun newBlockEntity(
        blockPos: BlockPos,
        blockState: BlockState
    ): BlockEntity {
        return MeteorBlockEntity(blockPos, blockState)
    }
}