package com.example.ytdownloader.ui.screens

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun formatDate(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(DATE_FMT)
}.getOrDefault(iso)
