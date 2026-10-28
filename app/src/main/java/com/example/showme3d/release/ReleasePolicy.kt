package com.example.showme3d.release

/** Distribution policy for the closed Chinese release. */
object ReleasePolicy {
    const val CLOUD_AI_ENABLED = false
    const val COMING_SOON = "该功能即将开放"

    fun requireCloudAi() {
        check(CLOUD_AI_ENABLED) { COMING_SOON }
    }
}
