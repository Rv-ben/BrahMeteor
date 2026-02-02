package skyline.brahmeteor.entities

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import skyline.brahmeteor.blocks.TurretBlock
import skyline.brahmeteor.math.FallingMeteorPredictor
import skyline.brahmeteor.math.InterceptSolver
import skyline.brahmeteor.math.TrajectoryInterceptSolver
import skyline.brahmeteor.registry.ModEntities

class TurretBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModEntities.TURRET_BLOCK_ENTITY, pos, state), WorldlyContainer {

    companion object {
        private const val SLOT_AMMO = 0
        private const val CONTAINER_SIZE = 1

        private const val SCAN_EVERY_TICKS = 5
        private const val FIRE_COOLDOWN_TICKS = 10
        private const val RANGE_BLOCKS = 128.0
        private const val PROJECTILE_SPEED = 12.0f
        private const val PROJECTILE_INACCURACY = 0.0f
        private const val MAX_PREDICT_TICKS = 80

        fun serverTick(level: Level, pos: BlockPos, state: BlockState, be: TurretBlockEntity) {
            if (level.isClientSide) return

            if (be.cooldownTicks > 0) {
                be.cooldownTicks--
            }

            if (be.cooldownTicks > 0) return
            if (level.gameTime % SCAN_EVERY_TICKS.toLong() != 0L) return

            val ammo = be.items[SLOT_AMMO]
            if (ammo.isEmpty) return

            val center = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            val box = AABB(pos).inflate(RANGE_BLOCKS)

            val meteors = level.getEntitiesOfClass(FallingMeteor::class.java, box)
            if (meteors.isEmpty()) return

            val target = meteors.minByOrNull { it.distanceToSqr(center.x, center.y, center.z) } ?: return

            // Consume ammo (1 block).
            val ammoItem = ammo.item
            ammo.shrink(1)
            be.setChanged()

            val facing = state.getValue(TurretBlock.FACING)
            val muzzle = center.add(
                facing.getStepX() * 0.7,
                0.7,
                facing.getStepZ() * 0.7
            )

            val projectile = TurretAmmoProjectile(level, muzzle, ItemStack(ammoItem))
            projectile.setOwner(null)

            val shooterPos = InterceptSolver.Vec3d(muzzle.x, muzzle.y, muzzle.z)

            val targetPosNow = InterceptSolver.Vec3d(target.x, target.y + target.bbHeight * 0.5, target.z)
            val targetVelNow = target.getKinematicsCurrentVelocity()
            val predictorState = FallingMeteorPredictor.State(
                pos = targetPosNow,
                vel = InterceptSolver.Vec3d(targetVelNow.x, targetVelNow.y, targetVelNow.z),
                timeTicks = target.getKinematicsTimeTicks(),
                accelFactor = target.getKinematicsAccelFactor()
            )
            val samples = FallingMeteorPredictor.predictPositions(predictorState, MAX_PREDICT_TICKS)
            val aim = TrajectoryInterceptSolver.solve(
                shooterPos = shooterPos,
                positionsPerTick = samples,
                projectileSpeed = PROJECTILE_SPEED.toDouble(),
                maxTimeTicks = MAX_PREDICT_TICKS
            )

            val aimPoint = aim?.aimPoint ?: targetPosNow
            val dx = aimPoint.x - shooterPos.x
            val dy = aimPoint.y - shooterPos.y
            val dz = aimPoint.z - shooterPos.z
            projectile.shoot(dx, dy, dz, PROJECTILE_SPEED, PROJECTILE_INACCURACY)

            level.addFreshEntity(projectile)

            // Visual feedback: muzzle flash + sound (helps even if projectile hits instantly).
            if (level is ServerLevel) {
                level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.05, 0.05, 0.05, 0.01)
                level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.01)
            }
            level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.8f, 1.2f)

            be.cooldownTicks = FIRE_COOLDOWN_TICKS
        }
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)
    private var cooldownTicks: Int = 0

    // --- Container / WorldlyContainer (hopper integration) ---

    override fun getContainerSize(): Int = CONTAINER_SIZE

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getItem(slot: Int): ItemStack = items[slot]

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        if (slot != SLOT_AMMO || amount <= 0) return ItemStack.EMPTY
        val stack = items[slot]
        if (stack.isEmpty) return ItemStack.EMPTY

        val taken = stack.split(amount)
        if (!taken.isEmpty) setChanged()
        return taken
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        if (slot != SLOT_AMMO) return ItemStack.EMPTY
        val stack = items[slot]
        items[slot] = ItemStack.EMPTY
        return stack
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot != SLOT_AMMO) return
        items[slot] = stack
        if (stack.count > maxStackSize) stack.count = maxStackSize
        setChanged()
    }

    override fun stillValid(player: Player): Boolean {
        if (level == null) return false
        val d2 = player.distanceToSqr(
            worldPosition.x + 0.5,
            worldPosition.y + 0.5,
            worldPosition.z + 0.5
        )
        return d2 <= 64.0
    }

    override fun clearContent() {
        items[SLOT_AMMO] = ItemStack.EMPTY
        setChanged()
    }

    override fun getSlotsForFace(side: Direction): IntArray = intArrayOf(SLOT_AMMO)

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, dir: Direction?): Boolean {
        if (slot != SLOT_AMMO) return false
        return stack.item is BlockItem
    }

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, dir: Direction): Boolean {
        // Allow extracting from below (optional), otherwise keep ammo inside.
        return slot == SLOT_AMMO && dir == Direction.DOWN
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean {
        return slot == SLOT_AMMO && stack.item is BlockItem
    }
}

