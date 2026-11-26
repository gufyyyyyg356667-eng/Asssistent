package com.mark.android

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var liveModel: EditText
    private lateinit var voice: EditText
    private lateinit var persona: EditText
    private lateinit var provUrl: EditText
    private lateinit var provModel: EditText
    private lateinit var provKey: EditText
    private lateinit var maxSteps: EditText
    private lateinit var dailyCap: EditText
    private lateinit var goal: EditText
    private lateinit var log: TextView
    private lateinit var wakeBtn: Button
    private lateinit var hudBtn: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        fun field(h: String, v: String, secret: Boolean = false, number: Boolean = false) = EditText(this).apply {
            hint = h; setText(v)
            inputType = when { secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                               number -> InputType.TYPE_CLASS_NUMBER else -> InputType.TYPE_CLASS_TEXT }
        }
        val p = Store.prefs
        key = field("Gemini API key", Store.key, secret = true)
        goal = EditText(this).apply { hint = "Type or speak a task, reminder or question…"; minLines = 2 }
        log = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 12f; text = Bus.all() }

        model = field("Text model (phone tasks, search, reports)", Store.textModel)
        liveModel = field("Live model (voice)", Store.liveModel)
        voice = field("Voice name", Store.voice)
        persona = field("Persona / name (optional, e.g. 'You are Jarvis, formal and brief')", Store.persona)
        provUrl = field("Fallback provider URL (OpenAI-compatible, e.g. http://192.168.1.5:11434/v1)", Store.providerUrl)
        provModel = field("Fallback provider model", Store.providerModel)
        provKey = field("Fallback provider key (optional)", Store.providerKey, secret = true)
        maxSteps = field("Max steps per phone task", Store.maxSteps.toString(), number = true)
        dailyCap = field("Daily AI call limit (0 = none)", Store.dailyCap.toString(), number = true)
        val more = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        listOf(model, liveModel, voice, persona, provUrl, provModel, provKey, maxSteps, dailyCap).forEach { more.addView(it) }

        wakeBtn = btn("") { toggleWake() }
        hudBtn = btn("") { toggleHud() }
        refreshButtons()

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(btn("🎤") { dictate() }); row1.addView(btn("Run") { run() }); row1.addView(btn("Stop") { AgentService.instance?.stop() })
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(btn("🗣 Live") { startVoice("LIVE") }); row2.addView(wakeBtn); row2.addView(btn("⏹ Voice") { startVoice("STOP") })

        col.addView(btn("1. Enable accessibility service") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        col.addView(key); col.addView(goal); col.addView(row1); col.addView(row2)
        col.addView(btn("Memory, routines, research…") { showStore() })
        col.addView(btn("🔔 Allow notification reading") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        col.addView(btn("🔋 Keep Mark alive in background") {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) })
        col.addView(hudBtn)
        col.addView(btn("⚙ More settings") { more.visibility = if (more.visibility == View.GONE) View.VISIBLE else View.GONE })
        col.addView(more)
        col.addView(ScrollView(this).apply { addView(log) })
        setContentView(col)

        Bus.listener = { line -> runOnUiThread { log.append("\n$line") } }
        handleIntent(intent)

        val need = mutableListOf<String>()
        fun ask(perm: String) { if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) need += perm }
        ask(Manifest.permission.RECORD_AUDIO); ask(Manifest.permission.READ_CONTACTS)
        ask(Manifest.permission.ANSWER_PHONE_CALLS); ask(Manifest.permission.CAMERA)
        ask(Manifest.permission.READ_CALENDAR); ask(Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) ask(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handleIntent(i) }
    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra("start_wake", false) == true) {
            Store.prefs.edit().putBoolean("wake", true).apply(); refreshButtons(); startVoice("WAKE_ON")
        }
    }

    private fun saveSettings() {
        Secure.put("key", key.text.toString()); Secure.put("provider_key", provKey.text.toString())
        Store.prefs.edit().putString("model", model.text.toString().trim())
            .putString("live_model", liveModel.text.toString().trim()).putString("voice", voice.text.toString().trim())
            .putString("persona", persona.text.toString().trim()).putString("provider_url", provUrl.text.toString().trim())
            .putString("provider_model", provModel.text.toString().trim())
            .putInt("daily_cap", dailyCap.text.toString().toIntOrNull()?.coerceIn(0, 100000) ?: 0)
            .putInt("max_steps", maxSteps.text.toString().toIntOrNull()?.coerceIn(5, 200) ?: 80).apply()
    }

    private fun run() {
        val svc = AgentService.instance
        if (svc == null) { toast("Enable the accessibility service first."); return }
        if (key.text.isBlank() || goal.text.isBlank()) { toast("Key and task are required."); return }
        saveSettings(); svc.start(goal.text.toString())
    }

    private fun startVoice(action: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); toast("Allow the microphone, then try again."); return
        }
        if (action != "STOP" && key.text.isBlank()) { toast("Set the API key first."); return }
        saveSettings()
        startForegroundService(Intent(this, VoiceService::class.java).setAction(action))
    }

    private fun toggleWake() {
        val on = !Store.prefs.getBoolean("wake", false)
        Store.prefs.edit().putBoolean("wake", on).apply()
        startVoice(if (on) "WAKE_ON" else "WAKE_OFF"); refreshButtons()
    }

    private fun toggleHud() {
        val on = !Store.prefs.getBoolean("hud", false)
        if (on && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            toast("Allow 'Display over other apps', then press the orb button again."); return
        }
        Store.prefs.edit().putBoolean("hud", on).apply(); refreshButtons()
    }

    private fun refreshButtons() {
        wakeBtn.text = if (Store.prefs.getBoolean("wake", false)) "👂 Wake: ON" else "👂 Wake: OFF"
        hudBtn.text = if (Store.prefs.getBoolean("hud", false)) "🟢 Status orb: ON" else "⚪ Status orb: OFF"
    }

    private fun showStore() {
        val tv = TextView(this).apply {
            setPadding(48, 24, 48, 24); textSize = 13f
            text = "MEMORY\n${Memory.block()}\n\nROUTINES\n" +
                (Routines.all().joinToString("\n") { "${it.id}  ${Routines.describe(it)}" }.ifBlank { "(none)" }) +
                "\n\nQUIET HOURS: ${Routines.quietText()}\nPROACTIVE: ${if (Proactive.enabled()) "on" else "off"}" +
                "\n\nPLUGINS\n${Plugins.catalog()}\n\nRESEARCH\n${Research.status()}\n\nUNDO\n${Undo.history()}"
        }
        AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear memory") { _, _ -> Memory.clear(); toast("Memory cleared.") }
            .setNegativeButton("Allow exact alarms") { _, _ ->
                if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                else toast("Already allowed.")
            }.show()
    }

    private fun dictate() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        try { startActivityForResult(i, 7) } catch (e: Exception) { toast("No speech recognizer found.") }
    }

    @Deprecated("simple and works everywhere")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 7 && res == RESULT_OK)
            goal.setText(data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: "")
    }

    override fun onDestroy() { Bus.listener = null; super.onDestroy() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
