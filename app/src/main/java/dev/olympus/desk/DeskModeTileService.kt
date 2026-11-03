package dev.olympus.desk

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class DeskModeTileService : TileService() {
    private val settings get() = getSharedPreferences("home-v3", MODE_PRIVATE)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        settings.edit().putBoolean("deskMode", !settings.getBoolean("deskMode", false)).apply()
        refresh()
    }

    private fun refresh() {
        val enabled = settings.getBoolean("deskMode", false)
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (enabled) "On" else "Off"
            updateTile()
        }
    }
}
