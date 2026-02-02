package skyline.brahmeteor

import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.client.renderer.entity.ThrownItemRenderer
import skyline.brahmeteor.entities.MeteorBlockEntity
import skyline.brahmeteor.entities.TurretAmmoProjectile
import skyline.brahmeteor.registry.ModEntities
import skyline.brahmeteor.renderer.FallingMeteorRenderer
import skyline.brahmeteor.renderer.MeteorBlockRenderState
import skyline.brahmeteor.renderer.MeteorBlockRenderer

object BrahmeteorClient : ClientModInitializer {
	override fun onInitializeClient() {
		// Register the falling meteor entity renderer using vanilla API
		EntityRenderers.register(ModEntities.FALLING_METEOR, ::FallingMeteorRenderer)
		EntityRenderers.register(ModEntities.TURRET_AMMO_PROJECTILE) { ctx ->
			ThrownItemRenderer<TurretAmmoProjectile>(ctx)
		}
		BlockEntityRenderers.register<MeteorBlockEntity, MeteorBlockRenderState>(
			ModEntities.METEOR_BLOCK_ENTITY,
			{
				p -> MeteorBlockRenderer(p)
			}
		)
	}
}