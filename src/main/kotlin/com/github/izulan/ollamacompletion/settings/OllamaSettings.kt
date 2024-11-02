package com.github.izulan.ollamacompletion.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persistent settings state with some UI-required compatibility properties.
 */
@Service(Service.Level.APP)
@State(
    name = "com.github.izulan.ollamacompletion.settings.OllamaSettings",
    storages = [Storage("OllamaCompletion.xml")]
)
class OllamaSettings : SimplePersistentStateComponent<OllamaSettingsState>(OllamaSettingsState()) {
    var host
        get() = state.host ?: ""
        set(value) {
            state.host = value
        }

    var systemPrompt
        get() = state.systemPrompt ?: ""
        set(value) {
            state.systemPrompt = value
        }

    var topP: Double
        get() = (state.topP.toDouble())
        set(value) {
            state.topP = value.toFloat()
        }

    var temperature: Double
        get() = (state.temperature.toDouble())
        set(value) {
            state.temperature = value.toFloat()
        }
}