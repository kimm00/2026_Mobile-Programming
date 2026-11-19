package com.example.focustrace.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FocusTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
