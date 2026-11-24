package ir.aghayemoallem.app

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import java.util.UUID
import android.util.Log

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready by mutableStateOf(false)
    private var status by mutableStateOf("در حال آماده‌سازی موتور گفتار…")
    private var offlineVoiceAvailable by mutableStateOf(false)
    private var saving by mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var saveTimeout: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this, "com.github.opscalehub.avacore")
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) { if (utteranceId.startsWith("save-")) Log.i("AghayeMoallemTTS", "Started: $utteranceId") }
            override fun onDone(utteranceId: String) {
                if (utteranceId.startsWith("save-")) Log.i("AghayeMoallemTTS", "Done: $utteranceId")
                if (utteranceId.startsWith("save-")) runOnUiThread {
                    saveTimeout?.let(mainHandler::removeCallbacks)
                    saveTimeout = null
                    saving = false
                    try {
                        val temp = File(cacheDir, "$utteranceId.wav")
                        if (!temp.exists() || temp.length() < 1000L) {
                            status = "فایل صوتی ساخته نشد؛ موتور گفتار خروجی نداد."
                            return@runOnUiThread
                        }
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "AghayeMoallem-$utteranceId.wav")
                            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AghayeMoallem")
                        }
                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: throw IllegalStateException("ذخیره‌سازی در پوشه دانلود انجام نشد")
                        contentResolver.openOutputStream(uri)?.use { out ->
                            temp.inputStream().use { input -> input.copyTo(out) }
                        } ?: throw IllegalStateException("فایل مقصد باز نشد")
                        temp.delete()
                        status = "فایل صوتی در پوشه Download/AghayeMoallem ذخیره شد."
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/wav"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(share, "ارسال فایل صوتی درس"))
                    } catch (e: Exception) {
                        status = "خطا در ذخیره فایل: ${e.localizedMessage ?: "نامشخص"}"
                    }
                }
            }
            override fun onError(utteranceId: String) {
                if (utteranceId.startsWith("save-")) Log.e("AghayeMoallemTTS", "Error: $utteranceId")
                if (utteranceId.startsWith("save-")) runOnUiThread {
                    saveTimeout?.let(mainHandler::removeCallbacks)
                    saveTimeout = null
                    saving = false
                    status = "AvaCore هنگام ساخت فایل خطا داد؛ یک متن کوتاه‌تر را امتحان کن."
                }
            }
        })
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    TeacherScreen(
                        ready = ready,
                        status = status,
                        offlineVoiceAvailable = offlineVoiceAvailable,
                        saving = saving,
                        onSpeak = { value, rate ->
                            tts?.setSpeechRate(rate)
                            tts?.speak(value, TextToSpeech.QUEUE_FLUSH, null, "teacher-utterance")
                        },
                        onSave = { value, rate ->
                            val engine = tts
                            if (engine != null && ready && value.isNotBlank() && !saving) {
                                saving = true
                                status = "در حال ساخت فایل صوتی فارسی…"
                                engine.setSpeechRate(rate)
                                val id = "save-" + UUID.randomUUID().toString().take(8)
                                val target = File(cacheDir, "$id.wav")
                                val timeout = Runnable {
                                    if (saving) {
                                        saving = false
                                        engine.stop()
                                        target.delete()
                                        status = "AvaCore تا ۹۰ ثانیه پاسخ نداد؛ فایل ساخته نشد."
                                        Log.e("AghayeMoallemTTS", "Timeout waiting for synthesis callback")
                                    }
                                    saveTimeout = null
                                }
                                saveTimeout = timeout
                                mainHandler.postDelayed(timeout, 90000L)
                                Log.i("AghayeMoallemTTS", "Submitting file synthesis; chars=${value.length}")
                                // Some TTS engines block while accepting synthesizeToFile.
                                // Never call this Binder API on the UI thread.
                                Thread({
                                    val result = try {
                                        engine.synthesizeToFile(value, Bundle(), target, id)
                                    } catch (e: Exception) {
                                        TextToSpeech.ERROR
                                    }
                                    if (result != TextToSpeech.SUCCESS) runOnUiThread {
                                        saveTimeout?.let(mainHandler::removeCallbacks)
                                        saveTimeout = null
                                        saving = false
                                        target.delete()
                                        status = "موتور گفتار درخواست ساخت فایل را نپذیرفت."
                                    }
                                }, "AvaCore-file-synthesis").start();
                            }
                        },
                        onStop = { saveTimeout?.let(mainHandler::removeCallbacks); saveTimeout = null; tts?.stop(); saving = false; status = "عملیات متوقف شد." },
                        onChooseEngine = { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                    )
                }
            }
        }
    }

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            status = "موتور گفتار گوشی راه‌اندازی نشد."
            return
        }
        val engine = tts ?: run {
            status = "اتصال به موتور AvaCore برقرار نشد."
            return
        }
        val languageResult = engine.setLanguage(Locale("fa", "IR"))
        val avaVoice = engine.voices?.firstOrNull { it.name == "fa-ir-ava-goftari" }
        val voiceResult = if (avaVoice != null) engine.setVoice(avaVoice) else TextToSpeech.ERROR
        ready = languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED &&
            voiceResult == TextToSpeech.SUCCESS
        offlineVoiceAvailable = avaVoice != null && !avaVoice.isNetworkConnectionRequired
        status = when {
            avaVoice == null -> "صدای AvaCore پیدا نشد؛ برنامه باید به موتور AvaCore متصل باشد."
            voiceResult != TextToSpeech.SUCCESS -> "انتخاب صدای فارسی AvaCore ناموفق بود."
            !ready -> "موتور AvaCore زبان فارسی را آماده نکرد."
            else -> "به صدای فارسی AvaCore متصل شد؛ آماده پخش و ساخت فایل آفلاین."
        }
    }

    override fun onDestroy() {
        saveTimeout?.let(mainHandler::removeCallbacks)
        saveTimeout = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

@Composable
private fun TeacherScreen(
    ready: Boolean,
    status: String,
    offlineVoiceAvailable: Boolean,
    saving: Boolean,
    onSpeak: (String, Float) -> Unit,
    onSave: (String, Float) -> Unit,
    onStop: () -> Unit,
    onChooseEngine: () -> Unit
) {
    var text by remember {
        mutableStateOf("سلام بچه‌های عزیز! به کلاس آقای معلم خوش آمدید. امروز می‌خواهیم با هم یک درس تازه یاد بگیریم. آماده‌اید؟ پس شروع کنیم!")
    }
    var speed by remember { mutableFloatStateOf(0.9f) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("آقای معلم", style = MaterialTheme.typography.headlineLarge)
        Text("خواندن و ساخت فایل صوتی فارسی", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("متن درس") },
            minLines = 5
        )
        Text("سرعت خواندن: " + String.format("%.1f", speed))
        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.6f..1.3f)
        Button(
            onClick = { if (text.isNotBlank()) onSave(text, speed) },
            enabled = ready && text.isNotBlank() && !saving,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "در حال ساخت فایل صوتی…" else "ساخت و ذخیره فایل صوتی") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (text.isNotBlank()) onSpeak(text, speed) }, enabled = ready && text.isNotBlank()) {
                Text("پخش صدا")
            }
            OutlinedButton(onClick = onStop) { Text("توقف") }
        }
        OutlinedButton(onClick = onChooseEngine, modifier = Modifier.fillMaxWidth()) {
            Text("انتخاب موتور صدای فارسی")
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (offlineVoiceAvailable) "وضعیت: صدای فارسی آفلاین شناسایی شد."
            else "این نسخه از موتور گفتار نصب‌شده روی گوشی استفاده می‌کند؛ صدای فارسی باید در تنظیمات گوشی فعال باشد.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
