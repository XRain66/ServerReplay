package me.senseiwells.replay.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration

object DateTimeUtils {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd--HH-mm-ss")

    fun getFormattedDate(): String {
        return LocalDateTime.now().format(formatter)
    }

    fun Duration.formatHHMMSS(): String {
        val seconds = this.inWholeSeconds
        val hours = seconds / 3600
        return "%02d:".format(hours) + this.formatMMSS()
    }

    fun Duration.formatMMSS(): String {
        val seconds = this.inWholeSeconds
        val minutes = seconds % 3600 / 60
        val secs = seconds % 60
        return "%02d:%02d".format(minutes, secs)
    }
}