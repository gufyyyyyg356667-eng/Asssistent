package com.mark.android

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Push-to-talk from anywhere: pull down the quick settings and tap "Talk to Mark". */
class TalkTile : TileService() {
    override fun onStartListening() { qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Talk to Mark"; updateTile() } }
    override fun onClick() {
        try { startForegroundService(Intent(this, VoiceService::class.java).setAction("LIVE")) }
        catch (e: Exception) { Bus.log("Tile could not start voice: ${e.message}") }
    }
}
