package skyline.brahmeteor.math

import kotlin.math.sqrt

/**
 * Pure math intercept solver for constant-velocity targets and constant-speed projectiles.
 *
 * Model:
 * - Shooter at position S
 * - Target at position T0 with constant velocity Vt
 * - Projectile starts at S at time 0 with constant speed Sp (direction chosen)
 *
 * Find t > 0 such that |(T0 + Vt*t) - S| = Sp * t.
 */
object InterceptSolver {

    data class Vec3d(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: Vec3d) = Vec3d(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: Vec3d) = Vec3d(x - o.x, y - o.y, z - o.z)
        operator fun times(k: Double) = Vec3d(x * k, y * k, z * k)
        fun dot(o: Vec3d) = x * o.x + y * o.y + z * o.z
        fun lengthSquared() = dot(this)
        fun length() = sqrt(lengthSquared())
        fun normalized(): Vec3d {
            val len = length()
            return if (len <= 1e-12) Vec3d(0.0, 0.0, 0.0) else Vec3d(x / len, y / len, z / len)
        }
    }

    /**
     * @return smallest positive intercept time, or null if no solution.
     */
    fun solveInterceptTime(relativePos: Vec3d, targetVel: Vec3d, projectileSpeed: Double): Double? {
        require(projectileSpeed > 0.0) { "projectileSpeed must be > 0" }

        // Solve: |r + v*t|^2 = (s*t)^2
        // (v·v - s^2)t^2 + 2(r·v)t + (r·r) = 0
        val s2 = projectileSpeed * projectileSpeed
        val a = targetVel.lengthSquared() - s2
        val b = 2.0 * relativePos.dot(targetVel)
        val c = relativePos.lengthSquared()

        // Handle near-linear case (a ~= 0): b*t + c = 0
        if (kotlin.math.abs(a) < 1e-12) {
            if (kotlin.math.abs(b) < 1e-12) return null
            val t = -c / b
            return if (t > 1e-6) t else null
        }

        val disc = b * b - 4.0 * a * c
        if (disc < 0.0) return null
        val sqrtDisc = sqrt(disc)

        val t1 = (-b - sqrtDisc) / (2.0 * a)
        val t2 = (-b + sqrtDisc) / (2.0 * a)

        val eps = 1e-6
        val candidates = listOf(t1, t2).filter { it > eps }
        return candidates.minOrNull()
    }

    /**
     * Computes aim direction (unit vector) and predicted impact position.
     */
    fun solveAim(
        shooterPos: Vec3d,
        targetPos: Vec3d,
        targetVel: Vec3d,
        projectileSpeed: Double
    ): AimSolution? {
        val r = targetPos - shooterPos
        val t = solveInterceptTime(r, targetVel, projectileSpeed) ?: return null
        val predicted = targetPos + (targetVel * t)
        val dir = (predicted - shooterPos).normalized()
        if (dir.lengthSquared() <= 1e-12) return null
        return AimSolution(time = t, direction = dir, predictedTargetPos = predicted)
    }

    data class AimSolution(
        val time: Double,
        val direction: Vec3d,
        val predictedTargetPos: Vec3d
    )
}

