package com.godzilla.app.domain.model

enum class TimeFrame(val label: String, val minutes: Int, val apiParam: String) {
    M1("1m", 1, "1m"),
    M5("5m", 5, "5m"),
    M15("15m", 15, "15m"),
    M30("30m", 30, "30m"),
    H1("1h", 60, "1h"),
    H4("4h", 240, "4h"),
    D1("1d", 1440, "1d");

    companion object {
        fun fromString(value: String): TimeFrame {
            return entries.find { it.label.equals(value, ignoreCase = true) || it.apiParam.equals(value, ignoreCase = true) } ?: M1
        }
    }
}
