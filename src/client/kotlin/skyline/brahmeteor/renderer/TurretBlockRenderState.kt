package skyline.brahmeteor.renderer

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.core.Direction

class TurretBlockRenderState : BlockEntityRenderState() {
    var aimYawRad: Float = 0.0f
    var aimPitchRad: Float = 0.0f
    var recoil01: Float = 0.0f
    var facing: Direction = Direction.NORTH
}

