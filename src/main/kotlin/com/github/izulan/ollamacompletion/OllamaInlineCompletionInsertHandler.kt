package com.github.izulan.ollamacompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionInsertEnvironment
import com.intellij.codeInsight.inline.completion.InlineCompletionInsertHandler
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement

class OllamaInlineCompletionInsertHandler : InlineCompletionInsertHandler {
    override fun afterInsertion(
        environment: InlineCompletionInsertEnvironment,
        elements: List<InlineCompletionElement>
    ) {
        val skippedTextLength = elements.filterIsInstance<InlineCompletionSkipTextElement>().sumOf { it.text.length }
        val offset = environment.editor.caretModel.offset
        var end = offset + skippedTextLength
        while (end < environment.editor.document.charsSequence.length && environment.editor.document.charsSequence[end] != '\n') {
            ++end
        }
        environment.editor.document.deleteString(offset, end)
    }

    companion object {
        val INSTANCE = OllamaInlineCompletionInsertHandler()
    }
}