package com.github.izulan.ollamacompletion.settings

import com.intellij.openapi.components.BaseState

class OllamaSettingsState : BaseState() {
    var host by string("http://localhost:11434/")
    var selectedModel by string()
    var deleteLineTail by property(true)
    var topK by property(40)
    var topP by property(0.9f)
    var contextSize by property(2048)
    var temperature by property(0.8f)
    var systemPrompt by string("Complete the given code. Output only the generated suffix.")
}