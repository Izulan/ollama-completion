package com.github.izulan.ollamacompletion.services

import kotlinx.coroutines.Job

interface CompletionService {
    val activePrompt: String?
    val response: StringBuffer
    val isResponseDone: Boolean
    fun startStream(model: String, prompt: String, incompleteCompletion: String): Job
}