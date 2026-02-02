package skyline.brahmeteor.blocks

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.util.StringRepresentable
import skyline.brahmeteor.entities.TurretBlockEntity
import skyline.brahmeteor.registry.ModEntities

class TurretBlock(settings: BlockBehaviour.Properties) : BaseEntityBlock(settings) {

    companion object {
        val FACING = BlockStateProperties.HORIZONTAL_FACING
        val PART: EnumProperty<TurretPart> = EnumProperty.create("part", TurretPart::class.java)
    }

    /** 1x2 vertical turret parts. Controller is the lower block. */
    enum class TurretPart(private val serialized: String) : StringRepresentable {
        LOWER("lower"),
        UPPER("upper");

        override fun getSerializedName(): String = serialized

        fun isController(): Boolean = this == LOWER
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, TurretPart.LOWER))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = simpleCodec(::TurretBlock)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getShape(state: BlockState, level: net.minecraft.world.level.BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return if (state.getValue(PART) == TurretPart.UPPER) {
            // Smaller selection box so you don't see a big cube outline.
            box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0)
        } else {
            Shapes.block()
        }
    }

    override fun getCollisionShape(state: BlockState, level: net.minecraft.world.level.BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return getShape(state, level, pos, context)
    }

    override fun getOcclusionShape(state: BlockState): VoxelShape {
        // Critical: make the UPPER part not occlude, so the LOWER top face renders.
        return if (state.getValue(PART) == TurretPart.UPPER) Shapes.empty() else Shapes.block()
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, PART)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    }

    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val facing = context.horizontalDirection.opposite
        val origin = context.clickedPos
        val level = context.level

        val offsets = structureOffsets(facing)
        if (!canPlaceStructure(level, context, origin, offsets)) return null

        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(PART, TurretPart.LOWER)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, itemStack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack)
        if (level.isClientSide) return
        if (!state.getValue(PART).isController()) return

        val facing = state.getValue(FACING)
        val offsets = structureOffsets(facing)

        // Place the upper block.
        val upperPos = pos.above()
        level.setBlock(
            upperPos,
            defaultBlockState().setValue(FACING, facing).setValue(PART, TurretPart.UPPER),
            Block.UPDATE_ALL
        )
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide) {
            val controllerPos = getControllerPos(level, pos, state)
            if (controllerPos != null) {
                val breakingController = controllerPos == pos

                // If the player broke a non-controller part, ensure the structure drops exactly one item.
                // If the player broke the controller itself, let vanilla destruction + loot table handle the drop.
                if (!player.isCreative && !breakingController) {
                    popResource(level, controllerPos, ItemStack(this))
                }

                val facing = state.getValue(FACING)
                // Remove the other part (upper).
                val upperPos = controllerPos.above()
                if (level.getBlockState(upperPos).block == this) {
                    level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                }

                // If the broken block was not the controller, remove the controller too.
                if (!breakingController && level.getBlockState(controllerPos).block == this) {
                    level.setBlock(controllerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                }
            }
        }

        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun newBlockEntity(blockPos: BlockPos, blockState: BlockState): BlockEntity? {
        return if (blockState.getValue(PART).isController()) {
            TurretBlockEntity(blockPos, blockState)
        } else {
            null
        }
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide) return null
        if (!state.getValue(PART).isController()) return null
        @Suppress("UNCHECKED_CAST")
        return if (type == ModEntities.TURRET_BLOCK_ENTITY) {
            val ticker: BlockEntityTicker<TurretBlockEntity> = BlockEntityTicker { lvl, pos, st, be ->
                TurretBlockEntity.serverTick(lvl, pos, st, be)
            }
            ticker as BlockEntityTicker<T>
        } else {
            null
        }
    }

    private fun canPlaceStructure(
        level: LevelAccessor,
        context: BlockPlaceContext,
        origin: BlockPos,
        offsets: Map<TurretPart, BlockPos>
    ): Boolean {
        for ((_, offset) in offsets) {
            val p = origin.offset(offset)
            val stateAt = level.getBlockState(p)
            if (!stateAt.canBeReplaced(context)) return false
        }
        return true
    }

    private fun structureOffsets(facing: Direction): Map<TurretPart, BlockPos> {
        return linkedMapOf(
            TurretPart.LOWER to BlockPos.ZERO,
            TurretPart.UPPER to BlockPos(0, 1, 0)
        )
    }

    private fun getControllerPos(level: Level, pos: BlockPos, state: BlockState): BlockPos? {
        // If controller already removed, bail.
        if (state.block != this) return null
        val part = state.getValue(PART)
        val controllerPos = if (part == TurretPart.LOWER) pos else pos.below()
        val controllerState = level.getBlockState(controllerPos)
        return if (controllerState.block == this && controllerState.getValue(PART).isController()) controllerPos else null
    }
}

