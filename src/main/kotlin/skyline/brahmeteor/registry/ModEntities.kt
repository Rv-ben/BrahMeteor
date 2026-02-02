package skyline.brahmeteor.registry

import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.block.entity.BlockEntityType
import skyline.brahmeteor.Constants
import skyline.brahmeteor.entities.FallingMeteor
import skyline.brahmeteor.entities.MeteorBlockEntity
import skyline.brahmeteor.entities.TurretAmmoProjectile
import skyline.brahmeteor.entities.TurretBlockEntity

object ModEntities {
    lateinit var FALLING_METEOR: EntityType<FallingMeteor>
    lateinit var TURRET_AMMO_PROJECTILE: EntityType<TurretAmmoProjectile>

    lateinit var METEOR_BLOCK_ENTITY: BlockEntityType<MeteorBlockEntity>
    lateinit var TURRET_BLOCK_ENTITY: BlockEntityType<TurretBlockEntity>

    fun initialize() {

        val fallingMeteorKey = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "falling_meteor"))

        FALLING_METEOR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "falling_meteor"),
            EntityType.Builder.of(::FallingMeteor, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(1)  // Update every tick for smooth movement
                .build(fallingMeteorKey)
        )

        val turretAmmoKey = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "turret_ammo"))
        TURRET_AMMO_PROJECTILE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "turret_ammo"),
            EntityType.Builder.of(::TurretAmmoProjectile, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .clientTrackingRange(128)
                .updateInterval(1)
                .build(turretAmmoKey)
        )

        METEOR_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "meteor"),
            FabricBlockEntityTypeBuilder.create(::MeteorBlockEntity, ModBlocks.METEOR_BLOCK).build()
        )

        TURRET_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "turret"),
            FabricBlockEntityTypeBuilder.create(::TurretBlockEntity, ModBlocks.TURRET).build()
        )
    }
}
