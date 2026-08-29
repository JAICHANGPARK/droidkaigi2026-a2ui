package com.example.a2uicomposelabs.forms

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * A generated form, kept.
 *
 * [lines] is the whole point: a form produced by a model is nothing but its JSONL, so saving
 * those lines saves the form itself. Replaying them later reproduces it exactly, with no
 * model, no network, and no chance of a different answer. [prompt] is kept beside them so it
 * is still possible to see what was asked for months later, and to ask for a variation.
 */
@Serializable
data class SavedForm(
    val id: String,
    val prompt: String,
    val createdAtMillis: Long,
    /** How it was produced, e.g. "Gemini · gemini-2.5-flash" or "Recorded preset". */
    val source: String,
    val lines: List<String>,
) {
    val createdAtLabel: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(createdAtMillis))
}

/**
 * Stores saved forms in the app's private files directory, so they survive the app being
 * killed and the device being rebooted.
 */
class SavedFormStore internal constructor(private val file: File) {

    var forms by mutableStateOf(read())
        private set

    fun save(prompt: String, source: String, lines: List<String>): SavedForm {
        val form = SavedForm(
            id = UUID.randomUUID().toString(),
            prompt = prompt.trim(),
            createdAtMillis = System.currentTimeMillis(),
            source = source,
            lines = lines,
        )
        // Newest first: the last thing generated is the thing most likely wanted again.
        forms = listOf(form) + forms
        write()
        return form
    }

    fun delete(id: String) {
        forms = forms.filterNot { it.id == id }
        write()
    }

    private fun read(): List<SavedForm> =
        runCatching {
            if (!file.exists()) emptyList()
            else JSON.decodeFromString<List<SavedForm>>(file.readText())
        }.getOrDefault(emptyList())

    private fun write() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JSON.encodeToString(forms))
        }
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private const val FILE_NAME = "saved_forms.json"

        @Volatile private var instance: SavedFormStore? = null

        fun getInstance(context: Context): SavedFormStore =
            instance ?: synchronized(this) {
                instance ?: SavedFormStore(File(context.applicationContext.filesDir, FILE_NAME))
                    .also { instance = it }
            }
    }
}

@Composable
fun rememberSavedFormStore(): SavedFormStore {
    val context = LocalContext.current
    return remember { SavedFormStore.getInstance(context) }
}
