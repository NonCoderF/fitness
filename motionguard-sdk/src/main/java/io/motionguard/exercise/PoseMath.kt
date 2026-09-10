package io.motionguard.exercise

import kotlin.math.acos
import kotlin.math.hypot

public object PoseMath {
    public fun angle(a: Point, b: Point, c: Point): Float {
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val denominator = hypot(abx.toDouble(), aby.toDouble()) * hypot(cbx.toDouble(), cby.toDouble())
        if (denominator <= 1e-6) return 0f
        val cosine = ((abx * cbx + aby * cby) / denominator).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine)).toFloat()
    }

    public fun distance(a: Point, b: Point): Float =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

    public fun velocity(previous: Point, current: Point, deltaTime: Float): Float {
        if (deltaTime <= 0f) return 0f
        return distance(previous, current) / deltaTime
    }
}

public fun angle(a: Point, b: Point, c: Point): Float = PoseMath.angle(a, b, c)

public fun distance(a: Point, b: Point): Float = PoseMath.distance(a, b)

public fun velocity(previous: Point, current: Point, deltaTime: Float): Float =
    PoseMath.velocity(previous, current, deltaTime)
