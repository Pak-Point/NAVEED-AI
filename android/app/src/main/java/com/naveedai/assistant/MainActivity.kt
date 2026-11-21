package com.naveedai.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.Firebase
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech

    private var recognizer: SpeechRecognizer? = null
    private var torchOn = false
    private var speechLocale = "ur-PK"
    private var wakeMode = false
    private var waitingForWakeCommand = false
    private var activityResumed = false
    private lateinit var geminiModel: GenerativeModel
    private val assistantScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val audioCode = 22
    private val cameraCode = 21
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
        geminiModel = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-4-hydrogen")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 30, 26, 28)
            setBackgroundColor(0xFF071426.toInt())
        }

        root.addView(TextView(this).apply {
            text = "NAVEED AI 2.0"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(0xFF64D8FF.toInt())
        })

        root.addView(TextView(this).apply {
            text = "آپ کا ذاتی موبائل اسسٹنٹ"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(-1)
            setPadding(0, 8, 0, 16)
        })

        status = TextView(this).apply {
            text = "السلام علیکم! میں Naveed AI ہوں۔"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(-1)
            setPadding(8, 14, 8, 14)
        }
        root.addView(status)

        val languageSpinner = Spinner(this)
        languageSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("اردو", "سرائیکی", "English")
        )
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                speechLocale = when (position) {
                    1 -> "skr-PK"
                    2 -> "en-PK"
                    else -> "ur-PK"
                }
                val locale = when (position) {
                    1 -> Locale("skr", "PK")
                    2 -> Locale("en", "PK")
                    else -> Locale("ur", "PK")
                }
                if (::tts.isInitialized) tts.setLanguage(locale)
            }
        }
        root.addView(languageSpinner)

        root.addView(Button(this).apply {
            text = "🎙 بولیں / Speak"
            setOnClickListener { listen() }
        })

        root.addView(Button(this).apply {
            text = "🟢 Hey Naveed آن / آف"
            setOnClickListener { toggleWakeMode() }
        })

        input = EditText(this).apply {
            hint = "اپنا سوال یا حکم یہاں لکھیں"
            textSize = 16f
            setSingleLine(false)
            minLines = 2
            setTextColor(-1)
            setHintTextColor(0xFFB8C7D9.toInt())
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2))

        root.addView(Button(this).apply {
            text = "Gemini سے پوچھیں / حکم چلائیں"
            setOnClickListener { processUserInput(input.text.toString()) }
        })

        root.addView(Button(this).apply {
            text = "🔦 ٹارچ آن / آف"
            setOnClickListener { toggleTorch() }
        })

        root.addView(TextView(this).apply {
            text = "مثالیں: Hey Naveed • WhatsApp کھولو • وقت بتاؤ • پاکستان کی تاریخ بتاؤ"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFB8C7D9.toInt())
            setPadding(0, 18, 0, 0)
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) tts.setLanguage(Locale("ur", "PK"))
    }

    private fun listen() {
        wakeMode = false
        waitingForWakeCommand = false
        startRecognition(false)
    }

    private fun toggleWakeMode() {
        if (wakeMode) {
            wakeMode = false
            waitingForWakeCommand = false
            recognizer?.cancel()
            status.text = "Hey Naveed بند ہے۔"
            say("وائس ایکٹیویشن بند کر دی گئی ہے۔")
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioCode)
            return
        }

        wakeMode = true
        waitingForWakeCommand = false
        say("Hey Naveed فعال ہے۔ ایپ کھلی رکھیں اور کہیں Hey Naveed۔")
        scheduleWakeListening(900)
    }

    private fun startRecognition(isWakeListening: Boolean) {
        if (!activityResumed && !isWakeListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("اس فون میں Speech Recognition دستیاب نہیں۔")
            wakeMode = false
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioCode)
            return
        }

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (isWakeListening) "Hey Naveed کہیں" else "Naveed AI کو حکم دیں"
            )
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (wakeMode) status.text = "Hey Naveed سن رہا ہوں…"
                    else status.text = "سن رہا ہوں…"
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "آواز سمجھ رہا ہوں…" }
                override fun onError(error: Int) {
                    if (wakeMode && activityResumed) scheduleWakeListening(900)
                    else status.text = "آواز واضح نہیں ہوئی، دوبارہ کوشش کریں۔"
                }
                override fun onResults(results: Bundle?) {
                    val phrase = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()

                    if (wakeMode) {
                        handleWakeResult(phrase)
                    } else {
                        processUserInput(phrase)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        try {
            recognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            if (wakeMode) scheduleWakeListening(1200)
            else say("مائیکروفون شروع نہیں ہو سکا۔ دوبارہ کوشش کریں۔")
        }
    }

    private fun handleWakeResult(phrase: String) {
        if (!wakeMode) return

        if (!waitingForWakeCommand) {
            val commandAfterWake = removeWakePhrase(phrase)
            if (commandAfterWake == null) {
                scheduleWakeListening(700)
                return
            }

            if (commandAfterWake.isBlank()) {
                waitingForWakeCommand = true
                say("جی، بولیں۔")
                scheduleWakeListening(700)
            } else {
                say("جی، آپ کا حکم سمجھ رہا ہوں۔")
                processUserInput(commandAfterWake)
            }
        } else {
            waitingForWakeCommand = false
            processUserInput(phrase)
        }
    }

    private fun removeWakePhrase(phrase: String): String? {
        val lower = phrase.lowercase(Locale.ROOT)
        val wakeWords = listOf("hey naveed", "hay naveed", "ہے نوید", "ہی نوید", "اے نوید")
        val matched = wakeWords.firstOrNull { lower.contains(it) } ?: return null
        val index = lower.indexOf(matched)
        return phrase.substring(index + matched.length).trim()
    }

    private fun scheduleWakeListening(delay: Long) {
        mainHandler.postDelayed({
            if (wakeMode && activityResumed) startRecognition(true)
        }, delay)
    }

    private fun processUserInput(raw: String) {
        val question = raw.trim()
        if (question.isBlank()) {
            say("جی، اپنا سوال یا حکم بتائیں۔")
            if (wakeMode) scheduleWakeListening(900)
            return
        }

        input.setText(question)
        if (!runPhoneCommand(question)) {
            askGemini(question)
        } else if (wakeMode) {
            scheduleWakeListening(1100)
        }
    }

    private fun runPhoneCommand(raw: String): Boolean {
        val c = raw.lowercase(Locale.ROOT).trim()
        when {
            c.contains("whatsapp") || c.contains("واٹس ایپ") -> openApp("com.whatsapp", "WhatsApp")
            c.contains("youtube") || c.contains("یوٹیوب") -> openApp("com.google.android.youtube", "YouTube")
            c.contains("facebook") || c.contains("فیس بک") -> openApp("com.facebook.katana", "Facebook")
            c.contains("instagram") || c.contains("انسٹاگرام") -> openApp("com.instagram.android", "Instagram")
            c.contains("torch") || c.contains("flash") || c.contains("ٹارچ") -> toggleTorch()
            c.contains("time") || c.contains("وقت") -> say(
                "ابھی وقت ہے " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            )
            c.contains("alarm") || c.contains("الارم") -> {
                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, 7)
                    .putExtra(AlarmClock.EXTRA_MINUTES, 0)
                if (alarmIntent.resolveActivity(packageManager) != null) startActivity(alarmIntent)
                else say("الارم ایپ دستیاب نہیں۔")
            }
            else -> return false
        }
        return true
    }

    private fun askGemini(question: String) {
        status.text = "Firebase Gemini سوچ رہا ہے…"
        assistantScope.launch {
            try {
                val prompt = "You are Naveed AI, the same assistant as Pak Point AI. " +
                    "Reply in the same language as the user. Support Urdu, Saraiki, Roman Urdu, and English. " +
                    "Be clear, friendly, accurate, and concise. Do not invent current facts. " +
                    "User message: " + question
                val response = geminiModel.generateContent(prompt)
                val answer = response.text.orEmpty().trim()
                if (answer.isNotBlank()) say(answer)
                else say("Firebase Gemini سے خالی جواب موصول ہوا۔")
            } catch (e: Exception) {
                val detail = e.message?.take(220).orEmpty()
                say("Firebase Gemini سے رابطہ نہیں ہو سکا۔ انٹرنیٹ، Firebase AI Logic اور App Check چیک کریں۔ " + detail)
            } finally {
                if (wakeMode) scheduleWakeListening(1200)
            }
        }
    }

    private fun openApp(pkg: String, label: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            startActivity(launch)
            status.text = label + " کھول رہا ہوں"
        } else {
            say(label + " آپ کے فون میں انسٹال نہیں۔")
        }
    }

    private fun toggleTorch() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraCode)
            return
        }
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: run {
                say("ٹارچ دستیاب نہیں۔")
                return
            }
            torchOn = !torchOn
            manager.setTorchMode(id, torchOn)
            say(if (torchOn) "ٹارچ آن کر دی" else "ٹارچ آف کر دی")
        } catch (_: Exception) {
            say("ٹارچ کنٹرول نہیں ہو سکی۔")
        }
    }

    private fun say(message: String) {
        status.text = message
        if (::tts.isInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "naveed-ai")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == audioCode) {
                if (wakeMode) scheduleWakeListening(500) else listen()
            }
            if (requestCode == cameraCode) toggleTorch()
        } else {
            say("اس کام کے لیے اجازت ضروری ہے۔")
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (wakeMode) scheduleWakeListening(800)
    }

    override fun onPause() {
        activityResumed = false
        recognizer?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        assistantScope.cancel()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
