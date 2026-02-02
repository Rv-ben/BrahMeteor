package skyline.brahmeteor.math

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InterceptSolverTest {

    private fun assertClose(a: Double, b: Double, eps: Double = 1e-6) {
        assertTrue(abs(a - b) <= eps, "Expected $a ~= $b (eps=$eps)")
    }

    @Test
    fun stationaryTarget_hitsDirectly() {
        val shooter = InterceptSolver.Vec3d(0.0, 0.0, 0.0)
        val targetPos = InterceptSolver.Vec3d(10.0, 0.0, 0.0)
        val targetVel = InterceptSolver.Vec3d(0.0, 0.0, 0.0)
        val speed = 5.0

        val sol = assertNotNull(InterceptSolver.solveAim(shooter, targetPos, targetVel, speed))
        // Should aim along +X
        assertClose(sol.direction.x, 1.0, 1e-9)
        assertClose(sol.direction.y, 0.0, 1e-9)
        assertClose(sol.direction.z, 0.0, 1e-9)

        // Time should be distance/speed
        assertClose(sol.time, 10.0 / 5.0, 1e-9)
    }

    @Test
    fun movingTarget_interceptIsConsistent() {
        val shooter = InterceptSolver.Vec3d(0.0, 0.0, 0.0)
        val targetPos0 = InterceptSolver.Vec3d(10.0, 0.0, 0.0)
        val targetVel = InterceptSolver.Vec3d(1.0, 0.0, 0.0) // moving away along +X
        val speed = 6.0

        val sol = assertNotNull(InterceptSolver.solveAim(shooter, targetPos0, targetVel, speed))

        // Simulate at time t: projectile position vs target position should coincide.
        val t = sol.time
        val projectilePos = shooter + (sol.direction * (speed * t))
        val targetPos = targetPos0 + (targetVel * t)

        assertClose(projectilePos.x, targetPos.x, 1e-6)
        assertClose(projectilePos.y, targetPos.y, 1e-6)
        assertClose(projectilePos.z, targetPos.z, 1e-6)
    }
}

