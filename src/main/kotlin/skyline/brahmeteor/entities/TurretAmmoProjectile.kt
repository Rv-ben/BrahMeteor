package skyline.brahmeteor.entities

import net.minecraft.world.entity.EntityType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

class TurretAmmoProjectile : ThrowableItemProjectile {

    companion object {
        // Prevent missed shots from lingering forever.
        private const val MAX_LIFETIME_TICKS = 100 // ~5 seconds
    }

    // Required constructor for entity registration.
    constructor(entityType: EntityType<out TurretAmmoProjectile>, level: Level) : super(entityType, level)

    // Convenience constructor used by the turret.
    constructor(level: Level, spawnPos: Vec3, ammoStack: ItemStack) : this(skyline.brahmeteor.registry.ModEntities.TURRET_AMMO_PROJECTILE, level) {
        setPos(spawnPos.x, spawnPos.y, spawnPos.z)
        setItem(ammoStack)
    }

    override fun getDefaultItem(): Item {
        // Renderer fallback if the entity has no item set.
        return Items.STONE
    }

    override fun getDefaultGravity(): Double {
        // Make shots effectively hitscan-like over short distances (no arc drop).
        return 0.0
    }

    override fun tick() {
        super.tick()

        // Despawn after a while to avoid buildup.
        if (!level().isClientSide && tickCount >= MAX_LIFETIME_TICKS) {
            discard()
            return
        }

        // Add a simple tracer so shots are visible.
        val lvl = level()
        if (lvl is ServerLevel) {
            lvl.sendParticles(ParticleTypes.CRIT, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    override fun onHitEntity(entityHitResult: EntityHitResult) {
        super.onHitEntity(entityHitResult)

        val hit = entityHitResult.entity
        if (!level().isClientSide && hit is FallingMeteor) {
            hit.discard()
            discard()
        }
    }
}

