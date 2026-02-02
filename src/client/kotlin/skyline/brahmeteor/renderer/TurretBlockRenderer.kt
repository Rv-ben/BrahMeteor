package skyline.brahmeteor.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import skyline.brahmeteor.blocks.TurretBlock
import skyline.brahmeteor.entities.TurretBlockEntity
import skyline.brahmeteor.registry.ModBlocks

class TurretBlockRenderer(private val ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<TurretBlockEntity, TurretBlockRenderState> {

    override fun createRenderState(): TurretBlockRenderState = TurretBlockRenderState()

    override fun extractRenderState(
        blockEntity: TurretBlockEntity,
        state: TurretBlockRenderState,
        tickProgress: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, tickProgress, cameraPos, crumblingOverlay)
        state.aimYawRad = blockEntity.getAimYaw()
        state.aimPitchRad = blockEntity.getAimPitch()
        state.recoil01 = blockEntity.getRecoil01()
        state.facing = blockEntity.blockState.getValue(TurretBlock.FACING)
    }

    override fun submit(
        state: TurretBlockRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        // Render an animated turret head + barrel on top of the 1x2 turret.

        poseStack.pushPose()

        // Controller is the lower block; render gun on the upper block.
        poseStack.translate(0.0, 1.0, 0.0)

        // Rotate head around its center to track target.
        poseStack.translate(0.5, 0.5, 0.5)
        poseStack.mulPose(Axis.YP.rotation(state.aimYawRad))
        poseStack.translate(-0.5, -0.5, -0.5)

        val headState = ModBlocks.TURRET_HEAD.defaultBlockState()
        val headModel = ctx.blockRenderDispatcher.getBlockModel(headState)
        val headType = ItemBlockRenderTypes.getRenderType(headState)
        submitNodeCollector.submitBlockModel(
            poseStack,
            headType,
            headModel,
            1.0f,
            1.0f,
            1.0f,
            state.lightCoords,
            OverlayTexture.NO_OVERLAY,
            0
        )

        // Barrel: render a stretched end rod with pitch + recoil.
        poseStack.pushPose()
        poseStack.translate(0.5, 0.55, 0.5)
        poseStack.mulPose(Axis.XP.rotation(state.aimPitchRad))
        poseStack.translate(0.0, 0.0, 0.35 - (state.recoil01 * 0.15))
        poseStack.scale(0.35f, 0.35f, 1.8f)
        poseStack.translate(-0.5, -0.5, -0.5)

        val barrelState = ModBlocks.TURRET_BARREL.defaultBlockState()
        val barrelModel = ctx.blockRenderDispatcher.getBlockModel(barrelState)
        val barrelType = ItemBlockRenderTypes.getRenderType(barrelState)
        submitNodeCollector.submitBlockModel(
            poseStack,
            barrelType,
            barrelModel,
            1.0f,
            1.0f,
            1.0f,
            state.lightCoords,
            OverlayTexture.NO_OVERLAY,
            0
        )
        poseStack.popPose()

        poseStack.popPose()
    }
}

