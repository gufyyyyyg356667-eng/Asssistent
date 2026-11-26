# Mark Android (v0.2)

Phone controls the phone. Gemini reads the screen through an Accessibility Service and picks one action per step.

## Features
- **Phone control**: open app, tap, type, scroll, back/home. Failed steps are reported back to the model (never rounded up to "done").
- **Real confirmation**: before send / pay / delete / post / install the app shows a Yes/No dialog the model cannot press.
- **Live voice**: Gemini Live over WebSocket, streaming mic in and voice out, interruptible, tool calls (phone_task, memory, routines).
- **Wake word**: "Hey Jarvis" / "Hey Mark" (also Bengali phrases). Foreground service, auto-sleeps after 2 min of silence.
- **Memory**: remember / recall / forget by voice or text; latest 40 facts go into every prompt, the rest is searchable.
- **Routines & reminders**: once / daily / weekly / every N minutes; "say" (notification + voice) or "do" (runs a phone task).
  Quiet hours: nothing unprompted speaks inside the window, an exact one-time reminder still does.

## Setup
1. Open in Android Studio, Sync, Run.
2. Allow microphone and notifications. In the app: "Enable accessibility service" -> turn on Mark.
3. Paste the Gemini API key. Live model default: models/gemini-3.1-flash-live-preview (same as Mark LVII), editable.
4. For exact reminder times: Memory & routines -> "Allow exact alarms".

## Examples (voice or typed)
- "Remind me every Friday at 17:00 to send the report"
- "Every morning at 8 open WhatsApp and read my unread chats"   (do-routine; the phone must be unlocked)
- "Don't talk to me between 23:00 and 07:00"
- "Remember that my sister's name is Rima"

## Known limits
- Wake word uses Android's speech recognizer, not an offline detector, so audio may go to Google's recognizer while idle and the phone may beep between listens. For a real offline detector swap `VoiceService.listenWake` for Picovoice Porcupine.
- Wake listening must be started from the app (Android does not let a microphone service start from the background); it does not survive a reboot. Routines do.
- "do" routines cannot operate a locked phone.
- Use headphones for Live voice; hardware echo cancellation helps but is not perfect.
- Not ported: avatar/HUD, Deep Research agent, undo, plugin system.

## v0.3 — Mark LVII's desktop tools, as Android tools
One registry (`Tools.kt`) feeds both the Live voice session and the typed agent, so every tool works by voice and by text.

| Mark LVII (PC) | Mark Android |
|---|---|
| web_search | web_search (Gemini + Google Search grounding, with source names) |
| weather_report | weather_report (Open-Meteo, 3 days, no key) |
| send_message | send_message (SMS / WhatsApp / Telegram / email: fills the composer, you confirm, then Send is tapped) |
| computer_settings | phone_settings (volume, brightness, media keys, flashlight, lock, screenshot, quick settings, settings pages) |
| screen_processor | screen_vision (screenshot -> Gemini; Android 11+) |
| system_monitor | system_status (battery, storage, RAM, network) |
| youtube_video | youtube (search/play via the app, summarize a public video URL) |
| browser_control | open_url (+ phone_task for anything inside the page) |
| flight_finder | flight_finder (opens Google Flights, read with screen_vision) |
| file_controller | files (the Mark folder only; delete asks you) |
| reminder / routines | set_alarm, set_timer, add_routine |
| computer_control / open_app | phone_task (accessibility: tap, type, scroll, open app) |

Honest differences: Android does not let apps flip Wi-Fi / Bluetooth / airplane mode directly or roam all storage,
so those open the right settings page and finish through phone_task with your confirmation.
Not yet: Deep Research + checkpointed background tasks, camera vision, code_helper, game_updater, avatar/HUD, undo, plugins.

## v0.4 — full hands-free control
**Hands (every touch a person can do):** tap, long-press, double-tap, swipe (any direction or coordinates), scroll (any direction, picks the right list),
drag, pinch in/out, type / clear / append (falls back to paste when an app refuses text), Enter, select all / copy / paste / cut,
Back / Home / Recents / notifications / quick settings / split screen / lock / power menu, open app (verified on screen).
**Eyes:** accessibility tree incl. views Android marks "not important", checkbox/switch state, element centres on a 0-1000 grid;
when an app has no usable tree (games, custom views) a screenshot is attached and the model taps by coordinates; `find_tap` finds things by looking.
**Big tasks:** up to 80 steps (Store.maxSteps), a running "notes" memory carried between steps, stuck detection (3 unchanged screens = change approach, 6 = stop),
retries on network errors, screen kept awake, plain swipe-lock dismissed, progress saved so "continue the last task" resumes after a crash.
**Voice, lying down:** phone_task runs in the background while you keep talking; say "stop" and it stops; the phone tells you when it is done.
Say "read my notifications", "answer the call", "scroll down", "open the second chat" etc. (read_screen / phone_act / call_control).
**Confirmation by voice, judged by code:** before Send / Pay / Delete / Post the phone speaks the question and listens;
only YOUR transcribed words (yes / no / হ্যাঁ / না ...) count, the model cannot answer for you. A tap on a button labelled like that is blocked until you confirmed.

## Android limits that no app can remove
- A phone locked with PIN / pattern / fingerprint cannot be unlocked by an app. Use Smart Lock (trusted place / on-body) so it stays unlocked.
- Sideloaded apps: Android 13+ greys out the Accessibility switch. App info -> ⋮ -> "Allow restricted settings", then enable Mark.
- After a reboot, starting the microphone from the background is blocked; Mark posts a notification, one tap re-arms the wake word.
- Banking/secure screens (FLAG_SECURE) block screenshots; the tree may be hidden there too.
- Turn on "Keep Mark alive" (battery optimisation) or some phones kill the listener.

## v0.5 — the rest of Mark LVII
| Mark LVII | Mark Android |
|---|---|
| Deep Research agent | `deep_research`: plan -> search each question -> cross-check doubtful claims -> report with sources in `reports/`. Saved after every step, resumes after a restart, waits out Gemini quota (HTTP 429) |
| background_tasks | the research job runs as a foreground service; `research_status`, `research_cancel`; phone tasks run in the background during voice |
| background_monitor | `watch_topic`: daily news check on topics YOU choose, alerts only on a new story |
| proactive engine | `proactive on`: max 3 nudges a day, never within 2 h of the last, never in quiet hours, silence is the default |
| camera / screen_processor | `camera_vision` (photo -> Gemini) and `screen_vision` |
| code_helper | `code_helper`: write / explain / fix files in `code/` (cannot run code on a phone) |
| undo | `undo`: volume, brightness, files (incl. delete, via a trash folder), memory, routines, quiet hours, plugins |
| plugin_loader | `add_plugin`: user-dictated recipes (task / url / search with {input}); no code is loaded |
| avatar / HUD | floating status orb: blue listening, amber working, green speaking, red asking you (never touchable) |
| hotkey (push-to-talk) | Quick Settings tile "Talk to Mark" |
| audio_devices | `audio_output`: speaker / earpiece / Bluetooth / headset |
| echo tail | 0.5 s mic mute after the assistant's voice ends, so it does not answer itself |
| llm ladder + providers | model ladder (falls back to flash-lite on quota/outage) + optional OpenAI-compatible fallback (Ollama on your PC, etc.) |
| file_processor | `files ask` on PDFs, images, text + "Share -> Send to Mark" inbox |
| video_player | `play_video` |
| game_updater | `app_updates` (Play Store, then Update all) |

Deliberately not ported: Python plugins, PC-only tools (desktop_control, dev_agent), the 3D avatar mesh and lip-sync, the cloud second body, running code on the phone.

## v0.6 — trust, reliability, calendar
- **API keys encrypted** with the hardware-backed Android Keystore (older plain-text keys are migrated on first start); auto-backup is off so keys never leave the phone.
- **Kill switch**: a STOP button on the ongoing notification while a task runs, and holding both volume keys for 1 s stops everything (task, research, voice, speech).
- **Action log** (`logs/` in the Mark folder, 14 days; typed text is never written) + tool `action_log` ("what did you just do?").
- **Daily AI-call limit** and `usage_today`, so a stuck loop cannot burn your quota.
- **App guides** for WhatsApp, YouTube, Chrome, Gmail, Maps, Messages, Play Store, Settings, Clock, Calendar, Instagram, Telegram; shown only while that app is open.
- **Learned shortcuts**: finished tasks leave a text-free note of the steps that worked; similar tasks start with that hint (`clear_recipes` forgets them).
- **Calendar**: `calendar_list` and `calendar_add` (with a 10-minute reminder, verified by reading it back, undoable).
- **Build from your phone**: see BUILD_FROM_PHONE.md (GitHub Actions builds the APK).
