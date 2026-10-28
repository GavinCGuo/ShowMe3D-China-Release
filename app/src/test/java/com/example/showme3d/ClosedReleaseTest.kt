package com.example.showme3d

import com.example.showme3d.release.ReleasePolicy
import com.example.showme3d.studio.agent.DoubaoAgentClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosedReleaseTest {
    @Test
    fun productionAgentRejectsEvenWithSavedKeyBeforeConnecting() {
        val client = DoubaoAgentClient(
            apiKey = "previously_saved_key",
            endpoint = "http://127.0.0.1:1/must-not-connect",
        )
        val result = client.requestDecision("添加立方体", emptyMap(), emptyList())
        assertTrue(result.isFailure)
        assertEquals(ReleasePolicy.COMING_SOON, result.exceptionOrNull()?.message)
    }
}
