package skyline.brahmeteor.math

/**
 * Pure predictor matching `FallingMeteor`'s discrete update rule:
 * - each tick increments time by 1
 * - then updates velocity:
 *     vx += time * alc
 *     vy += -time * alc
 *     vz += time * alc
 * - then moves position by the updated velocity
 *
 * This intentionally mirrors the in-mod logic (even if it's not physically realistic).
 */
object FallingMeteorPredictor {

    data class State(
        val pos: InterceptSolver.Vec3d,
        val vel: InterceptSolver.Vec3d,
        val timeTicks: Int,
        val accelFactor: Double
    )

    /**
     * @return positions sampled at tick boundaries: index 0 is current position (t=0),
     *         index i is predicted position after i ticks.
     */
    fun predictPositions(state: State, maxTicksAhead: Int): List<InterceptSolver.Vec3d> {
        val out = ArrayList<InterceptSolver.Vec3d>(maxTicksAhead + 1)
        var pos = state.pos
        var vel = state.vel
        var time = state.timeTicks
        val a = state.accelFactor

        out.add(pos)
        for (_i in 1..maxTicksAhead) {
            time += 1
            vel = InterceptSolver.Vec3d(
                vel.x + time * a,
                vel.y + -1.0 * (time * a),
                vel.z + time * a
            )
            pos = pos + vel
            out.add(pos)
        }
        return out
    }
}

