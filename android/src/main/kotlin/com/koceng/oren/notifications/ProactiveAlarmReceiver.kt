package com.koceng.oren.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Menerima alarm berkala lalu meminta scheduler mengecek apakah Oren perlu menyapa. */
class ProactiveAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ProactiveNotificationScheduler.checkAndNotify(context)
    }
}
