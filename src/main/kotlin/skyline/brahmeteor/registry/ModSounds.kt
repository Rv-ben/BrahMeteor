package skyline.brahmeteor.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import skyline.brahmeteor.Constants

object ModSounds {
    lateinit var TURRET_SHOT: SoundEvent
    lateinit var TURRET_SERVO: SoundEvent

    fun initialize() {
        TURRET_SHOT = register("turret_shot")
        TURRET_SERVO = register("turret_servo")
    }

    private fun register(name: String): SoundEvent {
        val id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, name)
        val event = SoundEvent.createVariableRangeEvent(id)
        Registry.register(BuiltInRegistries.SOUND_EVENT, id, event)
        return event
    }
}

