package com.github.izulan.ollamacompletion

import com.github.izulan.ollamacompletion.services.CompletionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Helper class for testing the completion provider.
 */
class CompletionServiceMock(private val cs: CoroutineScope) : CompletionService {
    @Volatile
    override var activePrompt: String? = null
    override val response = StringBuffer()

    @Volatile
    override var isResponseDone = false

    override fun startStream(model: String, prompt: String, incompleteCompletion: String): Job {
        return cs.launch {
            activePrompt = prompt
            isResponseDone = false
            response.setLength(0)
            response.append(incompleteCompletion)
            isResponseDone = streamGenerator(prompt + incompleteCompletion, response)
        }
    }

    companion object {
        var streamGenerator: suspend (String, StringBuffer) -> Boolean =
            { _: String, _: StringBuffer -> true }
    }
}