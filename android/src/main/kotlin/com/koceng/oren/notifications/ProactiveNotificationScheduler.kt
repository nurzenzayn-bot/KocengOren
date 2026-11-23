package com.koceng.oren.notifications

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import com.koceng.oren.AndroidLauncher
import com.koceng.oren.ai.ConversationMemory
import com.koceng.oren.ai.PersonaConfig
import com.koceng.oren.ai.PersonaManager
import com.koceng.oren.ai.ProactiveContext
import com.koceng.oren.ai.ProactiveMessageGenerator
import com.koceng.oren.ai.ProactivePlanner
import com.koceng.oren.ai.ProactiveTrigger
import java.util.Calendar

/**
 * Phase 4: penjadwal notifikasi proaktif.
 *
 * Memakai AlarmManager (tanpa dependency tambahan) dan mengecek kondisi
 * setiap 2 jam. Semua keputusan ada di ProactivePlanner (core) supaya
 * tidak spam dan mudah diuji.
 */
object ProactiveNotificationScheduler {

    const val PREFS_NAME = "koceng_oren_prefs"
    const val KEY_ENABLED = "proactive_enabled"
    const val KEY_MEMORY = "conversation_memory"
    const val KEY_PERSONA = "persona_id"
    const val KEY_LAST_MORNING = "proactive_last_morning"
    const val KEY_LAST_NIGHT = "proactive_last_night"
    const val KEY_LAST_SILENCE = "proactive_last_silence"
    const val KEY_LAST_HOMEWORK = "proactive_last_homework"
    const val KEY_LAST_ANY = "proactive_last_any"

    private const val CHANNEL_ID = "koceng_oren_proactive"
    private const val CHANNEL_NAME = "Sapaan Koceng Oren"
    private const val NOTIFICATION_ID = 4101
    private const val ALARM_REQUEST_CODE = 4102
    private const val CHECK_INTERVAL_MS = AlarmManager.INTERVAL_HOUR * 2L
    private const val FIRST_DELAY_MS = 30L * 60L * 1000L

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    /** Jadwalkan pengecekan berkala. Aman dipanggil berulang kali. */
    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val firstTrigger = System.currentTimeMillis() + FIRST_DELAY_MS
        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                firstTrigger,
                CHECK_INTERVAL_MS,
                alarmPendingIntent(context)
            )
        } catch (e: Exception) {
            // Beberapa OEM membatasi alarm; abaikan supaya app tetap jalan.
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = alarmPendingIntent(context)
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            // abaikan
        }
        pendingIntent.cancel()
    }

    /**
     * Dipanggil oleh ProactiveAlarmReceiver. Mengembalikan true kalau
     * notifikasi benar-benar ditampilkan.
     */
    fun checkAndNotify(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ENABLED, true)) return false

        val memoryJson = prefs.getString(KEY_MEMORY, null) ?: return false
        val memory = try {
            ConversationMemory.fromJson(memoryJson)
        } catch (e: Exception) {
            return false
        }

        val lastTurn = memory.getLastTurn() ?: return false
        val now = System.currentTimeMillis()
        val localHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val facts = memory.getKeyFacts()

        val plannerContext = ProactiveContext(
            nowMillis = now,
            localHour = localHour,
            lastActiveMillis = memory.getLastActiveTimestamp(),
            lastMode = lastTurn.mode,
            homeworkSubject = facts["topik_terakhir"],
            lastMorningSentMillis = prefs.getLong(KEY_LAST_MORNING, 0L),
            lastNightSentMillis = prefs.getLong(KEY_LAST_NIGHT, 0L),
            lastSilenceSentMillis = prefs.getLong(KEY_LAST_SILENCE, 0L),
            lastHomeworkSentMillis = prefs.getLong(KEY_LAST_HOMEWORK, 0L),
            lastAnySentMillis = prefs.getLong(KEY_LAST_ANY, 0L)
        )

        val trigger = ProactivePlanner.decide(plannerContext) ?: return false
        val personaId = prefs.getString(KEY_PERSONA, "default") ?: "default"
        val persona = PersonaManager().getPersona(personaId) ?: PersonaConfig.default()
        val message = ProactiveMessageGenerator.build(
            trigger = trigger,
            userName = facts["nama"],
            keyFacts = facts,
            persona = persona
        )

        val shown = showNotification(context, message.title, message.body)
        if (shown) markSent(prefs, trigger, now)
        return shown
    }

    /** Notifikasi contoh untuk tombol "Tes Notifikasi" di pengaturan. */
    fun showTest(context: Context): Boolean = showNotification(
        context,
        "Koceng Oren 🐱",
        "Tes notifikasi berhasil! Nanti gue bakal nyapa lu kalau lama nggak ngobrol."
    )

    fun showNotification(context: Context, title: String, body: String): Boolean {
        if (!canPostNotifications(context)) return false
        ensureChannel(context)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_DEFAULT)
        }

        return try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, builder.build())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Sapaan, pengingat PR, dan notifikasi kangen dari Koceng Oren."
        }
        manager.createNotificationChannel(channel)
    }

    private fun markSent(prefs: SharedPreferences, trigger: ProactiveTrigger, now: Long) {
        val key = when (trigger) {
            is ProactiveTrigger.MorningGreeting -> KEY_LAST_MORNING
            is ProactiveTrigger.NightGreeting -> KEY_LAST_NIGHT
            is ProactiveTrigger.LongSilence -> KEY_LAST_SILENCE
            is ProactiveTrigger.HomeworkFollowUp -> KEY_LAST_HOMEWORK
        }
        prefs.edit()
            .putLong(key, now)
            .putLong(KEY_LAST_ANY, now)
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AndroidLauncher::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, NOTIFICATION_ID, intent, immutableFlags())
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ProactiveAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, immutableFlags())
    }

    private fun immutableFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
}
