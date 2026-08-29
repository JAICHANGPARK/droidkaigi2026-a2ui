package com.example.a2uicomposelabs.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.util.Log
import com.example.a2uicomposelabs.a2ui.A2uiAction
import com.example.a2uicomposelabs.a2ui.A2uiBooleanSchema
import com.example.a2uicomposelabs.a2ui.A2uiCatalog
import com.example.a2uicomposelabs.a2ui.A2uiClient
import com.example.a2uicomposelabs.a2ui.A2uiComponentDefinition
import com.example.a2uicomposelabs.a2ui.A2uiComponentFactory
import com.example.a2uicomposelabs.a2ui.A2uiCheckSeverity
import com.example.a2uicomposelabs.a2ui.A2uiSurface
import com.example.a2uicomposelabs.a2ui.BindingScope
import com.example.a2uicomposelabs.a2ui.BasicCatalog
import com.example.a2uicomposelabs.a2ui.BasicCatalogSchema
import com.example.a2uicomposelabs.a2ui.ComponentRegistry
import com.example.a2uicomposelabs.a2ui.childList
import com.example.a2uicomposelabs.a2ui.componentSchema
import com.example.a2uicomposelabs.a2ui.dynamicNumber
import com.example.a2uicomposelabs.a2ui.dynamicString
import com.example.a2uicomposelabs.a2ui.replayAsset
import com.example.a2uicomposelabs.a2ui.twoWay
import com.example.a2uicomposelabs.agent.AgentChunk
import com.example.a2uicomposelabs.agent.a2uiSystemPrompt
import com.example.a2uicomposelabs.agent.rememberAgentSettings
import com.example.a2uicomposelabs.forms.AnsweredQuestion
import com.example.a2uicomposelabs.forms.SavedForm
import com.example.a2uicomposelabs.forms.collectAnswers
import com.example.a2uicomposelabs.forms.rememberSavedFormStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Demo 5 — the form nobody had to build.
 *
 * A survey is the case A2UI is best at: the *structure* changes on every request, so there is
 * no template to reuse. Writing a Compose screen per questionnaire does not scale; describing
 * one in a prompt does.
 *
 * Two things are worth watching here.
 *
 * First, the catalog. Sixteen of the eighteen basic components already cover a survey —
 * TextField for short answers, ChoicePicker for radio and multi-select, Slider for ranges,
 * DateTimeInput for dates, CheckBox for consent. Only the star rating was missing, so the app
 * adds exactly one component ([StarRatingComponent]) and the agent can use it from then on.
 * That is the whole cost of extending the vocabulary.
 *
 * Second, the artifact. A generated survey is five lines of JSONL — and those five lines *are*
 * the form definition. Generate once with a model, save the lines, replay them for every
 * respondent: no model in the loop, no network, and the same form every time. The three
 * presets below are exactly that: saved output from earlier generations.
 */

// ---------------------------------------------------------------------------
// The catalog: the basic eighteen, plus one component this app decided to own.
// ---------------------------------------------------------------------------

private const val SURVEY_CATALOG_ID = "app.survey.catalog/v1"

/**
 * Every saved preset uses this surface ID, so live generations are told to use it too — that
 * is what makes a generated form interchangeable with a recorded one.
 */
private const val SURVEY_SURFACE_ID = "survey"

internal val SurveyCatalogSchema: A2uiCatalog =
  BasicCatalogSchema.withId(SURVEY_CATALOG_ID) + listOf(
    A2uiComponentDefinition(
      name = "Question",
      description =
        "One question in the form: the question text, with its answer control directly " +
          "below it. EVERY question must be wrapped in one of these, and the answer control " +
          "goes in children. This is what makes the form readable as a questionnaire.",
      propertySchema = componentSchema(
        properties = mapOf(
          "text" to dynamicString("The question, written out in full, as the respondent reads it."),
          "required" to A2uiBooleanSchema("Marks the question with an asterisk."),
          "children" to childList("The answer control for this question, usually exactly one id."),
        ),
        required = setOf("text", "children"),
      ),
    ),
    A2uiComponentDefinition(
      name = "StarRating",
      description =
        "A row of tappable stars for rating something from 1 to max. Prefer this over a " +
          "Slider for satisfaction and quality questions; use a Slider for quantities.",
      propertySchema = componentSchema(
        properties = mapOf(
          "label" to dynamicString("The question shown above the stars."),
          "max" to dynamicNumber("How many stars to show. Defaults to 5."),
          "value" to twoWay("Bound path holding the number of stars chosen. 0 means unanswered."),
        ),
        required = setOf("value"),
      ),
    ),
  )

/** The other half of the catalog: code the app wrote, so the agent may only ask for it. */
internal val SurveyCatalog: Map<String, A2uiComponentFactory> = mapOf(

  // A questionnaire reads as a questionnaire because each question owns a card, states its
  // question at full size, and puts the answer control underneath it.
  "Question" to { node, scope, renderChild ->
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
          Text(
            scope.readString(node.props["text"]),
            style = MaterialTheme.typography.titleSmall,
          )
          if (scope.readBoolean(node.props["required"])) {
            Text(
              " *",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        scope.children(node.props).forEach { renderChild(it) }
      }
    }
  },

  "StarRating" to { node, scope, _ ->
    // Agent-controlled numbers, so clamp before they reach a loop bound.
    val max = scope.readFloat(node.props["max"], 5f).toInt().coerceIn(1, 10)
    val chosen = scope.readFloat(node.props["value"], 0f).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      val label = scope.readString(node.props["label"])
      if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium)
      Row {
        for (star in 1..max) {
          val filled = star <= chosen
          IconButton(
            // Tapping the current rating clears it, so a question stays answerable-with-nothing.
            onClick = {
              val next = if (star == chosen) 0 else star
              scope.write(node.props["value"], JsonPrimitive(next))
            }
          ) {
            Icon(
              imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
              contentDescription = "$star of $max",
              tint =
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
              modifier = Modifier.size(28.dp),
            )
          }
        }
      }
    }
  },
)

// ---------------------------------------------------------------------------
// Saved generations. Each file is one model response, kept verbatim.
// ---------------------------------------------------------------------------

private data class SurveyPreset(val chip: String, val prompt: String, val asset: String)

private val Presets = listOf(
  SurveyPreset(
    chip = "Café feedback",
    prompt = "A short café satisfaction survey: star ratings for the visit and the drinks, " +
      "whether they would come back, and a free-text box for improvements.",
    asset = "survey_cafe.jsonl",
  ),
  SurveyPreset(
    chip = "Event sign-up",
    prompt = "A sign-up form for the DroidKaigi after-party: name, email, whether they are " +
      "coming, which tracks they care about, arrival time, and a consent checkbox.",
    asset = "survey_event.jsonl",
  ),
  SurveyPreset(
    chip = "Onboarding",
    prompt = "An onboarding questionnaire for a fitness app: what they want to use it for, " +
      "how many years they have trained, first impression, and one wish.",
    asset = "survey_onboarding.jsonl",
  ),
)


/**
 * What a survey actually looks like, told to the model in its own words. The generic prompt
 * teaches the protocol; this teaches the shape of this particular screen — a list of
 * questions, each one component, each bound to its own answer in the data model.
 */
private val SURVEY_RULES = """
# Survey shape

A survey reads like a paper questionnaire: each question is written out in full, and its
answer control sits directly underneath it. Build it in exactly this order:

1. `createSurface` — root is a **Column**. Its `children` lists exactly three kinds of id,
   in this order: the title, one id per `Question`, and the submit button. NOTHING ELSE.
   An answer control is never listed in root; it is reached only through the `children` of
   its own `Question`. Listing it in both places draws it twice.
   Example for a three-question form:
   `"children":["title","q1","q2","q3","submit"]` — and NOT `"a1"`, `"a2"`, `"a3"`.
   Ids that have not arrived yet render as nothing, so the form fills in as you go. In the
   same message put a `dataModel` with one entry per answer, pre-filled empty: "" for text,
   false for a checkbox, 0 for a rating or number, [] for a multi-select.
2. A `Text` holding the form's title.
3. Then, for each question, TWO messages in this order:
   a. a `Question` carrying the full question text, `required` set to true or false, and
      `children` holding the single id of its answer control — this id belongs here and
      nowhere else;
   b. the answer control itself, with NO `label` at all (except CheckBox, whose label is the
      option wording) — the `Question` above already asks the question, and a second copy of
      it inside the control just repeats it.
   When the question is `"required":true`, give its answer control a matching check. The
   submit button disables itself while any check fails, so this is what makes "required"
   actually mean something. Pick the condition by what "unanswered" looks like for that
   control:
   - text, or a single choice stored as a string → `required`, e.g.
     `{"call":"required","args":{"value":{"path":"/theAnswer"}}}`
   - a multi-select stored as an array → `required` as well; an empty array counts as absent
   - **StarRating, Slider, and anything numeric → `numeric` with `"min":1`**, e.g.
     `{"call":"numeric","args":{"value":{"path":"/rating"},"min":1,"max":5}}`.
     `required` is WRONG here: an unanswered rating is the number 0, and 0 is a present
     value, so `required` would pass and the button would never disable.
   - **a CheckBox that must be ticked → the binding itself is the condition**, e.g.
     `{"condition":{"path":"/agreed"},"message":"Please accept to continue"}`. `required` is
     wrong here too: `false` is a present value.
   Add `email` or `length` checks on top where the answer has a shape to satisfy. Write the
   `message` as an instruction the respondent can act on.
   Pick the control that fits the answer:
   - satisfaction or quality, 1-5 → StarRating
   - the answer is exactly ONE of several named alternatives → ChoicePicker with
     `"variant":"mutuallyExclusive"` and one `options` entry per alternative. This is the
     default for most questions: yes/no, how often, which plan, age band, how they heard
     about you. Write the alternatives out — a yes/no question gets
     `"options":[{"label":"Yes","value":"yes"},{"label":"No","value":"no"}]`.
   - the answer may be several of a set → ChoicePicker with `"variant":"multipleSelection"`
   - a single opt-in or consent, where there is nothing to choose between ("I agree to the
     code of conduct") → CheckBox, whose `label` is the thing being agreed to
   - free text → TextField
   - a quantity or a year → Slider with min and max
   - a date or a time → DateTimeInput
   Leave `displayStyle` out. The default draws radio buttons for a single choice and
   checkboxes for a multiple one, which is what a questionnaire should look like; only set
   `"displayStyle":"chips"` for a multiple-choice question whose options are short tags.
   Vary the controls: a form where every question is the same control is a bad form.
4. Last, the submit `Button` with `"action":{"name":"submitSurvey","context":{...}}` binding
   every answer path, keyed by the same names used in `dataModel`.

Never put a bare answer control straight into root — every one belongs inside a `Question`.

Mark a question `"required":true` when the form is useless without it, and false when it is
a nice-to-have. Most forms have a mix: make roughly the important half required and leave
free-text and demographic questions optional. Never mark every question required.

Ask 3 to 6 questions. Every answer property is a binding, never a literal.
"""

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SurveyDemo(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val settings = rememberAgentSettings()
  val agent = remember(settings.effectiveApiKey, settings.effectiveModel) {
    settings.newAgent()
  }
  // The same catalog object twice: it becomes Gemini's system prompt, and it is what the
  // client validates every incoming message against.
  val client = remember { A2uiClient(SurveyCatalogSchema) }
  val registry = remember { ComponentRegistry(BasicCatalog) + SurveyCatalog }
  val systemPrompt =
    remember { a2uiSystemPrompt(SurveyCatalogSchema, SURVEY_SURFACE_ID, SURVEY_RULES) }
  val coroutineScope = rememberCoroutineScope()

  var preset by remember { mutableStateOf(Presets.first()) }
  var prompt by remember { mutableStateOf(Presets.first().prompt) }
  var preferLive by remember { mutableStateOf(false) }
  var running by remember { mutableStateOf(false) }
  var generated by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  var job by remember { mutableStateOf<Job?>(null) }
  // Kept so a failed generation can be copied out verbatim and read.
  var rawOutput by remember { mutableStateOf("") }
  // The lines that built the form currently on screen — this is what "saving a form" saves.
  val definition = remember { mutableStateListOf<String>() }
  var savedNotice by remember { mutableStateOf<String?>(null) }
  var missingRequired by remember { mutableStateOf<List<String>>(emptyList()) }
  var submission by remember { mutableStateOf<Submission?>(null) }

  val store = rememberSavedFormStore()
  val live = preferLive && agent.isConfigured
  val lineCount = definition.size

  fun reset() {
    job?.cancel()
    client.surfaces.clear()
    client.errors.clear()
    definition.clear()
    rawOutput = ""
    failure = null
    savedNotice = null
    missingRequired = emptyList()
    submission = null
    generated = false
  }

  // Applies one message and returns the reason the renderer refused it, or null. That reason
  // is handed straight back to the model as the tool's result, so it can rewrite the screen.
  fun accept(line: String): String? {
    val before = client.errors.size
    client.apply(line)
    definition += line
    return client.errors.drop(before).firstOrNull()?.toString()
  }

  fun generate() {
    reset()
    running = true
    job = coroutineScope.launch {
      try {
        if (live) {
          val seen = StringBuilder()
          agent.streamUi(
            systemPrompt = systemPrompt,
            userPrompt = "Build this form: $prompt",
            surfaceId = SURVEY_SURFACE_ID,
            applyUi = { json -> accept(json) },
          ).collect { chunk ->
            // Only for the "what did the model actually say" panel; nothing parses this.
            when (chunk) {
              is AgentChunk.Prose -> seen.append(chunk.text)
              is AgentChunk.Ui -> seen.append(chunk.json).append('\n')
            }
            rawOutput = seen.toString()
          }
          Log.d("SurveyDemo", "raw model output:\n$rawOutput")
        } else {
          // Replaying a saved generation — this is the "form definition" path.
          client.replayAsset(context, preset.asset, lineDelayMs = 450L, onLine = ::accept)
        }
        generated = true
      } catch (e: Exception) {
        failure = e.message ?: e::class.simpleName
      } finally {
        running = false
      }
    }
  }

  /** Replays a form saved earlier — no model, no network, byte-identical every time. */
  fun replaySaved(saved: SavedForm) {
    reset()
    prompt = saved.prompt
    running = true
    job = coroutineScope.launch {
      try {
        saved.lines.forEach { line ->
          delay(120L)
          accept(line)
        }
        generated = true
      } finally {
        running = false
      }
    }
  }

  /**
   * The submit button's action. Required questions are checked here, in the app — the agent
   * only said which ones matter; whether the form may be submitted is never its call.
   */
  fun submit(action: A2uiAction) {
    val surface = client.surfaces[SURVEY_SURFACE_ID] ?: client.surfaces.values.firstOrNull()
    val answers = surface?.collectAnswers().orEmpty()
    val missing = answers.filter { it.required && !it.answered }.map { it.question }
    missingRequired = missing
    if (missing.isEmpty()) submission = Submission(answers, prettyJson(action.toJson()))
  }

  submission?.let { done ->
    SubmittedPage(
      submission = done,
      onBackToForm = { submission = null },
      onNewForm = ::reset,
      modifier = modifier,
    )
    return
  }

  Column(
    modifier =
      modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Survey generator", style = MaterialTheme.typography.titleLarge)
    Text(
      "Describe the form you want. The agent answers in A2UI messages, and the renderer " +
        "builds the questions as they arrive — no screen was written for this survey.",
      style = MaterialTheme.typography.bodySmall,
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Presets.forEach { candidate ->
        AssistChip(
          onClick = {
            preset = candidate
            prompt = candidate.prompt
          },
          label = { Text(candidate.chip) },
        )
      }
    }

    OutlinedTextField(
      value = prompt,
      onValueChange = { prompt = it },
      label = { Text("What should the form ask?") },
      minLines = 2,
      modifier = Modifier.fillMaxWidth(),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(checked = preferLive, onCheckedChange = { preferLive = it }, enabled = agent.isConfigured)
      Text(
        if (agent.isConfigured) {
          if (preferLive) "  Generating with Gemini" else "  Replaying a saved generation"
        } else {
          "  Replaying a saved generation (add an API key in Settings for live)"
        },
        style = MaterialTheme.typography.bodySmall,
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Button(onClick = ::generate, enabled = !running) { Text(if (generated) "Regenerate" else "Generate form") }
      if (generated && !running) {
        OutlinedButton(onClick = ::generate) { Text("Replay") }
      }
      if (running) CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }

    if (lineCount > 0) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Form definition: $lineCount JSONL ${if (lineCount == 1) "line" else "lines"}",
          style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.weight(1f),
        )
        if (generated) {
          // Saving the lines saves the form. Nothing else needs to be kept.
          OutlinedButton(
            onClick = {
              val saved = store.save(
                prompt = prompt,
                source = if (live) "Gemini · ${settings.effectiveModel}" else "Recorded preset",
                lines = definition.toList(),
              )
              savedNotice = "Saved ${saved.createdAtLabel} — replays with no model."
            }
          ) { Text("Save form") }
        }
      }
    }

    savedNotice?.let { Text(it, style = MaterialTheme.typography.labelSmall) }

    if (store.forms.isNotEmpty()) {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Saved forms (${store.forms.size})", style = MaterialTheme.typography.labelMedium)
          Text(
            "Kept on the device. Each one is the prompt that asked for it plus the JSONL it " +
              "produced, so it can be reopened, filled in, or asked for again later.",
            style = MaterialTheme.typography.labelSmall,
          )
          store.forms.forEach { saved ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(saved.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2)
              Text(
                "${saved.createdAtLabel} · ${saved.source} · ${saved.lines.size} lines",
                style = MaterialTheme.typography.labelSmall,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { replaySaved(saved) }) { Text("Open") }
                TextButton(onClick = { store.delete(saved.id) }) { Text("Delete") }
              }
            }
          }
        }
      }
    }

    failure?.let { message ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Generation failed: $message",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )
        val clipboard = LocalClipboardManager.current
        TextButton(onClick = { clipboard.setText(AnnotatedString(message)) }) { Text("Copy") }
      }
    }

    // Rejected messages are the interesting failure: the model invented something and the
    // catalog refused it. Nothing invalid ever reached the surface below.
    RejectionReport(client.errors, rawOutput = rawOutput)

    // Prefer the agreed ID, but render whatever the agent actually created: a model that
    // ignores the instruction should still show its form rather than a blank screen.
    val surface = client.surfaces[SURVEY_SURFACE_ID] ?: client.surfaces.values.firstOrNull()

    // What the surface's own checks say right now. The submit button is already disabled by
    // these; this just tells the respondent why, instead of leaving a dead button on screen.
    val outstanding = surface
      ?.let { BindingScope(it, {}, evaluator = SurveyCatalogSchema.evaluator).surfaceCheckFailures() }
      .orEmpty()
    val blocking = outstanding.filter { it.severity == A2uiCheckSeverity.ERROR }.map { it.message }
    // Forms generated before checks existed have none, so fall back to the app's own pass.
    val pending = blocking.ifEmpty { missingRequired }

    if (pending.isNotEmpty()) {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            "Answer these first",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
          )
          pending.distinct().forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
    outstanding.filter { it.severity == A2uiCheckSeverity.WARNING }.forEach { warning ->
      Text("⚠ ${warning.message}", style = MaterialTheme.typography.bodySmall)
    }
    when (surface) {
      null ->
        Text(
          if (running) "waiting for the agent…" else "Press Generate to build a form.",
          style = MaterialTheme.typography.bodyMedium,
        )
      else ->
        A2uiSurface(
          state = surface,
          registry = registry,
          onAction = ::submit,
          catalog = SurveyCatalogSchema,
        )
    }
  }
}

/** What the respondent submitted: the readable answers, and the message sent to the agent. */
private data class Submission(val answers: List<AnsweredQuestion>, val actionJson: String)

/**
 * The page after submit. The answers were never posted anywhere — the renderer resolved every
 * bound path into the action's `context` and handed it to the app, which is exactly as far as
 * an agent-generated form gets to reach on its own.
 */
@Composable
private fun SubmittedPage(
  submission: Submission,
  onBackToForm: () -> Unit,
  onNewForm: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Submitted", style = MaterialTheme.typography.headlineSmall)
    Text(
      "These are the values the renderer resolved and handed to the app. Nothing left the " +
        "device: an action is a message to your code, not a network request.",
      style = MaterialTheme.typography.bodySmall,
    )

    submission.answers.forEach { answer ->
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row {
            Text(answer.question, style = MaterialTheme.typography.labelMedium)
            if (answer.required) {
              Text(
                " *",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }
          Text(
            answer.answer.ifBlank { "— not answered" },
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onBackToForm) { Text("Back to form") }
      OutlinedButton(onClick = onNewForm) { Text("New form") }
    }

    Text("action → agent", style = MaterialTheme.typography.labelMedium)
    Card(Modifier.fillMaxWidth()) {
      Text(
        submission.actionJson,
        modifier = Modifier.padding(12.dp),
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}
