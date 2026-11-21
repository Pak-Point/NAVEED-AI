package com.naveedai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class WakeWordService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACTION_COMMAND = "com.naveedai.assistant.WAKE_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val ACTION_STOP = "com.naveedai.assistant.STOP_WAKE"
        private const val CHANNEL_ID = "naveed_ai_wake"
        private const val NOTIFICATION_ID = 4102
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var waitingForCommand = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var active = false

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        createNotificationChannel()
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NaveedAI:WakeWord")
        wakeLock?.setReferenceCounted(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!active) {
            active = true
            try { wakeLock?.acquire() } catch (_: Exception) {}
            startListeningSoon(700)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "NAVEED AI Voice Assistant", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Keeps Hey Naveed voice activation available"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("NAVEED AI is listening")
            .setContentText("Hey Naveed voice activation is on. Tap to open the app.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun startListeningSoon(delay: Long) {
        handler.postDelayed({
            if (active) startListening()
        }, delay)
    }

    private fun startListening() {
        if (!active) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            startListeningSoon(3000)
            return
        }
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) { startListeningSoon(1300) }
                    override fun onResults(results: android.os.Bundle?) {
                        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        handlePhrase(phrase)
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {}
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ur-PK")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            startListeningSoon(1800)
        }
    }

    private fun handlePhrase(phrase: String) {
        if (!active) return
        val normalized = phrase.lowercase(Locale.ROOT)
        val wakeWords = listOf("hey naveed", "hay naveed", "ہے نوید", "ہی نوید", "اے نوید")
        if (waitingForCommand) {
            waitingForCommand = false
            sendCommand(phrase)
        } else {
            val wake = wakeWords.firstOrNull { normalized.contains(it) }
            if (wake != null) {
                val index = normalized.indexOf(wake)
                val command = phrase.substring((index + wake.length).coerceAtMost(phrase.length)).trim()
                if (command.isNotBlank()) {
                    sendCommand(command)
                } else {
                    waitingForCommand = true
                    speak("جی، بولیں۔")
                }
            }
        }
        startListeningSoon(900)
    }

    private fun sendCommand(command: String) {
        sendBroadcast(Intent(ACTION_COMMAND).setPackage(packageName).putExtra(EXTRA_COMMAND, command))
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "naveed-wake")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ur", "PK")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Foreground service remains active when the app task is swiped away.
    }

    override fun onDestroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
