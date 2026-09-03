package com.example.a2uicomposelabs.demos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.a2uicomposelabs.a2ui.model.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.runtime.A2uiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One line of a transcript. Shared by every demo that holds a conversation. */
sealed interface ChatTurn {
    data class User(val text: String) : ChatTurn
    data class Assistant(val text: String, val copyText: String = text) : ChatTurn
    data class Ui(val surfaceId: String) : ChatTurn
}

/**
 * One conversation, and everything it owns.
 *
 * The demos used to keep this in `remember`, which meant a surface lived exactly as long as the
 * screen showing it. That is fine for a form and wrong for anything that keeps moving: a
 * delivery in progress has to survive the user wandering off to another demo and coming back.
 *
 * So the session holds the [A2uiClient] — and therefore the surfaces and their data models —
 * plus its own background work. Nothing here is a snapshot: the surfaces are the live ones, so
 * returning to an old conversation shows whatever state it has reached since.
 */
class ChatSession internal constructor(
    val id: String,
    catalog: A2uiCatalog,
    /** Outlives the screen: work started here keeps running while the session is off-screen. */
    val scope: CoroutineScope,
) {
    val client = A2uiClient(catalog)
    val transcript = mutableStateListOf<ChatTurn>()
    val wire = mutableStateListOf<String>()

    var turnCount by mutableIntStateOf(0)
        internal set

    /** Taken from the first thing the user said, so the switcher reads like a chat app. */
    var title by mutableStateOf("New chat")
        private set

    /** Draft text and in-flight state, kept here so switching away does not lose them. */
    var input by mutableStateOf("")
    var running by mutableStateOf(false)

    /** The agent turn currently streaming, if any. */
    internal var turnJob: Job? = null

    /** Long-lived work keyed by what it is doing — a delivery ticker, a device monitor. */
    private val background = mutableMapOf<String, Job>()

    fun say(text: String) = transcript.add(ChatTurn.Assistant(text))

    fun nextSurfaceId(): String {
        turnCount += 1
        return "turn$turnCount"
    }

    fun rename(prompt: String) {
        if (title == "New chat") title = prompt.take(48)
    }

    /** Starts [work] under [key], replacing whatever was running under that key. */
    fun launchBackground(key: String, work: suspend CoroutineScope.() -> Unit) {
        background.remove(key)?.cancel()
        background[key] = scope.launch { work() }
    }

    fun stopBackground(key: String) {
        background.remove(key)?.cancel()
    }

    internal fun dispose() {
        turnJob?.cancel()
        background.values.forEach(Job::cancel)
        background.clear()
    }
}

/**
 * Every conversation one demo screen has held, newest last.
 *
 * A plain in-memory store, deliberately: it has to survive navigation, not process death.
 * Persisting surfaces across a cold start would mean serialising live component trees, which is
 * a different exercise from the one this app is demonstrating.
 */
class ChatSessionStore(private val catalog: A2uiCatalog) {

    // Lazy on purpose: these stores are top-level values, and touching Dispatchers.Main while
    // a class initialises would take the whole file down in a plain JVM unit test.
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val sessions = mutableStateListOf<ChatSession>()

    var currentId by mutableStateOf<String?>(null)
        private set

    /** The open conversation, creating the first one on demand. */
    fun current(): ChatSession =
        sessions.firstOrNull { it.id == currentId } ?: newSession()

    fun newSession(): ChatSession {
        val session = ChatSession("s${sessions.size + 1}", catalog, scope)
        sessions += session
        currentId = session.id
        return session
    }

    fun select(id: String) {
        if (sessions.any { it.id == id }) currentId = id
    }

    fun delete(id: String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        sessions.removeAt(index).dispose()
        if (currentId == id) currentId = sessions.lastOrNull()?.id
    }
}
