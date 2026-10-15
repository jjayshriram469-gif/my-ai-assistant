package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFormatter {

    fun formatMessageTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateHeader(timestamp: Long): String {
        val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()

        return when {
            isSameDay(messageCal, nowCal) -> "Today"
            isYesterday(messageCal, nowCal) -> "Yesterday"
            messageCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) -> {
                val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
            else -> {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    fun formatRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        if (diff < 60_000L) return "Just now"

        val minutes = diff / (60_000L)
        if (minutes < 60) return "${minutes}m ago"

        val hours = diff / (3600_000L)
        if (hours < 24) return "${hours}h ago"

        val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()
        if (isYesterday(messageCal, nowCal)) return "Yesterday"

        val days = diff / (86400_000L)
        if (days < 7) return "${days}d ago"

        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getDateCategory(timestamp: Long): String {
        val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()

        val diff = nowCal.timeInMillis - timestamp
        val days = diff / (86400_000L)

        return when {
            isSameDay(messageCal, nowCal) -> "Today"
            isYesterday(messageCal, nowCal) -> "Yesterday"
            days < 7 -> "Previous 7 Days"
            days < 30 -> "Previous 30 Days"
            else -> "Older"
        }
    }

    fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(cal: Calendar, nowCal: Calendar): Boolean {
        val yesterdayCal = (nowCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(cal, yesterdayCal)
    }
}
