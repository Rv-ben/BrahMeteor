package skyline.brahmeteor.math

import kotlin.math.abs

/**
 * Intercept solver against an arbitrary target trajectory sampled at tick boundaries.
 *
 * We search for the smallest t >= 0 such that:
 *   distance(shooter, targetPos(t)) <= projectileSpeed * t
 *
 * where targetPos(t) is linearly interpolated between samples at integer ticks.
 */
object TrajectoryInterceptSolver {

    data class Solution(
        val time: Double,
        val aimPoint: InterceptSolver.Vec3d,
        val direction: InterceptSolver.Vec3d
    )

    fun solve(
        shooterPos: InterceptSolver.Vec3d,
        positionsPerTick: List<InterceptSolver.Vec3d>,
        projectileSpeed: Double,
        maxTimeTicks: Int
    ): Solution? {
        require(projectileSpeed > 0.0)
        if (positionsPerTick.size < 2) return null

        val maxT = maxTimeTicks.coerceAtMost(positionsPerTick.size - 1)

        // f(t) = dist - speed*t. We want first t where f(t) <= 0.
        fun posAt(t: Double): InterceptSolver.Vec3d {
            val clamped = t.coerceIn(0.0, maxT.toDouble())
            val i = clamped.toInt().coerceAtMost(maxT - 1)
            val frac = clamped - i
            val p0 = positionsPerTick[i]
            val p1 = positionsPerTick[i + 1]
            return InterceptSolver.Vec3d(
                p0.x + (p1.x - p0.x) * frac,
                p0.y + (p1.y - p0.y) * frac,
                p0.z + (p1.z - p0.z) * frac
            )
        }

        fun f(t: Double): Double {
            val p = posAt(t)
            val d = (p - shooterPos).length()
            return d - projectileSpeed * t
        }

        // Coarse scan in whole ticks.
        var prevT = 0.0
        var prevF = f(prevT)
        if (prevF <= 0.0) {
            val aim = posAt(0.0)
            val dir = (aim - shooterPos).normalized()
            return Solution(time = 0.0, aimPoint = aim, direction = dir)
        }

        var bracketLo: Double? = null
        var bracketHi: Double? = null

        for (tick in 1..maxT) {
            val t = tick.toDouble()
            val ft = f(t)
            if (ft <= 0.0 && prevF > 0.0) {
                bracketLo = prevT
                bracketHi = t
                break
            }
            prevT = t
            prevF = ft
        }

        val lo0 = bracketLo ?: return null
        val hi0 = bracketHi ?: return null

        // Binary search within bracket for earliest hit time.
        var lo = lo0
        var hi = hi0
        for (_i in 0 until 40) {
            val mid = (lo + hi) * 0.5
            val fm = f(mid)
            if (fm <= 0.0) {
                hi = mid
            } else {
                lo = mid
            }
            if (abs(hi - lo) < 1e-4) break
        }

        val tHit = hi
        val aim = posAt(tHit)
        val dir = (aim - shooterPos).normalized()
        if (dir.lengthSquared() <= 1e-12) return null
        return Solution(time = tHit, aimPoint = aim, direction = dir)
    }
}

