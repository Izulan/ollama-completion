package com.github.izulan.ollamacompletion

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CompletionTrieTest {
    /**
     * Test inserting a single completion and retrieving it with an exact match.
     */
    @Test
    fun testSingleCompletionExactMatch() {
        val trie = CompletionTrie()
        trie.insert("Hello ", "World")
        val result = trie.getCompletion("Hello ")

        assertEquals("World", result?.completion)
        assertTrue(result?.isComplete == true)
    }

    /**
     * Test retrieving a completion with a partial prefix match.
     */
    @Test
    fun testSingleCompletionPartialMatch() {
        val trie = CompletionTrie()
        trie.insert("Hello ", "World")
        val result = trie.getCompletion("Hello W")

        assertEquals("orld", result?.completion)
        assertTrue(result?.isComplete == true)
    }

    /**
     * Test retrieving a completion when the prefix does not exist in the trie.
     */
    @Test
    fun testNonExistentPrefix() {
        val trie = CompletionTrie()
        trie.insert("Hello ", "World")
        val result = trie.getCompletion("Hi")

        assertNull(result)
    }

    /**
     * Test inserting completions with directly overlapping prefixes and retrieving them.
     */
    @Test
    fun testDirectlyOverlappingPrefixes() {
        val trie = CompletionTrie()
        trie.insert("test", "ing", true)
        trie.insert("testing", "123", true)

        val resultTest = trie.getCompletion("test")
        val resultTesting = trie.getCompletion("testing")
        val resultEnd = trie.getCompletion("testing123")
        val resultPartial = trie.getCompletion("testing12")

        assertEquals("ing", resultTest?.completion)
        assertTrue(resultTest?.isComplete == true)
        assertEquals("123", resultTesting?.completion)
        assertTrue(resultTesting?.isComplete == true)
        assertNull(resultEnd)
        assertEquals("3", resultPartial?.completion)
        assertTrue(resultPartial?.isComplete == true)
    }

    /**
     * Test updating a complete completion for an existing prefix.
     * This should not change the completion.
     */
    @Test
    fun testUpdatingCompleteCompletion() {
        val trie = CompletionTrie()
        trie.insert("Hello ", "World")
        trie.insert("Hello ", "There")

        val result = trie.getCompletion("Hello ")

        assertEquals("World", result?.completion)
    }

    /**
     * Test inserting a completion that converges with an existing one.
     * Here "Hel" would complete to "lo There", so it passes by "Hello ".
     * But we know that "World" is a valid completion for that too.
     * Thus, we can discard the "There" and have learned that just "Hel" is a
     * sufficient trigger.
     */
    @Test
    fun testExtendCompleteCompletion() {
        val trie = CompletionTrie()
        trie.insert("Hello ", "World")
        trie.insert("Hel", "lo There")
        // This should not extend, as the completion paths don't intersect
        trie.insert("Foo Bar", "1")
        trie.insert("Foo ", "Ba")
        // Same here
        trie.insert("'Foo Bar", "1")
        trie.insert("'Foo ", "Bar")

        assertEquals("lo World", trie.getCompletion("Hel")?.completion)
        assertEquals("World", trie.getCompletion("Hello ")?.completion)
        assertEquals("1", trie.getCompletion("Foo Bar")?.completion)
        assertEquals("Ba", trie.getCompletion("Foo ")?.completion)
        assertEquals("1", trie.getCompletion("'Foo Bar")?.completion)
        assertEquals("Bar", trie.getCompletion("'Foo ")?.completion)
    }

    /**
     * Test inserting an overlapping completion that conflicts with an incomplete one.
     * This removes the incomplete completion and replaces it by the complete one.
     */
    @Test
    fun testReplaceIncompleteCompletion() {
        val trie = CompletionTrie()
        trie.insert("Hello ", "World", false)
        trie.insert("Hel", "lo There", true)

        val result = trie.getCompletion("Hell")
        val resultMid = trie.getCompletion("Hello ")

        assertEquals("o There", result?.completion)
        assertTrue(result?.isComplete == true)
        assertEquals("There", resultMid?.completion)
        assertTrue(resultMid?.isComplete == true)
    }

    /**
     * Test inserting an overlapping incomplete completion that conflicts with an incomplete one.
     * Even new incomplete completions should just delete conflicting incomplete ones.
     */
    @Test
    fun testReplaceIncompleteCompletionByIncomplete() {
        val trie = CompletionTrie()
        trie.insert("Hello ", "World", false)
        trie.insert("Hel", "lo There", false)

        val result = trie.getCompletion("Hell")
        val resultMid = trie.getCompletion("Hello ")

        assertEquals("o There", result?.completion)
        assertTrue(result?.isComplete == false)
        assertEquals("There", resultMid?.completion)
        assertTrue(resultMid?.isComplete == false)
    }

    /**
     * Test the pruning behavior when the trie exceeds its maximum size.
     */
    @Test
    fun testPruningBehavior() {
        val trie = CompletionTrie(maxSize = 20)
        // Size 0 -> 5
        trie.insert("aac", "xx")
        // Size 5 -> 9
        trie.insert("aa", "1234")
        // Size 9 -> 9
        trie.insert("a", "aa")
        // Size 9 -> 9
        trie.insert("", "a")
        // Size 9 -> 14
        trie.insert("ab", "test")
        // Size 14 -> 18
        trie.insert("aa1234", "6789")

        assertEquals(18, trie.size)

        // Size 18 -> 23 (prune "aac" (-2), "aa" (-0), "a" (-0), "" (-0), "ab" (-5)) -> 18
        trie.insert("ac", "test")

        assertEquals(18, trie.size)

        assertNull(trie.getCompletion("aac"))
        assertNull(trie.getCompletion("aa"))
        assertNull(trie.getCompletion("a"))
        assertNull(trie.getCompletion(""))
        assertNull(trie.getCompletion("ab"))
        assertEquals("6789", trie.getCompletion("aa1234")?.completion)
        assertEquals("test", trie.getCompletion("ac")?.completion)
    }

    /**
     * Test the size calculation of the trie after various operations.
     */
    @Test
    fun testSizeCalculation() {
        val trie = CompletionTrie()
        assertEquals(0, trie.size)

        // Size increases by 5 (prefix) + 5 (completion) = 10
        trie.insert("hello", "world")
        assertEquals(10, trie.size)
        // Size increases by 5 for the additional completion
        trie.insert("helloworld", "test")
        assertEquals(14, trie.size)
        // Should not be inserted
        trie.insert("hello", "test")
        assertEquals(14, trie.size)
        // Insert the 'i' completion
        trie.insert("h", "i")
        assertEquals(15, trie.size)
        // No new nodes/edges
        trie.insert("hel", "lo")
        assertEquals(15, trie.size)
    }

    /**
     * Test inserting a completion marked as incomplete and retrieving it.
     */
    @Test
    fun testIncompleteCompletion() {
        val trie = CompletionTrie()
        trie.insert("Hello ", "World", isComplete = false)
        val result = trie.getCompletion("Hello ")

        assertEquals("World", result?.completion)
        assertTrue(result?.isComplete == false)
    }

    /**
     * Test that inserting a completion with a longer prefix doesn't interfere with shorter prefixes.
     */
    @Test
    fun testLongerPrefixInsertion() {
        val trie = CompletionTrie()
        trie.insert("fun", "ction")
        trie.insert("function", "al")

        val resultFun = trie.getCompletion("fun")
        val resultFunction = trie.getCompletion("function")

        assertEquals("ction", resultFun?.completion)
        assertEquals("al", resultFunction?.completion)
    }

    /**
     * Test that the trie handles empty strings correctly.
     */
    @Test
    fun testEmptyStringHandling() {
        val trie = CompletionTrie()
        trie.insert("", "empty")
        trie.insert("hello", "")

        assertEquals("empty", trie.getCompletion("")?.completion)
        assertNull(trie.getCompletion("hello")?.completion)
    }

    /**
     * Test whether splitting an edge, which is part of a completion, works.
     */
    @Test
    fun testSplitCompletionEdge() {
        val trie = CompletionTrie()
        trie.insert("a", "123")
        trie.insert("a124", "567")

        assertEquals("123", trie.getCompletion("a")?.completion)
        assertEquals("567", trie.getCompletion("a124")?.completion)
        assertEquals(8, trie.size)
    }

    /**
     * Test that the trie correctly updates LRU indices when completions are accessed.
     */
    @Test
    fun testLRUIndexUpdateOnAccess() {
        val trie = CompletionTrie(6)
        // Size 0 -> 3
        trie.insert("ab", "1")
        // Size 3 -> 4
        trie.insert("a", "2")
        // Size 4 -> 6
        trie.insert("ac", "3")

        // Access 'ab' to update its LRU index
        trie.getCompletion("ab")

        // Insert a new completion to exceed the default maxSize and trigger pruning
        trie.insert("d", "4")

        // After pruning, 'ab' and 'd' should remain with a size of 5
        assertEquals("1", trie.getCompletion("ab")?.completion)
        assertEquals("4", trie.getCompletion("d")?.completion)
        assertNull(trie.getCompletion("a"))
        assertNull(trie.getCompletion("ac"))
        assertEquals(5, trie.size)
    }

    @Test
    fun testNodeMergeAfterPrune() {
        val trie = CompletionTrie(6)
        // Size 0 -> 3
        trie.insert("ab", "1")
        // Size 3 -> 5
        trie.insert("c", "2")
        // Size 5 -> 6
        trie.insert("ab1", "3")
        // Size 6 -> 8 (prune, delete ab->1 and c->2) -> 6
        trie.insert("ac", "4")

        assertEquals(6, trie.size)
        assertNull(trie.getCompletion("ab"))
        assertNull(trie.getCompletion("c"))
        assertNotNull(trie.getCompletion("ab1"))
        assertNotNull(trie.getCompletion("ac"))
    }

    /**
     * Test a slightly larger tree
     */
    @Test
    fun testTrieWithPruning() {
        val trie = CompletionTrie(30)

        // Insert overlapping completions
        trie.insert("animal", "farm")
        assertEquals("Invalid size", 10, trie.size)

        trie.insert("antelope", "wildlife")
        assertEquals("Invalid size", 24, trie.size)

        trie.insert("ant", "hill")
        assertEquals("Invalid size", 28, trie.size)

        trie.insert("antarctica", "cold")
        // 39 -> 31 (animal prune) -> 18 (antelope prune)
        assertEquals("Invalid size", 18, trie.size)

        trie.insert("banana", "yellow")
        assertEquals("Invalid size", 30, trie.size)

        trie.insert("banner", "man")
        // 36 -> 32 (ant prune) -> 18 (antarctica prune)
        assertEquals("Invalid size", 18, trie.size)

        assertNull("Completion for 'animal' should be pruned", trie.getCompletion("animal"))
        assertNull("Completion for 'antelope' should be pruned", trie.getCompletion("antelope"))
        assertNull("Completion for 'ant' should be pruned", trie.getCompletion("ant"))
        assertNull("Completion for 'antactica' should be pruned", trie.getCompletion("antarctica"))


        assertEquals("Invalid Completion for 'banana'", "yellow", trie.getCompletion("banana")?.completion)
        assertEquals("Invalid Completion for 'banner'", "man", trie.getCompletion("banner")?.completion)
    }
}
