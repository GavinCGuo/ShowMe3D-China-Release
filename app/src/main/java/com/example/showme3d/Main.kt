package com.example.showme3d

import com.example.showme3d.content.StudioStage
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) = with(scope) {
    DefaultStage { PicoTheme { StudioStage() } }
}

