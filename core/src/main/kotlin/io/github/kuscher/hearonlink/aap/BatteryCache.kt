package io.github.kuscher.hearonlink.aap

/** Where a battery value came from. */
enum class Source { LIVE, ADVERT_PRECISE, ADVERT }

/** One part's last known level. [live] is false once the AirPods stop reporting it. */
data class PartLevel(val percent: Int, val charging: Boolean, val at: Long, val source: Source, val live: Boolean)

/**
 * Last known battery for each part. The AirPods only report what they can see right now: the case
 * only while a bud sits in it, a bud in a closed case not at all. So a "disconnected" report keeps
 * the old value (marked not live) instead of wiping it, and the case's Bluetooth adverts fill gaps.
 */
data class Batteries(
    val left: PartLevel? = null,
    val right: PartLevel? = null,
    val case: PartLevel? = null,
    val single: PartLevel? = null,
) {
    fun part(c: Component) = when (c) { Component.LEFT -> left; Component.RIGHT -> right; Component.CASE -> case; Component.SINGLE -> single }

    private fun with(c: Component, v: PartLevel?) = when (c) {
        Component.LEFT -> copy(left = v); Component.RIGHT -> copy(right = v)
        Component.CASE -> copy(case = v); Component.SINGLE -> copy(single = v)
    }

    /** A battery push from the control channel. */
    fun fromAap(readings: List<BatteryReading>, now: Long): Batteries {
        var b = this
        for (r in readings) {
            val old = b.part(r.component)
            b = if (r.level != null) b.with(r.component, PartLevel(r.level, r.charging, now, Source.LIVE, live = true))
            else b.with(r.component, old?.copy(live = false))
        }
        return b
    }

    /**
     * Levels from a proximity advert. Precise (decrypted, 1 %) values win over the coarse 10 % ones;
     * neither replaces a live control-channel value (the AirPods push those whenever they change).
     */
    fun fromAdvert(left: Int?, right: Int?, case: Int?, lc: Boolean, rc: Boolean, cc: Boolean, precise: Boolean, now: Long): Batteries {
        val src = if (precise) Source.ADVERT_PRECISE else Source.ADVERT
        fun merge(old: PartLevel?, v: Int?, charging: Boolean): PartLevel? {
            if (v == null) return old
            if (old != null && old.live) return old
            if (old != null && !precise && old.source != Source.ADVERT && now - old.at < 10 * 60_000 && kotlin.math.abs(old.percent - v) <= 10) return old
            return PartLevel(v, charging, now, src, live = false)
        }
        return copy(left = merge(this.left, left, lc), right = merge(this.right, right, rc), case = merge(this.case, case, cc))
    }

    /** Nothing is live once the session ends. */
    fun stale() = copy(left = left?.copy(live = false), right = right?.copy(live = false), case = case?.copy(live = false), single = single?.copy(live = false))

    /** Drop values older than [maxAgeMs]. */
    fun expire(now: Long, maxAgeMs: Long = 24 * 3600_000L): Batteries {
        fun keep(p: PartLevel?) = p?.takeIf { now - it.at <= maxAgeMs }
        return Batteries(keep(left), keep(right), keep(case), keep(single))
    }

    /** The lowest bud level we know right now (for the tile and low-battery checks). */
    fun lowestBud(): Int? = listOfNotNull(left?.percent, right?.percent, single?.percent).minOrNull()
}
