package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.JournalDao
import com.example.data.local.SignalEventEntity
import com.example.domain.model.NotificationDeliveryStatus
import com.example.domain.model.PriceSourceType
import com.example.domain.model.SignalEventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class SignalNotificationManager(
    private val context: Context,
    private val dao: JournalDao
) {
    private val deliveredIdempotencyKeys = ConcurrentHashMap.newKeySet<String>()

    companion object {
        const val CHANNEL_TP = "jurnal_sinyal_tp_channel"
        const val CHANNEL_SL = "jurnal_sinyal_sl_channel"
        const val CHANNEL_EVENTS = "jurnal_sinyal_events_channel"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // 1. TP Channel (Distinct notification sound + upbeat double-pulse vibration)
            val tpSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val tpChannel = NotificationChannel(
                CHANNEL_TP,
                "Target Take Profit (TP) Tercapai",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi ketika target TP1, TP2, dst. tercapai"
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 140L, 80L, 220L)
                setSound(tpSoundUri, audioAttrs)
            }

            // 2. SL Channel (Distinct alarm/alert sound + long warning vibration)
            val slSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val slChannel = NotificationChannel(
                CHANNEL_SL,
                "Stop Loss (SL) Tersentuh",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan penting ketika level Stop Loss tersentuh"
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 380L, 140L, 380L)
                setSound(slSoundUri, audioAttrs)
            }

            // 3. Entry, Cancellation & Connectivity Channel
            val eventChannel = NotificationChannel(
                CHANNEL_EVENTS,
                "Entry, Status Sinyal & Koneksi",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi sentuhan Entry pertama, pembatalan sinyal, dan pemulihan internet"
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 160L)
            }

            nm.createNotificationChannel(tpChannel)
            nm.createNotificationChannel(slChannel)
            nm.createNotificationChannel(eventChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Persists the event in the database BEFORE attempting to send the notification,
     * prevents duplicate notifications via idempotencyKey, and updates delivery status.
     */
    suspend fun persistAndNotifyEvent(
        event: SignalEventEntity,
        notificationsEnabledInSettings: Boolean
    ): SignalEventEntity {
        // 1. Check idempotency in DB & memory so duplicate events/notifications never overwrite original event
        val existing = dao.getEventByIdempotencyKey(event.idempotencyKey)
        if (existing != null) {
            if (existing.notificationStatus == NotificationDeliveryStatus.SENT.name) {
                deliveredIdempotencyKeys.add(event.idempotencyKey)
            }
            return existing
        }

        // 2. Save event BEFORE sending notification (Section 11 rule)
        dao.upsertEvent(event)

        if (event.notificationStatus == NotificationDeliveryStatus.NOT_REQUIRED.name) {
            return event
        }

        return deliverNotificationForSavedEvent(event, notificationsEnabledInSettings)
    }

    suspend fun deliverNotificationForSavedEvent(
        event: SignalEventEntity,
        notificationsEnabledInSettings: Boolean
    ): SignalEventEntity {
        if (!deliveredIdempotencyKeys.add(event.idempotencyKey) &&
            event.notificationStatus == NotificationDeliveryStatus.SENT.name
        ) {
            return event
        }

        if (!notificationsEnabledInSettings || !hasNotificationPermission()) {
            val failedEvent = event.copy(
                notificationStatus = NotificationDeliveryStatus.FAILED.name,
                isPendingSync = true
            )
            dao.upsertEvent(failedEvent)
            deliveredIdempotencyKeys.remove(event.idempotencyKey)
            return failedEvent
        }

        return try {
            val channelId = when (event.eventType) {
                SignalEventType.TP_HIT.name -> CHANNEL_TP
                SignalEventType.SL_HIT.name -> CHANNEL_SL
                else -> CHANNEL_EVENTS
            }

            val timeFormatted = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
                .format(Date(event.eventTimestampMillis))
            val sourceLabel = try {
                PriceSourceType.valueOf(event.priceSource).label
            } catch (_: Exception) {
                event.priceSource
            }
            val eventLabel = try {
                SignalEventType.valueOf(event.eventType).label
            } catch (_: Exception) {
                event.eventType
            }

            val title = "${event.pair} [${event.direction}] • $eventLabel"
            val body = "Harga: ${event.price} | Waktu: $timeFormatted | Sumber: $sourceLabel"

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n${event.message}"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(event.idempotencyKey.hashCode(), notification)

            val sentEvent = event.copy(
                notificationStatus = NotificationDeliveryStatus.SENT.name,
                isPendingSync = true
            )
            dao.upsertEvent(sentEvent)
            sentEvent
        } catch (e: SecurityException) {
            val failed = event.copy(
                notificationStatus = NotificationDeliveryStatus.FAILED.name,
                notificationRetryCount = event.notificationRetryCount + 1,
                isPendingSync = true
            )
            dao.upsertEvent(failed)
            deliveredIdempotencyKeys.remove(event.idempotencyKey)
            failed
        } catch (e: Exception) {
            val failed = event.copy(
                notificationStatus = NotificationDeliveryStatus.FAILED.name,
                notificationRetryCount = event.notificationRetryCount + 1,
                isPendingSync = true
            )
            dao.upsertEvent(failed)
            deliveredIdempotencyKeys.remove(event.idempotencyKey)
            failed
        }
    }

    /**
     * Retries failed notifications once automatically after connectivity recovers.
     */
    suspend fun retryFailedNotificationsOnConnectionRecovery(
        userId: String,
        notificationsEnabledInSettings: Boolean
    ) {
        val failedList = dao.getFailedNotificationEvents(userId)
        for (event in failedList) {
            if (event.notificationRetryCount < 1) {
                deliverNotificationForSavedEvent(
                    event.copy(notificationRetryCount = event.notificationRetryCount + 1),
                    notificationsEnabledInSettings
                )
            }
        }
    }

    /**
     * Manual retry triggered by user from UI when a notification previously failed.
     */
    suspend fun manualRetryNotification(
        event: SignalEventEntity,
        notificationsEnabledInSettings: Boolean
    ): SignalEventEntity {
        deliveredIdempotencyKeys.remove(event.idempotencyKey)
        return deliverNotificationForSavedEvent(event, notificationsEnabledInSettings)
    }

    fun sendConnectionRestoredNotification(notificationsEnabledInSettings: Boolean) {
        if (!notificationsEnabledInSettings || !hasNotificationPermission()) return
        try {
            val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Koneksi Internet Pulih • Monitoring Aktif")
                .setContentText("Terhubung kembali pada $timeFormatted. Pemantauan harga sinyal dilanjutkan.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify("internet_restored".hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
