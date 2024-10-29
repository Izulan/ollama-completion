package com.github.izulan.ollamacompletion

import com.github.izulan.ollamacompletion.services.CompletionService
import com.intellij.codeInsight.inline.completion.*
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.github.izulan.ollamacompletion.settings.OllamaSettings
import kotlin.math.abs

class OllamaInlineCompletionProvider : InlineCompletionProvider {
    override val id: InlineCompletionProviderID
        get() = InlineCompletionProviderID("com.github.izulan.ollamacompletion.OllamaInlineCompletionProvider")

    private val completionService = service<CompletionService>()
    private val settings get() = service<OllamaSettings>()
    private var activeRequest: InlineCompletionRequest? = null
    private var activeApiJob: Job? = null
    private val mutex = Mutex()
    private var cache = hashMapOf<Document, CompletionTrie>()

    override val insertHandler: InlineCompletionInsertHandler
        get() = if (settings.state.deleteLineTail)
            OllamaInlineCompletionInsertHandler.INSTANCE
        else DefaultInlineCompletionInsertHandler.INSTANCE


    override fun isEnabled(event: InlineCompletionEvent): Boolean = true

    private fun shouldLetFinish(prompt: String): Boolean {
        val activePrompt = completionService.activePrompt ?: return false

        // In many programming languages I often return to special points in the program.
        // We deem these completions 'higher value' and allow them extra time to complete
        val isJunction = activePrompt.endsWith('.')
                || activePrompt.endsWith('\n')
                || activePrompt.endsWith('=')

        return (prompt.startsWith(activePrompt) &&
                completionService.response.startsWith(prompt.substring(activePrompt.length))
                ) || (isJunction && (abs(activePrompt.length - prompt.length) < 10))
    }

    private fun putCacheCompletion(document: Document) {
        if (completionService.activePrompt == null) return

        cache.getOrPut(document) { CompletionTrie() }
            .insert(
                completionService.activePrompt!!,
                completionService.response.toString(),
                completionService.isResponseDone
            )
    }

    private fun getCacheCompletion(document: Document, prefix: String): CompletionTrie.CompletionResult? {
        return cache[document]?.getCompletion(prefix)
    }

    override suspend fun getSuggestion(
        request: InlineCompletionRequest,
    ): InlineCompletionSingleSuggestion = InlineCompletionSingleSuggestion.build {
        val filePrefix = readActionBlocking {
            request.document.getText(TextRange(0, request.editor.caretModel.primaryCaret.offset))
        }
        val ignoreResult = activeRequest?.document != request.document
        var incompleteCompletion = ""

        // Mutex necessary as the completion trie is not thread-safe at all
        mutex.withLock {
            // Cache-hits leave the provider unaffected
            // Allow previous request to continue processing (the API will discard the result anyway)
            getCacheCompletion(request.document, filePrefix)?.let {
                if (it.isComplete) {
                    emit(InlineCompletionGrayTextElement(it.completion))
                    return@build
                } else {
                    // If an incomplete completion exists use that as an elevated starting point
                    // for the completion generation
                    incompleteCompletion = it.completion
                }
            }

            activeRequest = request

            // Requests of differing documents always get cancelled
            if ((!completionService.isResponseDone && !shouldLetFinish(filePrefix))
                || ignoreResult
            ) {
                activeApiJob?.cancel()
            }
        }

        activeApiJob?.join()

        mutex.withLock {
            // Should never be needed as cancellation should already end this in the join
            // Keep it as a safety net due to experimental API
            if (request != activeRequest) {
                // Another request is handling things now
                return@build
            }

            if (activeApiJob != null && !ignoreResult) {
                // Cache the complete or incomplete result from that previous request
                putCacheCompletion(request.document)
                activeApiJob = null
                // Maybe that new completion is already fitting
                getCacheCompletion(request.document, filePrefix)?.let {
                    if (it.isComplete) {
                        emit(InlineCompletionGrayTextElement(it.completion))
                        return@build
                    } else {
                        incompleteCompletion = it.completion
                    }
                }
            }

            // It is now our turn now to generate a response
            activeApiJob = completionService.startStream(
                settings.state.selectedModel ?: "",
                filePrefix,
                incompleteCompletion
            )
        }

        activeApiJob?.join()

        mutex.withLock {
            if (request != activeRequest) {
                // Another request is handling the request
                return@build
            }

            putCacheCompletion(request.document)
            activeApiJob = null
            getCacheCompletion(request.document, filePrefix)?.let {
                if (it.isComplete) {
                    emit(InlineCompletionGrayTextElement(it.completion))
                }
            }
        }
    }

}