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
import net.minecraft.util.StringRepresentable
import skyline.brahmeteor.entities.TurretBlockEntity
import skyline.brahmeteor.registry.ModEntities

class TurretBlock(settings: BlockBehaviour.Properties) : BaseEntityBlock(settings) {

    companion object {
        val FACING = BlockStateProperties.HORIZONTAL_FACING
        val PART: EnumProperty<TurretPart> = EnumProperty.create("part", TurretPart::class.java)
    }

    enum class TurretPart(private val serialized: String) : StringRepresentable {
        CONTROLLER("controller"),
        X1("x1"),
        Z1("z1"),
        X1Z1("x1z1");

        override fun getSerializedName(): String = serialized
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, TurretPart.CONTROLLER))
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = simpleCodec(::TurretBlock)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

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
            .setValue(PART, TurretPart.CONTROLLER)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, itemStack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack)
        if (level.isClientSide) return
        if (state.getValue(PART) != TurretPart.CONTROLLER) return

        val facing = state.getValue(FACING)
        val offsets = structureOffsets(facing)

        // Place the 3 non-controller parts. They must NOT recurse into placing the structure.
        for ((part, offset) in offsets.entries) {
            if (part == TurretPart.CONTROLLER) continue
            val targetPos = pos.offset(offset)
            level.setBlock(
                targetPos,
                defaultBlockState().setValue(FACING, facing).setValue(PART, part),
                Block.UPDATE_ALL
            )
        }
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
                val offsets = structureOffsets(facing)

                // Remove the 3 other parts unconditionally.
                for ((_, offset) in offsets.entries) {
                    val p = controllerPos.offset(offset)
                    if (p == controllerPos) continue
                    if (level.getBlockState(p).block == this) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
                    }
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
        return if (blockState.getValue(PART) == TurretPart.CONTROLLER) {
            TurretBlockEntity(blockPos, blockState)
        } else {
            null
        }
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide) return null
        if (state.getValue(PART) != TurretPart.CONTROLLER) return null
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
        // Controller is the origin corner.
        // X1 is to the "right" of facing, Z1 is "forward" (facing), X1Z1 is right+forward.
        val right = facing.getClockWise()
        val forward = facing
        return linkedMapOf(
            TurretPart.CONTROLLER to BlockPos.ZERO,
            TurretPart.X1 to BlockPos(right.getStepX(), 0, right.getStepZ()),
            TurretPart.Z1 to BlockPos(forward.getStepX(), 0, forward.getStepZ()),
            TurretPart.X1Z1 to BlockPos(right.getStepX() + forward.getStepX(), 0, right.getStepZ() + forward.getStepZ())
        )
    }

    private fun getControllerPos(level: Level, pos: BlockPos, state: BlockState): BlockPos? {
        // If controller already removed, bail.
        if (state.block != this) return null
        val facing = state.getValue(FACING)
        val right = facing.getClockWise()
        val forward = facing

        val offsetToController = when (state.getValue(PART)) {
            TurretPart.CONTROLLER -> BlockPos.ZERO
            TurretPart.X1 -> BlockPos(-right.getStepX(), 0, -right.getStepZ())
            TurretPart.Z1 -> BlockPos(-forward.getStepX(), 0, -forward.getStepZ())
            TurretPart.X1Z1 -> BlockPos(-(right.getStepX() + forward.getStepX()), 0, -(right.getStepZ() + forward.getStepZ()))
        }
        val controllerPos = pos.offset(offsetToController)
        val controllerState = level.getBlockState(controllerPos)
        return if (controllerState.block == this && controllerState.getValue(PART) == TurretPart.CONTROLLER) controllerPos else null
    }
}

