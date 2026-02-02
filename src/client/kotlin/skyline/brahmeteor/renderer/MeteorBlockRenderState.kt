package skyline.brahmeteor.renderer

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState

class MeteorBlockRenderState : BlockEntityRenderState() {

    private var size = 1

    fun getSize(): Int {
        return size
    }

    fun setSize(size: Int) {
        this.size = size
    }
}
