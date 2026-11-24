package ir.aghayemoallem.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready by mutableStateOf(false)
    private var status by mutableStateOf("در حال آماده‌سازی موتور گفتار…")
    private var offlineVoiceAvailable by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    TeacherScreen(
                        ready = ready,
                        status = status,
                        offlineVoiceAvailable = offlineVoiceAvailable,
                        onSpeak = { value, rate ->
                            tts?.setSpeechRate(rate)
                            tts?.speak(value, TextToSpeech.QUEUE_FLUSH, null, "teacher-utterance")
                        },
                        onStop = { tts?.stop() }
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
        val languageResult = tts?.setLanguage(Locale("fa", "IR"))
        ready = languageResult != null &&
            languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED

        val persianVoices: List<Voice> =
            (tts?.voices ?: emptySet()).filter { it.locale.language == "fa" }
        offlineVoiceAvailable = persianVoices.any { !it.isNetworkConnectionRequired }
        status = when {
            !ready -> "زبان فارسی روی موتور گفتار گوشی پشتیبانی نمی‌شود؛ باید صدای فارسی نصب شود."
            offlineVoiceAvailable -> "صدای فارسی آفلاین در موتور گوشی پیدا شد."
            persianVoices.isNotEmpty() -> "صدای فارسی پیدا شد، اما آفلاین بودن آن تأیید نشده است."
            else -> "موتور آماده است؛ برای گفتار فارسی باید صدای فارسی نصب شود."
        }
    }

    override fun onDestroy() {
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
    onSpeak: (String, Float) -> Unit,
    onStop: () -> Unit
) {
    var text by remember {
        mutableStateOf("سلام بچه‌های عزیز! امروز با هم یک درس تازه یاد می‌گیریم.")
    }
    var speed by remember { mutableFloatStateOf(0.9f) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("آقای معلم", style = MaterialTheme.typography.headlineLarge)
        Text("ابزار خواندن متن فارسی", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("متن درس") },
            minLines = 5
        )
        Text("سرعت خواندن: " + String.format("%.1f", speed))
        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.6f..1.3f)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (text.isNotBlank()) onSpeak(text, speed) }, enabled = ready && text.isNotBlank()) {
                Text("پخش صدا")
            }
            OutlinedButton(onClick = onStop) { Text("توقف") }
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (offlineVoiceAvailable) "وضعیت: صدای فارسی آفلاین شناسایی شد."
            else "توجه: این نسخه از موتور گفتار نصب‌شده روی گوشی استفاده می‌کند؛ صدای اختصاصی آفلاین هنوز به برنامه اضافه نشده است.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
