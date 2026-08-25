package com.captainavi.app.update

/**
 * Pure version helpers for GitHub release tags like `v1.0.1` / `1.0.1`.
 */
object AppVersionCompare {
    fun normalize(version: String): List<Int> {
        val cleaned = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .substringBefore("+")
            .trim()
        if (cleaned.isEmpty()) return listOf(0)
        return cleaned.split('.').map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }
    }

    /** True when [remote] is strictly newer than [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        val a = normalize(remote)
        val b = normalize(local)
        val len = maxOf(a.size, b.size)
        for (i in 0 until len) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }
}
