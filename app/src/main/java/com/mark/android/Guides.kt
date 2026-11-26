package com.mark.android

/** Hints for popular apps, given to the agent only while that app is on screen. They can go out of date: the screen always wins. */
object Guides {
    private val g = listOf(
        "com.whatsapp" to "Chats list: magnifier (top right) searches chats; the round button (bottom right) starts a new chat. Inside a chat: the field says \"Message\"; the send arrow appears right of it only after text is typed. Voice-message mic shows when the field is empty (do not press it). Always confirm before Send.",
        "com.google.android.youtube" to "Magnifier at the top right opens search. After typing, press the keyboard's search key or tap a suggestion. Results are a vertical list: tap a title to play. Shorts are a separate tab; skip them unless asked.",
        "com.android.chrome" to "The address bar says \"Search or type web address\": tap it, type, then press Enter. The tab count box and the three-dot menu are at the top right. Cookie banners usually have an Accept / Reject button near the bottom.",
        "com.google.android.gm" to "Gmail: the \"Compose\" button is bottom right. In a draft the fields are To, Subject and \"Compose email\"; the send arrow is at the top right. Search is the top bar. Confirm before Send.",
        "com.google.android.apps.maps" to "Maps: the top bar \"Search here\" takes a place; then use the Directions button, choose the transport mode, and Start only when the user wants navigation to begin.",
        "com.google.android.apps.messaging" to "Messages: \"Start chat\" is the button bottom right; the text field says \"Text message\"; the send button is labelled Send SMS or Send. Confirm before Send.",
        "com.android.vending" to "Play Store: the profile picture at the top right -> Manage apps & device -> \"Updates available\" -> Update all. Install buttons say Install; never install anything the user did not ask for.",
        "com.android.settings" to "Settings has a search bar at the top: use it to jump to a page. Switches are checkable items; read their checked state on the screen before toggling, and check again after.",
        "com.google.android.deskclock" to "Clock: the Alarm tab has a + button; pick the time on the dial or keypad and press OK. Timers are on the Timer tab.",
        "com.google.android.calendar" to "Calendar: the + button bottom right creates an event; the title field is at the top, then date/time rows; Save is at the top right.",
        "com.instagram.android" to "Instagram: the bottom bar has Home, Search, Reels, Shop and Profile. Direct messages are the paper-plane icon at the top right. Confirm before sending or posting anything.",
        "org.telegram.messenger" to "Telegram: the magnifier at the top right searches chats; the message field says \"Message\"; the send arrow appears after typing. Confirm before Send."
    )
    fun forPackage(pkg: String): String = g.firstOrNull { pkg.startsWith(it.first) }?.second ?: ""
}
