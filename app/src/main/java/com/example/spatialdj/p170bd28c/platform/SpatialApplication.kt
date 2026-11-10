package com.example.spatialdj.p170bd28c.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.spatialdj.p170bd28c.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
