package skyline.brahmeteor.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import skyline.brahmeteor.blocks.MeteorBlock
import skyline.brahmeteor.entities.FallingMeteor
import skyline.brahmeteor.registry.ModBlocks

class FallingMeteorRenderer(val context: EntityRendererProvider.Context) : EntityRenderer<FallingMeteor, FallingBlockRenderState>(context) {

    private val blockDispatcher = context.blockRenderDispatcher

    init {
        this.shadowRadius = 0.5f
    }

    override fun createRenderState(): FallingBlockRenderState {
        return FallingBlockRenderState()
    }

    override fun extractRenderState(entity: FallingMeteor, state: FallingBlockRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        val blockPos = BlockPos.containing(entity.x, entity.boundingBox.maxY, entity.z)
        state.movingBlockRenderState.randomSeedPos = entity.getStartPos()
        state.movingBlockRenderState.blockPos = blockPos
        val size = entity.getMeteorSize()
        state.movingBlockRenderState.blockState = ModBlocks.METEOR_BLOCK.defaultBlockState().setValue(MeteorBlock.SIZE, size)
        state.movingBlockRenderState.biome = entity.level().getBiome(blockPos)
        state.movingBlockRenderState.level = entity.level()
        
        // Full brightness so the meteor looks like it's glowing/on fire
        state.lightCoords = 15728880  // LightTexture.FULL_BRIGHT
    }

    override fun submit(
        state: FallingBlockRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        val blockState = state.movingBlockRenderState.blockState
        val meteorSize = blockState.getValue(MeteorBlock.SIZE).toFloat()

        poseStack.pushPose()

        // Center the block at the entity origin (same as vanilla falling block)
        poseStack.translate(-0.5, 0.0, -0.5)

        // Spin + scale around the block center
        poseStack.translate(0.5, 0.5, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 5f))
        poseStack.mulPose(Axis.XP.rotationDegrees(state.ageInTicks * 3f))
        poseStack.scale(meteorSize, meteorSize, meteorSize)
        poseStack.translate(-0.5, -0.5, -0.5)

        // Render explicitly via BlockStateModel + RenderType so it works even though the placed block uses RenderShape.INVISIBLE.
        val model = blockDispatcher.getBlockModel(blockState)
        val renderType = ItemBlockRenderTypes.getMovingBlockRenderType(blockState)
        submitNodeCollector.submitBlockModel(
            poseStack,
            renderType,
            model,
            1.0f,
            1.0f,
            1.0f,
            state.lightCoords,
            OverlayTexture.NO_OVERLAY,
            0
        )

        poseStack.popPose()

        super.submit(state, poseStack, submitNodeCollector, cameraRenderState)
    }
}
