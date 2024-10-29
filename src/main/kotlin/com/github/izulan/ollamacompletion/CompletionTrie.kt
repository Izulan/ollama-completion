package com.github.izulan.ollamacompletion

import com.jetbrains.rd.util.first

/**
 * Compressed prefix trie with LRU pruning that allows for storing completed and incomplete completions.
 *
 * Prefixes and completions share a tree, in fact they can even overlap.
 *
 * Some invariants hold for the trie after every method call:
 *  - minLruIndex is always the minimum LRU index of all subtrees and the own lruIndex
 *  - size always equals the sum of all characters of all subtrees (from edges)
 *  - each node can be marked as a completion step by setting `completionEdge`
 *      - each such path must eventually lead to a terminal node with an `lruIndex`,
 *      which indicates the end. `isComplete` indicates the status of that completion.
 *      - terminal nodes can be inner nodes but all leaves must be terminal nodes
 *  - Inner nodes must have at least two children (otherwise they must be compressed),
 *  except if a completion begins in the middle of them
 */
class CompletionTrie(private var maxSize: Int = 1_000_000) {
    private val root = TrieNode()
    private var lruCounter = 0

    /**
     * Returns the number of stored characters in the trie.
     */
    val size: Int
        get() = root.size

    private data class TrieNode(
        /** Outgoing edges, identified by their first character (unique). */
        val edges: MutableMap<Char, Edge> = mutableMapOf(),
        /** Recursively combined length of all child labels. */
        var size: Int = 0,
        /** Minimum LRU index of all children. */
        var minLruIndex: Int = Int.MAX_VALUE,
        /** Refers to an [Edge] in [edges] which continues the current completion. */
        var completionEdge: Edge? = null,
        /** Non-null values signal a termination node with specified LRU index. */
        var lruIndex: Int? = null,
        /** Only meaningful with LRU index set, then specifies whether terminated completion is complete. */
        var isComplete: Boolean = false
    )

    private data class Edge(var label: String, var node: TrieNode)
    data class CompletionResult(val completion: String, val isComplete: Boolean)

    /**
     * Inserts a completion into the trie.
     * - Traverses the trie as per the given prefix input
     * - Same for the completion input, but also lay completion pointers
     * - If the node during the completion traversal already points to a completed completion,
     * the to be inserted completion will be **ignored**, and the LRU updated.
     * - If the completion overlaps with an existing incomplete completion, that one
     * will be removed and the new one inserted (even if it is incomplete)
     *
     * To complete an incomplete completion, make sure to keep the incomplete completion part of the
     * completion and not move it to the prefix. That would lead to a new completion, as it does not overlap.
     *
     * @param completion Empty completions will be ignored
     */
    fun insert(prefix: String, completion: String, isComplete: Boolean = true) {
        if (completion.isEmpty()) return
        insert(root, prefix, completion, isComplete)
        while (root.size > maxSize) {
            removeLeastRecentlyUsedCompletion(root)
        }
    }

    /**
     * Retrieves a completion for the given prefix if it exists.
     * Updates the LRU indices for the accessed nodes on success.
     */
    fun getCompletion(prefix: String): CompletionResult? {
        var curNode = root
        var curPrefix = prefix
        val path = mutableListOf<TrieNode>()
        val result = StringBuilder()
        // If we land on a termination node after traversing the prefix,
        // ignore it, as 0 character completions are useless
        var directlyAfterPrefix = true

        // Traverse prefix (and potentially partial completion)
        while (curPrefix.isNotEmpty()) {
            path.add(curNode)
            val edge = curNode.edges[curPrefix[0]] ?: return null

            if (curPrefix.startsWith(edge.label)) {
                curNode = edge.node
                curPrefix = curPrefix.substring(edge.label.length, curPrefix.length)
            } else if (edge.label.startsWith(curPrefix) && edge == curNode.completionEdge) {
                result.append(edge.label.substring(curPrefix.length))
                curPrefix = ""
                directlyAfterPrefix = false
                curNode = edge.node
            } else {
                return null
            }
        }

        // Traverse completion
        while (curNode.completionEdge != null || (curNode.lruIndex != null && !directlyAfterPrefix)) {
            path.add(curNode)

            if (curNode.lruIndex != null && !directlyAfterPrefix) {
                // Update the path's to reflect the updated LRU index
                updateLruForPath(path, ++lruCounter)
                return CompletionResult(result.toString(), curNode.isComplete)
            }

            directlyAfterPrefix = false
            result.append(curNode.completionEdge!!.label)
            curNode = curNode.completionEdge!!.node
        }

        return null
    }

    /**
     * Compute the min LRU index for [node].
     *
     * @return Computed min LRU index
     */
    private fun computeMinLruIndex(node: TrieNode): Int {
        var minLru = node.lruIndex ?: Int.MAX_VALUE
        for (child in node.edges.values) {
            minLru = minOf(minLru, child.node.minLruIndex)
        }
        return minLru
    }

    /**
     * Updates the LRU indices on the [path] (top-down).
     * The last element of [path] will assume [newLruIndex].
     *
     * @return Replaced LRU index
     */
    private fun updateLruForPath(path: List<TrieNode>, newLruIndex: Int): Int {
        if (path.isEmpty()) throw IllegalArgumentException("Path must hold at least one node")

        val oldLruIndex = path.last().lruIndex!!
        path.last().lruIndex = newLruIndex

        for (n in path.reversed()) {
            if (n.minLruIndex != oldLruIndex && newLruIndex > oldLruIndex) break
            n.minLruIndex = computeMinLruIndex(n)
        }

        return oldLruIndex
    }

    private fun insert(
        node: TrieNode,
        prefix: String,
        completion: String,
        isComplete: Boolean
    ): Int {
        if (prefix.isEmpty() && completion.isEmpty()) {
            node.isComplete = isComplete
            node.lruIndex = ++lruCounter
            node.minLruIndex = node.lruIndex!!
            return 0
        }

        var deletionSizeChange = 0
        // We are merging into an existing completion that may not match the suffix of the new one.
        // If that completion is complete -> generalize the old one
        // If not -> remove the incomplete one and insert the new one
        if (prefix.isEmpty() && node.completionEdge != null) {
            val pathToCompletion = getPathToCompletion(node)
            if (pathToCompletion.last().isComplete) {
                updateLruForPath(pathToCompletion, ++lruCounter)
                return 0
            }

            updateLruForPath(pathToCompletion, 0)
            val oldSize = node.size
            removeLeastRecentlyUsedCompletion(node)
            deletionSizeChange = node.size - oldSize
        }

        val toInsert = prefix.ifEmpty { completion }
        val isInsertingPrefix = prefix.isNotEmpty()

        val firstChar = toInsert[0]
        val edge = node.edges[firstChar]
        val sizeChange: Int


        if (edge != null) {
            val commonLength = commonPrefixLength(toInsert, edge.label)

            if (commonLength == edge.label.length) {
                // Full match on the edge label; continue recursively
                val remainingInsert = toInsert.substring(commonLength, toInsert.length)
                sizeChange = if (isInsertingPrefix) {
                    insert(edge.node, remainingInsert, completion, isComplete)
                } else {
                    insert(edge.node, prefix, remainingInsert, isComplete)
                }
                if (!isInsertingPrefix) {
                    node.completionEdge = edge
                }
            } else {
                // Partial match (at least first char); need to split the edge then redo
                splitEdge(edge, commonLength, node.completionEdge == edge)
                return insert(node, prefix, completion, isComplete) + deletionSizeChange
            }
        } else {
            // No matching edge; create a new edge and node
            val newNode = TrieNode()
            node.edges[firstChar] = Edge(toInsert, newNode)
            if (!isInsertingPrefix) {
                node.completionEdge = node.edges[firstChar]
            }
            sizeChange = toInsert.length + insert(newNode, "", if (isInsertingPrefix) completion else "", isComplete)
        }

        node.size += sizeChange
        node.minLruIndex = computeMinLruIndex(node)
        return sizeChange + deletionSizeChange
    }

    private fun getPathToCompletion(start: TrieNode): MutableList<TrieNode> {
        var curNode = start
        val path = mutableListOf(curNode)

        // Completion point will have lruIndex set
        while (curNode.lruIndex == null) {
            assert(curNode.completionEdge != null)
            curNode = curNode.completionEdge!!.node
            path.add(curNode)
        }

        return path
    }

    /**
     * Splits [edge] after [splitPos] characters.
     * Sets completion edge in-between if [isCompletionEdge] is set.
     */
    private fun splitEdge(
        edge: Edge,
        splitPos: Int,
        isCompletionEdge: Boolean
    ) {
        val commonPart = edge.label.substring(0, splitPos)
        val edgeSuffix = edge.label.substring(splitPos, edge.label.length)

        // The node to go between
        val splitNode = TrieNode(
            minLruIndex = edge.node.minLruIndex,
            size = edge.node.size + edgeSuffix.length
        )

        val splitToOriginalEdge = Edge(edgeSuffix, edge.node)
        splitNode.edges[edgeSuffix[0]] = splitToOriginalEdge

        if (isCompletionEdge) {
            splitNode.completionEdge = splitToOriginalEdge
        }

        edge.label = commonPart
        edge.node = splitNode
    }

    private fun commonPrefixLength(s1: String, s2: String): Int {
        val minLength = minOf(s1.length, s2.length)
        for (i in 0 until minLength) {
            if (s1[i] != s2[i]) return i
        }
        return minLength
    }

    private fun removeLeastRecentlyUsedCompletion(node: TrieNode) {
        // Completion to delete found
        if (node.minLruIndex == node.lruIndex) {
            // Deleting inner completions does not reduce size (as in chars) but
            // can reduce splits and thus actual memory footprint
            node.lruIndex = null
            node.minLruIndex = computeMinLruIndex(node)
            return
        }

        val minEdgeKey = node.edges.keys.find { node.edges[it]!!.node.minLruIndex == node.minLruIndex }
        val minEdge = node.edges[minEdgeKey]!!
        val oldSize = minEdge.node.size
        val childWasCompletion = minEdge.node.lruIndex != null

        removeLeastRecentlyUsedCompletion(minEdge.node)

        if (minEdge.node.lruIndex == null && minEdge.node.edges.isEmpty()) {
            // Leaves without completions hold no information and can be deleted
            node.edges.remove(minEdgeKey)
            node.size -= oldSize + minEdge.label.length
        } else if (minEdge.node.lruIndex == null && minEdge.node.edges.size == 1 && (childWasCompletion == (minEdge.node.completionEdge == null))) {
            // The min node was previously split to accommodate the completion-indicator node or a completion beginning.
            // We may be able to merge it if:
            // - min-node was no completion termination and node points to the same completion (thus the split was because of user input, which is now gone)
            // - min-node was a completion termination and the sole edge is no completion (the completion was removed)
            val soleChildEdge = minEdge.node.edges.first().value
            minEdge.node = soleChildEdge.node
            minEdge.label += soleChildEdge.label
        } else {
            // Something potentially changed further down; update size
            node.size += minEdge.node.size - oldSize
        }

        // Correct broken completion edges after potential removal (propagates from deletion point)
        if (node.completionEdge?.node?.lruIndex == null && (node.completionEdge?.node?.completionEdge == null || childWasCompletion)) {
            node.completionEdge = null
        }

        node.minLruIndex = computeMinLruIndex(node)
    }
}
