package com.smini131.hoyocheckin.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WorkTimeCalculatorTest {
    @Test
    fun `오전 시간이 지나지 않았으면 오늘로 계산한다`() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = Instant.parse("2026-08-19T22:00:00Z").toEpochMilli() // 07:00 KST
        val next = WorkTimeCalculator.nextRun(now, zone, 8, 0)
        assertEquals("2026-08-20T08:00+09:00[Asia/Seoul]", Instant.ofEpochMilli(next).atZone(zone).toString())
    }

    @Test
    fun `오후 시간이 지났으면 다음 날로 계산한다`() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = Instant.parse("2026-08-20T12:30:00Z").toEpochMilli() // 21:30 KST
        val next = WorkTimeCalculator.nextRun(now, zone, 20, 0)
        assertEquals("2026-08-21T20:00+09:00[Asia/Seoul]", Instant.ofEpochMilli(next).atZone(zone).toString())
    }

    @Test
    fun `자정 경계에서도 항상 미래다`() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-08-20T00:00:00Z").toEpochMilli()
        val next = WorkTimeCalculator.nextRun(now, zone, 0, 0)
        assertTrue(next > now)
        assertEquals("2026-08-21T00:00Z[UTC]", Instant.ofEpochMilli(next).atZone(zone).toString())
    }

    @Test
    fun `DST 전진으로 없는 시각은 해당 지역 규칙으로 안전하게 보정한다`() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-08T05:00:00Z").toEpochMilli()
        val next = WorkTimeCalculator.nextRun(now, zone, 2, 30)
        val local = Instant.ofEpochMilli(next).atZone(zone)
        assertEquals(3, local.hour)
        assertEquals(30, local.minute)
    }

    @Test
    fun `기기 시간이 역행해도 계산 결과는 새 현재보다 미래다`() {
        val zone = ZoneId.of("Asia/Seoul")
        val rolledBack = Instant.parse("2026-08-19T10:00:00Z").toEpochMilli()
        val next = WorkTimeCalculator.nextRun(rolledBack, zone, 8, 0)
        assertTrue(next > rolledBack)
    }

    @Test
    fun `같은 슬롯과 목표 시각은 같은 unique work 이름이다`() {
        val target = 1_800_000_000_000L
        assertEquals(
            WorkNameFactory.name(ScheduleSlot.MORNING, target),
            WorkNameFactory.name(ScheduleSlot.MORNING, target)
        )
    }
}
