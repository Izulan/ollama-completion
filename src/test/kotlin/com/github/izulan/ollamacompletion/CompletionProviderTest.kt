package com.github.izulan.ollamacompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.testInlineCompletion
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.AssertionFailedError
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import com.github.izulan.ollamacompletion.settings.OllamaSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(JUnit4::class)
@Suppress("UnstableApiUsage")
class CompletionProviderTest : BasePlatformTestCase() {
    override fun runInDispatchThread(): Boolean {
        return false
    }

    private fun register(deleteLineTail: Boolean) {
        InlineCompletionHandler.registerTestHandler(OllamaInlineCompletionProvider())
        service<OllamaSettings>().state.deleteLineTail = deleteLineTail
    }

    /**
     * Test that the tail deletion actually deletes the tail.
     */
    @Test
    fun testDeleteTailNoBreak() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>tail")
        register(true)

        CompletionServiceMock.streamGenerator = { _, res ->
            res.append("test")
            true
        }
        typeChar('n')
        delay()
        assertInlineRender("test")
        insert()
        assertFileContent("funtest<caret>")
    }

    /**
     * Test that the tail deletion does not touch the next line.
     */
    @Test
    fun testDeleteTailBreak() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>curline\nnextline")
        register(true)
        service<OllamaSettings>().state.deleteLineTail = true

        CompletionServiceMock.streamGenerator = { _, res ->
            res.append("test")
            true
        }
        typeChar('n')
        delay()
        assertInlineRender("test")
        insert()
        assertFileContent("funtest<caret>\nnextline")
    }

    /**
     * Test that the tail deletion only deletes the line after the last inserted character.
     */
    @Test
    fun testDeleteTailBreakInCompletion() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>curline\nnextline")
        register(false)
        service<OllamaSettings>().state.deleteLineTail = true

        CompletionServiceMock.streamGenerator = { _, res ->
            res.append("test\n")
            true
        }
        typeChar('n')
        delay()
        assertInlineRender("test\n")
        insert()
        assertFileContent("funtest\n<caret>\nnextline")
    }

    /**
     * Test the completion provider when giving time for a request to complete.
     */
    @Test
    fun testLetComplete() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>")
        register(false)

        CompletionServiceMock.streamGenerator = { _, res ->
            res.append("test")
            true
        }
        typeChar('n')
        delay()
        assertInlineRender("test")
        insert()
        assertFileContent("funtest<caret>")
        typeChar('o')
        delay()
        assertInlineRender("test")
        insert()
        assertFileContent("funtestotest<caret>")
    }

    /**
     * Test interrupting the completion provider during a completion request with a new one.
     */
    @Test
    fun testInterruptCompletion() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>")
        register(false)

        val chan = Channel<Unit>()
        CompletionServiceMock.streamGenerator = { _, _ ->
            chan.send(Unit)
            awaitCancellation()
        }

        typeChar('n')
        chan.receive()
        assertInlineRender("")
        assertNoLookup()
        typeChar('o')
        chan.receive()
        assertInlineRender("")
        assertNoLookup()
        assertFileContent("funo<caret>")
    }

    /**
     * Test allowing a previous request to finish when the output matches the new input.
     */
    @Test
    fun testContinueCompletion() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>")
        register(false)

        val chan = Channel<Unit>()
        CompletionServiceMock.streamGenerator = { _, res ->
            res.append("c")
            chan.send(Unit)
            res.append("tion")
            chan.send(Unit)
            true
        }

        typeChar('n')
        // Make sure the partial completion arrives on time
        chan.receive()
        assertInlineRender("")
        typeChar('c')
        // Allow the request to finish
        chan.receive()
        delay()
        assertInlineRender("tion")
        backSpace()
        backSpace()
        typeChar('n')
        // From cache; would otherwise block
        delay()
        assertInlineRender("ction")
    }

    /**
     * Test the storage and usage of incomplete completions.
     */
    @Test
    fun testContinueIncomplete() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>")
        register(false)

        val chan = Channel<Unit>()
        CompletionServiceMock.streamGenerator = { prompt, res ->
            when (prompt) {
                "fun" -> {
                    res.append("cti")
                    chan.send(Unit)
                    awaitCancellation()
                }

                "functi" -> res.append("on")
                "funcx" -> res.append("another")
            }
            true
        }

        typeChar('n')
        chan.receive()
        assertInlineRender("")
        typeChar('c')
        // To cancel the current request
        typeChar('x')
        delay()
        assertInlineRender("another")
        // To ensure a new completion request
        backSpace()
        // Stored incomplete completion should be completed
        typeChar('t')
        delay()
        assertInlineRender("ion")
    }

    /**
     * Test that incomplete completions get considered for the next request.
     * Also see that output added after cancellation is considered.
     */
    @Test
    fun testUnnecessaryCompletionCancellation() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>")
        register(false)

        val block = AtomicBoolean(true)
        val chan = Channel<Unit>()
        CompletionServiceMock.streamGenerator = { prompt, res ->
            when (prompt) {
                "fun" -> {
                    chan.send(Unit)
                    // A channel would get canceled, this not
                    while (block.get()) Thread.yield()
                    res.append("ction")
                    false
                }

                "function" -> true
                else -> throw AssertionFailedError()
            }
        }

        typeChar('n')
        chan.receive()
        assertInlineRender("")
        typeChar('c')
        block.set(false)
        delay()
        assertInlineRender("tion")
    }

    /**
     * Test that no two completion jobs are executed at the same time.
     */
    @Test
    fun testTerminatePrevRequest() = myFixture.testInlineCompletion {
        init(PlainTextFileType.INSTANCE, "fu<caret>")
        register(false)

        val block = AtomicBoolean(true)
        val chan = Channel<Unit>()
        CompletionServiceMock.streamGenerator = { prompt, res ->
            when (prompt) {
                "fun" -> {
                    chan.send(Unit)
                    // A channel would get canceled, this not
                    while (block.get()) Thread.yield()
                    false
                }

                "funte" -> {
                    res.append("st")
                    true
                }

                else -> throw AssertionFailedError()
            }
        }

        typeChar('n')
        chan.receive()
        assertInlineRender("")
        typeChar('t')
        typeChar('e')
        block.set(false)
        delay()
        assertInlineRender("st")
    }
}