package com.github.izulan.ollamacompletion.topics

import com.intellij.util.messages.Topic

interface OllamaStatusNotifier {
    fun onStart()
    fun onConnected()
    fun onComplete(totalDuration: Long)
    fun onCancel()
    fun onError(message: String)

    companion object {
        @Topic.AppLevel
        val TOPIC = Topic.create("Ollama status change", OllamaStatusNotifier::class.java)
    }
}