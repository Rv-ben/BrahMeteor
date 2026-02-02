package skyline.brahmeteor

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.slf4j.LoggerFactory
import skyline.brahmeteor.eventHandlers.WorldMeteorSpawner
import skyline.brahmeteor.registry.ModBlocks
import skyline.brahmeteor.registry.ModEntities
import skyline.brahmeteor.registry.ModItems


object Brahmeteor : ModInitializer {
    private val logger = LoggerFactory.getLogger("brahmeteor")

	override fun onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		logger.info("Hello Fabric world!")

		// Order matters: Blocks first, then Entities (block entities need blocks)
		ModBlocks.initialize()
		ModEntities.initialize()
		ModItems.initialize()

		ServerTickEvents.START_SERVER_TICK.register { server -> WorldMeteorSpawner.Spawn(server) }
	}
}