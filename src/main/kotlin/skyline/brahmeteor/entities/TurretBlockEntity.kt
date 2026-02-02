package skyline.brahmeteor.entities

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import skyline.brahmeteor.blocks.TurretBlock
import skyline.brahmeteor.math.FallingMeteorPredictor
import skyline.brahmeteor.math.InterceptSolver
import skyline.brahmeteor.math.TrajectoryInterceptSolver
import skyline.brahmeteor.registry.ModEntities
import skyline.brahmeteor.registry.ModSounds
import kotlin.math.atan2
import kotlin.math.sqrt

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
        private const val RECOIL_TICKS = 6
        private const val BURST_SHOTS = 3
        private const val BURST_INTERVAL_TICKS = 1L

        fun serverTick(level: Level, pos: BlockPos, state: BlockState, be: TurretBlockEntity) {
            if (level.isClientSide) return

            // Decay recoil every tick (server authoritative; client receives snapshots).
            if (be.recoilTicks > 0) {
                be.recoilTicks--
                be.setChanged()
                be.level?.sendBlockUpdated(be.worldPosition, be.blockState, be.blockState, 3)
            }

            // Burst fire: 3 shots at t, t+1, t+2. Each shot recomputes lead.
            if (be.burstRemaining > 0) {
                if (level.gameTime >= be.nextBurstGameTime) {
                    val ammo = be.items[SLOT_AMMO]
                    if (ammo.isEmpty) {
                        be.burstRemaining = 0
                        be.burstTargetId = -1
                        be.cooldownTicks = FIRE_COOLDOWN_TICKS
                        return
                    }

                    val target = (if (be.burstTargetId != -1) level.getEntity(be.burstTargetId) else null) as? FallingMeteor
                    if (target == null || !target.isAlive) {
                        be.burstRemaining = 0
                        be.burstTargetId = -1
                        be.cooldownTicks = FIRE_COOLDOWN_TICKS
                        return
                    }

                    be.fireSingleShot(level, pos, state, target)
                    be.burstRemaining--
                    be.nextBurstGameTime = level.gameTime + BURST_INTERVAL_TICKS

                    if (be.burstRemaining <= 0) {
                        be.burstTargetId = -1
                        be.cooldownTicks = FIRE_COOLDOWN_TICKS
                    }
                }
                return
            }

            if (be.cooldownTicks > 0) {
                be.cooldownTicks--
            }

            if (be.cooldownTicks > 0) return
            if (level.gameTime % SCAN_EVERY_TICKS.toLong() != 0L) return

            val ammo = be.items[SLOT_AMMO]
            if (ammo.isEmpty) return

            val facing = state.getValue(TurretBlock.FACING)

            // 1x2 turret: controller is the lower block. Aim/render around the top block center.
            val center = Vec3(
                pos.x + 0.5,
                pos.y + 1.5,
                pos.z + 0.5
            )
            val box = AABB(pos).inflate(RANGE_BLOCKS)

            val meteors = level.getEntitiesOfClass(FallingMeteor::class.java, box).filterIndexed {
                _,
                meteor -> meteor.getKinematicsTimeTicks() > 10 && ( (meteor.y - center.y) < 50)
            }

            if (meteors.isEmpty()) return

            val target = meteors.minByOrNull { it.distanceToSqr(center.x, center.y, center.z) } ?: return

            // Start burst at tick t, then t+1, t+2.
            be.burstTargetId = target.id
            be.burstRemaining = BURST_SHOTS
            be.nextBurstGameTime = level.gameTime
            // Fire the first shot immediately.
            be.fireSingleShot(level, pos, state, target)
            be.burstRemaining--
            be.nextBurstGameTime = level.gameTime + BURST_INTERVAL_TICKS
        }
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)
    private var cooldownTicks: Int = 0
    private var burstRemaining: Int = 0
    private var nextBurstGameTime: Long = 0L
    private var burstTargetId: Int = -1

    // Animation state (synced to client)
    private var aimYaw: Float = 0.0f   // radians
    private var aimPitch: Float = 0.0f // radians
    private var recoilTicks: Int = 0

    fun getAimYaw(): Float = aimYaw
    fun getAimPitch(): Float = aimPitch
    fun getRecoil01(): Float = (recoilTicks.toFloat() / RECOIL_TICKS.toFloat()).coerceIn(0.0f, 1.0f)

    private fun setAim(yawRad: Float, pitchRad: Float, recoil: Int) {
        // Clamp pitch to avoid extreme flipping.
        aimYaw = yawRad
        aimPitch = Mth.clamp(pitchRad, (-75f * Mth.DEG_TO_RAD), (45f * Mth.DEG_TO_RAD))
        recoilTicks = recoil.coerceAtLeast(recoilTicks)
        setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    private fun fireSingleShot(level: Level, pos: BlockPos, state: BlockState, target: FallingMeteor) {
        val ammo = items[SLOT_AMMO]
        if (ammo.isEmpty) return

        // Consume ammo (1 block).
        val ammoItem = ammo.item
        ammo.shrink(1)
        setChanged()

        val facing = state.getValue(TurretBlock.FACING)
        val center = Vec3(pos.x + 0.5, pos.y + 1.5, pos.z + 0.5)
        val muzzle = center.add(
            facing.getStepX() * 0.85,
            0.05,
            facing.getStepZ() * 0.85
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

        // Update animation targets (yaw/pitch) and recoil.
        val yawRad = atan2(dx, dz) // yaw around Y
        val horiz = sqrt(dx * dx + dz * dz)
        val pitchRad = -atan2(dy, horiz) // pitch up is negative X rotation in MC
        setAim(yawRad.toFloat(), pitchRad.toFloat(), RECOIL_TICKS)

        level.addFreshEntity(projectile)

        // Visual feedback: muzzle flash + better sound.
        if (level is ServerLevel) {
            level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.05, 0.05, 0.05, 0.01)
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.01)
        }
        val pitch = 0.95f + (level.random.nextFloat() * 0.15f)
        // Custom sound (will be silent until .ogg files are added)
        level.playSound(null, pos, ModSounds.TURRET_SHOT, SoundSource.BLOCKS, 1.0f, pitch)
        // Vanilla fallback so you hear it now
        level.playSound(null, pos, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, 1.0f, pitch)
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_SHOOT, SoundSource.BLOCKS, 0.35f, 1.15f + (level.random.nextFloat() * 0.1f))
    }

    override fun saveAdditional(valueOutput: ValueOutput) {
        super.saveAdditional(valueOutput)
        valueOutput.putFloat("AimYaw", aimYaw)
        valueOutput.putFloat("AimPitch", aimPitch)
        valueOutput.putInt("Recoil", recoilTicks)
    }

    override fun loadAdditional(valueInput: ValueInput) {
        super.loadAdditional(valueInput)
        aimYaw = valueInput.getFloatOr("AimYaw", 0.0f)
        aimPitch = valueInput.getFloatOr("AimPitch", 0.0f)
        recoilTicks = valueInput.getIntOr("Recoil", 0)
    }

    override fun getUpdateTag(registryLookup: net.minecraft.core.HolderLookup.Provider): net.minecraft.nbt.CompoundTag {
        return saveWithoutMetadata(registryLookup)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> {
        return ClientboundBlockEntityDataPacket.create(this)
    }

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

