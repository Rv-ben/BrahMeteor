package skyline.brahmeteor.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.phys.Vec3
import skyline.brahmeteor.entities.MeteorBlockEntity
import skyline.brahmeteor.registry.ModBlocks


class MeteorBlockRenderer(val ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<MeteorBlockEntity, MeteorBlockRenderState> {

    override fun createRenderState(): MeteorBlockRenderState {
        return MeteorBlockRenderState()
    }

    override fun extractRenderState(
        blockEntity: MeteorBlockEntity,
        state: MeteorBlockRenderState,
        tickProgress: Float,
        cameraPos: Vec3,
        crumblingOverlay: CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay)
        state.setSize(blockEntity.size)
    }

    override fun submit(
        state: MeteorBlockRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        poseStack.pushPose()
        
        val size = state.getSize().toFloat()
        
        // Scale the block based on size (scale from center)
        poseStack.translate(0.5, 0.5, 0.5)
        poseStack.scale(size, size, size)
        poseStack.translate(-0.5, -0.5, -0.5)

        // Render using the same chunk RenderType + BlockStateModel path vanilla uses.
        // This avoids relying on any renderer-side effects (your current “white unless meteor is visible” bug).
        val blockState = ModBlocks.METEOR_BLOCK.defaultBlockState()
        val blockModel = ctx.blockRenderDispatcher.getBlockModel(blockState)
        val renderType = ItemBlockRenderTypes.getRenderType(blockState)

        submitNodeCollector.submitBlockModel(
            poseStack,
            renderType,
            blockModel,
            1.0f,
            1.0f,
            1.0f,
            LightTexture.FULL_BLOCK,
            OverlayTexture.NO_OVERLAY,
            0
        )
        
        poseStack.popPose()
    }
}
