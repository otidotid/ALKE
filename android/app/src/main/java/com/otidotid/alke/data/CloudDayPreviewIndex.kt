package com.otidotid.alke.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * Manifest-only cloud index. No Drive requests, no health data materialized.
 * New sidecars are independently encrypted, hash-bound to their monthly parent,
 * and can be skipped safely when an older manifest lacks them.
 */
internal object CloudDayPreviewIndex {
    private const val MAX_PREVIEW_BYTES = 16L * 1024L * 1024L
    private const val MAX_LINES = 7 * 200

    data class Window(val from: LocalDate, val through: LocalDate)

    /** Seven calendar days per segment, anchored to the first of the month. */
    fun windows(month: YearMonth, coveredThrough: LocalDate): List<Window> {
        require(YearMonth.from(coveredThrough) == month)
        val first = month.atDay(1)
        return generateSequence(first) { current -> current.plusDays(7) }
            .takeWhile { !it.isAfter(coveredThrough) }
            .map { start -> Window(start, minOf(start.plusDays(6), coveredThrough)) }
            .toList()
    }

    fun find(record: GoogleDriveArchiveRecord, day: LocalDate): CloudDayPreviewPart? {
        if (!record.encrypted || record.month != YearMonth.from(day).toString()) return null
        val through = record.coveredThrough?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (record.coveredThrough != null && (through == null || day.isAfter(through))) return null
        return record.dayPreviewParts.firstOrNull { part ->
            val from = runCatching { LocalDate.parse(part.fromDay) }.getOrNull()
            val end = runCatching { LocalDate.parse(part.throughDay) }.getOrNull()
            from != null && end != null && !end.isBefore(from) &&
                YearMonth.from(from) == YearMonth.from(day) &&
                YearMonth.from(end) == YearMonth.from(day) &&
                !day.isBefore(from) && !day.isAfter(end) &&
                part.monthlySha256 == record.sha256 &&
                part.sha256.matches(Regex("[a-fA-F0-9]{64}")) &&
                part.driveFileId.isNotBlank() &&
                part.sizeBytes in 1..MAX_PREVIEW_BYTES &&
                part.previewLineCount in 0..MAX_LINES
        }
    }
}
