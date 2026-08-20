package com.smini131.hoyocheckin.work

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

interface TimeProvider {
    fun nowMillis(): Long
    fun deviceZone(): ZoneId
}

object SystemTimeProvider : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun deviceZone(): ZoneId = ZoneId.systemDefault()
}

object ServerDayPolicy {
    val SERVER_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    fun dayKey(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(SERVER_ZONE).toLocalDate().toString()
}

object WorkTimeCalculator {
    fun nextRun(
        nowMillis: Long,
        zone: ZoneId,
        hour: Int,
        minute: Int
    ): Long {
        require(hour in 0..23 && minute in 0..59)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var candidate = resolve(now.toLocalDate(), LocalTime.of(hour, minute), zone)
        if (!candidate.isAfter(now)) {
            candidate = resolve(now.toLocalDate().plusDays(1), LocalTime.of(hour, minute), zone)
        }
        return candidate.toInstant().toEpochMilli()
    }

    private fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, time, zone)
}

object WorkNameFactory {
    fun name(slot: ScheduleSlot, targetMillis: Long): String =
        "genshin-checkin-${slot.name.lowercase()}-${targetMillis / 60_000L}"
}

enum class ScheduleSlot { MORNING, EVENING }
