package org.qosp.notes.ui.links

/** When each node of the Network appears during the timelapse (milliseconds from the start). */
internal class TimelapseSchedule(val duration: Long, val appearMs: Map<String, Long>) {
    fun visibleAt(elapsed: Long): Set<String> = appearMs.filterValues { it <= elapsed }.keys
}

internal object Timelapse {
    fun duration(notes: Int): Long = (notes * 80L).coerceIn(3000L, 12000L)

    /**
     * [appearAt]: note key to creation time. A note of rank r (ties share the lowest rank) appears at
     * duration * (r + 1) / (count + 1). Each of [others] (ghosts, tags, attachments) appears with the earliest
     * note it is linked to in [neighbours], or at the end when it has none.
     */
    fun schedule(appearAt: Map<String, Long>, others: Collection<String>, neighbours: Map<String, Set<String>>): TimelapseSchedule {
        val count = appearAt.size
        val duration = duration(count)
        val times = appearAt.values.sorted()
        val result = HashMap<String, Long>()
        val firstRank = HashMap<Long, Int>()
        times.forEachIndexed { i, t -> firstRank.putIfAbsent(t, i) }
        for ((key, created) in appearAt) {
            val rank = firstRank.getValue(created)
            result[key] = duration * (rank + 1) / (count + 1)
        }
        for (key in others) {
            result[key] = neighbours[key].orEmpty().filter { it in appearAt }.minOfOrNull { result.getValue(it) } ?: duration
        }
        return TimelapseSchedule(duration, result)
    }
}
