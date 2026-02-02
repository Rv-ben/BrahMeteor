package skyline.brahmeteor

import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.entity.EntityRenderers
import skyline.brahmeteor.entities.MeteorBlockEntity
import skyline.brahmeteor.registry.ModEntities
import skyline.brahmeteor.renderer.FallingMeteorRenderer
import skyline.brahmeteor.renderer.MeteorBlockRenderState
import skyline.brahmeteor.renderer.MeteorBlockRenderer

object BrahmeteorClient : ClientModInitializer {
	override fun onInitializeClient() {
		// Register the falling meteor entity renderer using vanilla API
		EntityRenderers.register(ModEntities.FALLING_METEOR, ::FallingMeteorRenderer)
		BlockEntityRenderers.register<MeteorBlockEntity, MeteorBlockRenderState>(
			ModEntities.METEOR_BLOCK_ENTITY,
			{
				p -> MeteorBlockRenderer(p)
			}
		)
	}
}