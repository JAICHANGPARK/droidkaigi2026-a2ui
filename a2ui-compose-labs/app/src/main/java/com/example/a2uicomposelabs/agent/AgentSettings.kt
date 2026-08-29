package com.example.a2uicomposelabs.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.a2uicomposelabs.BuildConfig

/**
 * Where the live-agent demo gets its API key and model name.
 *
 * A key typed into Settings is stored in the app's private preferences and used ahead of the
 * build-time key — so the app can be built with no key at all, and nothing about the agent
 * ever needs to reach a source file or a binary.
 *
 * The stored value is not encrypted at rest. App-private storage is unreadable by other apps
 * on a normal device, which is enough for a demo; a production app would not hold a raw
 * provider key on the device in the first place.
 *
 * Backed by Compose state, so saving on the Settings screen immediately changes what the
 * demo screen uses.
 */
class AgentSettings internal constructor(private val prefs: SharedPreferences) {

    /** Which API a turn goes to. Gemini unless somebody chose otherwise. */
    var provider by mutableStateOf(AgentProvider.from(prefs.getString(KEY_PROVIDER, null)))
        private set

    /**
     * The key the user typed for [provider], or "" when there is none.
     *
     * Each provider keeps its own key, so switching back and forth does not make you retype
     * anything. Gemini's still lives under the original preference name — an app that already
     * had a key saved keeps it.
     */
    var apiKey by mutableStateOf(prefs.getString(keyPref(AgentProvider.from(prefs.getString(KEY_PROVIDER, null))), "").orEmpty())
        private set

    /** The model the user chose for [provider], or "" for that provider's default. */
    var model by mutableStateOf(prefs.getString(modelPref(AgentProvider.from(prefs.getString(KEY_PROVIDER, null))), "").orEmpty())
        private set

    /** What the agent should actually use. Only Gemini has a build-time fallback key. */
    val effectiveApiKey: String
        get() = apiKey.ifBlank {
            if (provider == AgentProvider.GEMINI) BuildConfig.GEMINI_API_KEY else ""
        }

    val effectiveModel: String get() = model.ifBlank { provider.defaultModel }

    val hasKey: Boolean get() = effectiveApiKey.isNotBlank()

    /** True when no key was typed but the build supplied one — worth saying out loud in the UI. */
    val usingBuildKey: Boolean
        get() = provider == AgentProvider.GEMINI &&
            apiKey.isBlank() && BuildConfig.GEMINI_API_KEY.isNotBlank()

    /** Switches provider and loads that provider's saved key and model. */
    fun selectProvider(next: AgentProvider) {
        provider = next
        apiKey = prefs.getString(keyPref(next), "").orEmpty()
        model = prefs.getString(modelPref(next), "").orEmpty()
        prefs.edit().putString(KEY_PROVIDER, next.name).apply()
    }

    fun save(apiKey: String, model: String) {
        this.apiKey = apiKey.trim()
        this.model = model.trim()
        prefs.edit()
            .putString(keyPref(provider), this.apiKey)
            .putString(modelPref(provider), this.model)
            .putString(KEY_PROVIDER, provider.name)
            .apply()
    }

    /** Forgets this provider's stored key and model; a build-time key (if any) takes over. */
    fun clear() = save("", "")

    /** The agent the demos should talk to, built from what is stored right now. */
    fun newAgent(): A2uiAgent = when (provider) {
        AgentProvider.GEMINI -> GeminiAgent(effectiveApiKey, effectiveModel)
        AgentProvider.OPENAI -> OpenAiAgent(effectiveApiKey, effectiveModel)
        AgentProvider.ANTHROPIC -> AnthropicAgent(effectiveApiKey, effectiveModel)
    }

    companion object {
        private const val PREFS = "a2ui_agent_settings"
        private const val KEY_PROVIDER = "provider"

        // Gemini keeps the original names so an existing install does not lose its key.
        private fun keyPref(p: AgentProvider) =
            if (p == AgentProvider.GEMINI) "api_key" else "api_key_${p.name.lowercase()}"

        private fun modelPref(p: AgentProvider) =
            if (p == AgentProvider.GEMINI) "model" else "model_${p.name.lowercase()}"

        @Volatile private var instance: AgentSettings? = null

        fun getInstance(context: Context): AgentSettings =
            instance ?: synchronized(this) {
                instance ?: AgentSettings(
                    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}

/** One instance per process, so Settings and the demo screen always agree. */
@Composable
fun rememberAgentSettings(): AgentSettings {
    val context = LocalContext.current
    return remember { AgentSettings.getInstance(context) }
}
