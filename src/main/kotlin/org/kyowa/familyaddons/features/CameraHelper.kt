package org.kyowa.familyaddons.features

import org.kyowa.familyaddons.config.FamilyConfigManager

/**
 * The camera tweaks this build ships: your own third person distance, and
 * keeping the camera off your face in front view.
 *
 * The camera never passes through a block here, and there is no freelook, so
 * the view can only ever show what you could see anyway. [freelookActive] stays
 * false for the mixin that reads it.
 */
object CameraHelper {

    /** Nothing in this build lets the camera sit inside a block. */
    fun isClipEnabled(): Boolean = false

    /** The distance the player asked for, or null to leave it alone. */
    fun getCustomDistance(): Float? {
        val cfg = FamilyConfigManager.config.utilities
        if (!cfg.cameraDistEnabled) return null
        return cfg.cameraDist.coerceIn(3f, 12f)
    }

    @JvmField @Volatile var freelookActive: Boolean = false
    @JvmField @Volatile var freelookYaw: Float = 0f
    @JvmField @Volatile var freelookPitch: Float = 0f

    fun register() {}
}
