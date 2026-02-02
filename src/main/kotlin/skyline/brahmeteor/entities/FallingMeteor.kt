package skyline.brahmeteor.entities

import net.minecraft.core.BlockPos
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.level.Level.ExplosionInteraction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LightBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.Vec3
import skyline.brahmeteor.blocks.MeteorBlock
import skyline.brahmeteor.registry.ModBlocks
import skyline.brahmeteor.registry.ModEntities
import java.lang.Math.log
import kotlin.random.Random

class FallingMeteor : Entity {

    companion object {
        @JvmStatic
        val DATA_START_POS: EntityDataAccessor<BlockPos> = SynchedEntityData.defineId(
            FallingMeteor::class.java, 
            EntityDataSerializers.BLOCK_POS
        )

        @JvmStatic
        val DATA_SIZE: EntityDataAccessor<Int> = SynchedEntityData.defineId(
            FallingMeteor::class.java,
            EntityDataSerializers.INT
        )
    }

    private var currentVelocity: Vec3 = Vec3.ZERO
    private var startPos: BlockPos = BlockPos.ZERO
    private var downwardSpeed = 0.5
    private var lastLightPos: BlockPos? = null
    private var meteorSizeLocal: Int = 1
    var time: Int = 0
    var explosionDuration = 0;
    var testAlc: Double = .00012

    /**
     * Exposed kinematics inputs so other systems (e.g. turrets) can predict motion accurately.
     * This meteor's velocity is updated each tick by `calcCurrentVelocity(time)` and then applied
     * directly to movement.
     */
    fun getKinematicsTimeTicks(): Int = time
    fun getKinematicsAccelFactor(): Double = testAlc
    fun getKinematicsCurrentVelocity(): Vec3 = currentVelocity

    // Primary constructor for spawning (required for entity type registration)
    constructor(entityType: EntityType<out FallingMeteor>, level: Level) : super(entityType, level)

    // Convenience constructor for spawning at a position
    constructor(level: Level, pos: BlockPos, state: BlockState) : this(ModEntities.FALLING_METEOR, level) {
        val posX = pos.x + 0.5
        val posY = pos.y.toDouble()
        val posZ = pos.z + 0.5
        
        // Position at center of block
        setPos(posX, posY, posZ)
        
        // Set old positions for smooth interpolation (like vanilla FallingBlockEntity)
        xo = posX
        yo = posY
        zo = posZ
        
        // Store start position
        startPos = pos

        // Store size (from state) so renderer + landing block match
        val size = if (state.hasProperty(MeteorBlock.SIZE)) state.getValue(MeteorBlock.SIZE) else 1
        setMeteorSize(size)

        downwardSpeed = -(Random.nextDouble() * getMeteorSize())
        // Random horizontal drift
        val horizontalDrift = 2
        val xDrift = (Random.nextDouble() - 0.5 ) * horizontalDrift
        val zDrift = (Random.nextDouble() - 0.5) * horizontalDrift

        currentVelocity = Vec3(xDrift, downwardSpeed, zDrift)
        
        // Block building like vanilla FallingBlockEntity
        blocksBuilding = true
    }

    override fun tick() {
        time++

        calcCurrentVelocity(time)

        // Set constant velocity each tick (overrides any drag from previous tick)
        deltaMovement = currentVelocity
        
        // Move the entity (same as FallingBlockEntity)
        move(MoverType.SELF, deltaMovement)
        
        // Apply effects from blocks (same as FallingBlockEntity)
        applyEffectsFromBlocks()
        
        // Handle portal travel (same as FallingBlockEntity)
        handlePortal()
        
        // Handle server-side logic
        if (level() is ServerLevel && isAlive) {
            val blockPos = blockPosition()
            
            // Update dynamic lighting
            updateDynamicLight(blockPos)
            
            if (!onGround()) {
                // Remove if it's been falling too long or out of world
                if (time > 100 && (blockPos.y <= level().minY || blockPos.y > level().maxY) || time > 600) {
                    removeDynamicLight()
                    discard()
                }
            } else if ( this.verticalCollision || this.horizontalCollision) {
                onHitGround()
            }
        }
        
        // Apply drag like FallingBlockEntity (but we reset velocity next tick anyway)
        deltaMovement = deltaMovement.scale(0.98)
    }

    override fun remove(removalReason: RemovalReason) {
        // Turrets (and other systems) can discard meteors midair; ensure we clean up any
        // temporary `Blocks.LIGHT` left behind.
        if (!level().isClientSide) {
            removeDynamicLight()
        }
        super.remove(removalReason)
    }

    fun setStartPos(pos: BlockPos) {
        startPos = pos
        if (entityData != null) {
            entityData.set(DATA_START_POS, pos)
        }
    }

    fun getStartPos(): BlockPos {
        return if (entityData != null) {
            entityData.get(DATA_START_POS)
        } else {
            startPos
        }
    }

    fun setMeteorSize(size: Int) {
        val clamped = size.coerceIn(1, 5)
        meteorSizeLocal = clamped
        // entityData always exists here, but keep this safe for edge cases during construction/loading.
        runCatching { entityData.set(DATA_SIZE, clamped) }
    }

    fun getMeteorSize(): Int {
        return runCatching { entityData.get(DATA_SIZE) }.getOrElse { meteorSizeLocal }
    }

    fun getMeteorExplosionSize() : Float {

        val momentum = (-1 * downwardSpeed ) * getMeteorSize();
        val ex = 4 * log(momentum).toFloat()
        return ex;
    }

    fun calcCurrentVelocity(currentTime: Int) {
        currentVelocity = Vec3(
            currentVelocity.x + currentTime * testAlc,
            currentVelocity.y + -1 * (currentTime *  (testAlc)),
            currentVelocity.z + currentTime * testAlc
        )
    }

    private fun handleLandingExplosion(landingPos: BlockPos) {
        level().explode(
            null,
            landingPos.x.toDouble(),
            landingPos.y.toDouble() - 1 ,
            landingPos.z.toDouble(),
            getMeteorExplosionSize(),
            false,
            ExplosionInteraction.BLOCK)
    }

    private fun updateDynamicLight(currentPos: BlockPos) {
        // Remove light from previous position if it moved
        if (lastLightPos != null && lastLightPos != currentPos) {
            val oldState = level().getBlockState(lastLightPos!!)
            if (oldState.block == Blocks.LIGHT) {
                level().removeBlock(lastLightPos!!, false)
            }
        }

        // Place light at current position if air
        val currentState = level().getBlockState(currentPos)
        if (currentState.isAir) {
            // Place light block with level 15 (max brightness)
            val lightState = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15)
            level().setBlock(currentPos, lightState, Block.UPDATE_ALL)
            lastLightPos = currentPos
        }
    }

    private fun removeDynamicLight() {
        if (lastLightPos != null) {
            val state = level().getBlockState(lastLightPos!!)
            if (state.block == Blocks.LIGHT) {
                level().removeBlock(lastLightPos!!, false)
            }
            lastLightPos = null
        }
    }

    private fun onHitGround() {
        // Get the landing position
        val landingPos = blockPosition()

        handleLandingExplosion(landingPos)

        if (explosionDuration > getMeteorSize()){
            placeNewBlock(landingPos)
        }

        explosionDuration += 1
    }

    fun placeNewBlock(landingPos: BlockPos) {
        discard()
        removeDynamicLight()
        val blockStateToPlace = ModBlocks
            .METEOR_BLOCK
            .defaultBlockState()
            .setValue(MeteorBlock.SIZE, getMeteorSize())

        level().setBlock(landingPos, blockStateToPlace, Block.UPDATE_CLIENTS)
    }

    override fun getMovementEmission(): MovementEmission {
        return MovementEmission.NONE
    }

    override fun isPickable(): Boolean {
        return !isRemoved
    }

    // Make the meteor glow while falling
    override fun isCurrentlyGlowing(): Boolean {
        return true
    }

    // Glow color (orange/red for meteor)
    override fun getTeamColor(): Int {
        return 0xFF6600  // Orange color
    }

    override fun hurtServer(serverLevel: ServerLevel, damageSource: DamageSource, amount: Float): Boolean {
        return false // Meteor cannot be damaged
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        time = input.getIntOr("Time", 0)
        setMeteorSize(input.getIntOr("Size", 1))
        // Restore a default velocity if loaded from save
        if (currentVelocity == Vec3.ZERO) {
            currentVelocity = Vec3(0.0, -0.5, 0.0)
        }
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        output.putInt("Time", time)
        output.putInt("Size", getMeteorSize())
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_START_POS, BlockPos.ZERO)
        builder.define(DATA_SIZE, 1)
    }

}
