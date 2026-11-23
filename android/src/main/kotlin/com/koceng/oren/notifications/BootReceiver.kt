package com.koceng.oren.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarm hilang setelah HP reboot, jadi jadwalkan ulang saat boot selesai. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            ProactiveNotificationScheduler.schedule(context)
        }
    }
}
