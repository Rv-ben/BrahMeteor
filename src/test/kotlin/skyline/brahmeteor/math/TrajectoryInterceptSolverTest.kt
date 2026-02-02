package skyline.brahmeteor.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrajectoryInterceptSolverTest {

    private fun assertClose(a: Double, b: Double, eps: Double = 1e-3) {
        assertTrue(abs(a - b) <= eps, "Expected $a ~= $b (eps=$eps)")
    }

    @Test
    fun acceleratingMeteorTrajectory_interceptsWithinTolerance() {
        // Construct a "meteor-like" trajectory using the same discrete update rule.
        val targetState = FallingMeteorPredictor.State(
            pos = InterceptSolver.Vec3d(0.0, 80.0, 0.0),
            vel = InterceptSolver.Vec3d(0.2, -1.0, 0.1),
            timeTicks = 20,
            accelFactor = 0.0012
        )
        val samples = FallingMeteorPredictor.predictPositions(targetState, maxTicksAhead = 80)

        val shooter = InterceptSolver.Vec3d(20.0, 65.0, 20.0)
        val speed = 12.0 // blocks/tick

        val sol = assertNotNull(
            TrajectoryInterceptSolver.solve(
                shooterPos = shooter,
                positionsPerTick = samples,
                projectileSpeed = speed,
                maxTimeTicks = 80
            )
        )

        // Validate by comparing projectile position vs interpolated target at tHit.
        fun targetPosAt(t: Double): InterceptSolver.Vec3d {
            val i = t.toInt().coerceIn(0, 79)
            val frac = t - i
            val p0 = samples[i]
            val p1 = samples[i + 1]
            return InterceptSolver.Vec3d(
                p0.x + (p1.x - p0.x) * frac,
                p0.y + (p1.y - p0.y) * frac,
                p0.z + (p1.z - p0.z) * frac
            )
        }

        val t = sol.time
        val projPos = shooter + (sol.direction * (speed * t))
        val tgtPos = targetPosAt(t)

        // We expect a very small miss due to interpolation + search tolerance.
        assertClose(projPos.x, tgtPos.x, 0.2)
        assertClose(projPos.y, tgtPos.y, 0.2)
        assertClose(projPos.z, tgtPos.z, 0.2)
    }
}

