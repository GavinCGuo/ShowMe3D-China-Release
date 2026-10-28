package com.example.showme3d.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.showme3d.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
