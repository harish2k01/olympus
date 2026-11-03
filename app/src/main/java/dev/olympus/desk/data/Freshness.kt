package dev.olympus.desk.data

import java.time.Instant

internal enum class Freshness { Fresh, Aging, Stale, Unknown }

internal fun observationAgeSeconds(timestamp: String, now: Instant): Long? =
    runCatching { Instant.parse(timestamp) }.getOrNull()?.let { (now.epochSecond - it.epochSecond).coerceAtLeast(0) }

internal fun classifyFreshness(seconds: Long?): Freshness = when {
    seconds == null -> Freshness.Unknown
    seconds < 60 -> Freshness.Fresh
    seconds < 300 -> Freshness.Aging
    else -> Freshness.Stale
}

internal fun formatObservationAge(seconds: Long): String =
    if (seconds < 60) "$seconds s" else "${seconds / 60} min"
