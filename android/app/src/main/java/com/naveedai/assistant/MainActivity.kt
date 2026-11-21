package com.naveedai.assistant

import android.Manifest
import android.app.Activity
import android.app.AlarmClock
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private var torchOn = false
    private var speechLocale = "ur-PK"
    private val audioCode = 22
    private val cameraCode = 21

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 36, 26, 24)
            setBackgroundColor(0xFF071426.toInt())
        }
        root.addView(TextView(this).apply {
            text = "NAVEED AI"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(0xFF64D8FF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "آپ کا ذاتی موبائل اسسٹنٹ"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(-1)
            setPadding(0, 8, 0, 20)
        })
        status = TextView(this).apply {
            text = "السلام علیکم! میں Naveed AI ہوں۔"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(-1)
            setPadding(8, 18, 8, 18)
        }
        root.addView(status)
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("اردو", "سرائیکی", "English"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    speechLocale = when (position) { 1 -> "skr-PK"; 2 -> "en-PK"; else -> "ur-PK" }
                    val locale = when (position) { 1 -> Locale("skr", "PK"); 2 -> Locale("en", "PK"); else -> Locale("ur", "PK") }
                    if (::tts.isInitialized) tts.setLanguage(locale)
                }
            }
        })
        root.addView(Button(this).apply { text = "🎙 بولیں / Speak"; setOnClickListener { listen() } })
        input = EditText(this).apply { hint = "اپنا حکم یہاں لکھیں"; textSize = 16f; setSingleLine(true) }
        root.addView(input, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply { text = "حکم چلائیں"; setOnClickListener { command(input.text.toString()) } })
        root.addView(Button(this).apply { text = "🔦 ٹارچ آن / آف"; setOnClickListener { toggleTorch() } })
        root.addView(TextView(this).apply {
            text = "مثالیں: Open WhatsApp • YouTube کھولو • ٹارچ آن کرو • وقت بتاؤ"
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(0xFFB8C7D9.toInt()); setPadding(0, 20, 0, 0)
        })
        setContentView(root)
    }

    override fun onInit(result: Int) { if (result == TextToSpeech.SUCCESS) tts.setLanguage(Locale("ur", "PK")) }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { say("Speech recognition اس فون میں دستیاب نہیں۔"); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioCode); return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLocale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Naveed AI کو حکم دیں")
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "سن رہا ہوں…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "حکم سمجھ رہا ہوں…" }
            override fun onError(error: Int) { status.text = "آواز واضح نہیں ہوئی، دوبارہ کوشش کریں۔" }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                input.setText(phrase); command(phrase)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    private fun command(raw: String) {
        val c = raw.lowercase(Locale.ROOT).trim()
        when {
            c.contains("whatsapp") || c.contains("واٹس ایپ") -> openApp("com.whatsapp", "WhatsApp")
            c.contains("youtube") || c.contains("یوٹیوب") -> openApp("com.google.android.youtube", "YouTube")
            c.contains("facebook") || c.contains("فیس بک") -> openApp("com.facebook.katana", "Facebook")
            c.contains("instagram") || c.contains("انسٹاگرام") -> openApp("com.instagram.android", "Instagram")
            c.contains("torch") || c.contains("flash") || c.contains("ٹارچ") -> toggleTorch()
            c.contains("time") || c.contains("وقت") -> say("ابھی وقت ہے " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()))
            c.contains("alarm") || c.contains("الارم") -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, 7).putExtra(AlarmClock.EXTRA_MINUTES, 0)
                if (i.resolveActivity(packageManager) != null) startActivity(i) else say("الارم ایپ دستیاب نہیں۔")
            }
            c.isBlank() -> say("جی، اپنا حکم بتائیں۔")
            else -> say("یہ کمانڈ ابھی شامل نہیں۔ اگلے ورژن میں AI گفتگو اور مزید کنٹرول شامل ہوں گے۔")
        }
    }

    private fun openApp(pkg: String, label: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) { startActivity(launch); status.text = "$label کھول رہا ہوں" }
        else say("$label آپ کے فون میں انسٹال نہیں۔")
    }

    private fun toggleTorch() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraCode); return
        }
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: run { say("ٹارچ دستیاب نہیں۔"); return }
            torchOn = !torchOn
            manager.setTorchMode(id, torchOn)
            say(if (torchOn) "ٹارچ آن کر دی" else "ٹارچ آف کر دی")
        } catch (_: Exception) { say("ٹارچ کنٹرول نہیں ہو سکی۔") }
    }

    private fun say(message: String) {
        status.text = message
        if (::tts.isInitialized) tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "naveed-ai")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == audioCode) listen()
            if (requestCode == cameraCode) toggleTorch()
        } else say("اس کام کے لیے اجازت ضروری ہے۔")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
