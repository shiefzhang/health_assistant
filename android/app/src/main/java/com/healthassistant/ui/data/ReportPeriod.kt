package com.healthassistant.ui.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 报表类型 */
enum class ReportType(val label: String) {
    WEEK("周报"),
    MONTH("月报"),
    YEAR("年报"),
}

/** 报表时间范围 */
data class ReportPeriod(
    val type: ReportType,
    val start: LocalDate,
    val endExclusive: LocalDate,
) {
    val label: String
        get() = when (type) {
            ReportType.WEEK -> "${start.monthValue}月${start.dayOfMonth}日 - ${endExclusive.minusDays(1).monthValue}月${endExclusive.minusDays(1).dayOfMonth}日"
            ReportType.MONTH -> "${start.year}年${start.monthValue}月"
            ReportType.YEAR -> "${start.year}年"
        }

    val startIso: String get() = "${start.toString()}T00:00:00+08:00"
    val endIso: String get() = "${endExclusive.toString()}T00:00:00+08:00"

    fun previous(): ReportPeriod = when (type) {
        ReportType.WEEK -> copy(start = start.minusDays(7), endExclusive = endExclusive.minusDays(7))
        ReportType.MONTH -> {
            val ym = YearMonth.from(start).minusMonths(1)
            copy(start = ym.atDay(1), endExclusive = ym.plusMonths(1).atDay(1))
        }
        ReportType.YEAR -> copy(start = start.minusYears(1), endExclusive = endExclusive.minusYears(1))
    }

    fun next(): ReportPeriod = when (type) {
        ReportType.WEEK -> copy(start = start.plusDays(7), endExclusive = endExclusive.plusDays(7))
        ReportType.MONTH -> {
            val ym = YearMonth.from(start).plusMonths(1)
            copy(start = ym.atDay(1), endExclusive = ym.plusMonths(1).atDay(1))
        }
        ReportType.YEAR -> copy(start = start.plusYears(1), endExclusive = endExclusive.plusYears(1))
    }

    companion object {
        fun forType(type: ReportType): ReportPeriod = when (type) {
            ReportType.WEEK -> {
                val today = LocalDate.now()
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ReportPeriod(ReportType.WEEK, monday, monday.plusDays(7))
            }
            ReportType.MONTH -> {
                val ym = YearMonth.now()
                ReportPeriod(ReportType.MONTH, ym.atDay(1), ym.plusMonths(1).atDay(1))
            }
            ReportType.YEAR -> {
                val y = LocalDate.now().year
                ReportPeriod(ReportType.YEAR, LocalDate.of(y, 1, 1), LocalDate.of(y + 1, 1, 1))
            }
        }
    }
}
