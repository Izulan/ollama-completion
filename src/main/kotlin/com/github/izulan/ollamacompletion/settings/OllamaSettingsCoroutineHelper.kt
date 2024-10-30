package com.github.izulan.ollamacompletion.settings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.response.ModelDetail
import io.github.ollama4j.models.response.Model as OllamaModel
import kotlinx.coroutines.*

@Service(Service.Level.APP)
/**
 * Wrapper class for asynchronous `ollama4j` requests.
 * Callbacks executed on EDT.
 */
class OllamaSettingsCoroutineHelper(
    private val cs: CoroutineScope
) {
    /**
     * Get a list of LLMs from Ollama.
     */
    fun getModelList(
        api: OllamaAPI,
        onSuccess: (List<OllamaModel>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        cs.launch(ModalityState.current().asContextElement()) {
            try {
                val result = runInterruptible(Dispatchers.IO) {
                    api.listModels()
                }
                withContext(Dispatchers.EDT) {
                    onSuccess(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.EDT) {
                    onError(e)
                }
            }
        }
    }

    /**
     * Create a new model from the given modelfile content.
     */
    fun createModel(
        api: OllamaAPI, modelName: String, content: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        cs.launch(ModalityState.current().asContextElement()) {
            try {
                runInterruptible(Dispatchers.IO) {
                    api.createModelWithModelFileContents(modelName, content)
                }
                withContext(Dispatchers.EDT) {
                    onSuccess()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.EDT) {
                    onError(e)
                }
            }
        }
    }

    /**
     * Get details (such as modelfile) for a specified model.
     */
    fun getModelDetails(
        api: OllamaAPI,
        modelName: String,
        onSuccess: (ModelDetail) -> Unit,
        onError: (Exception) -> Unit
    ) {
        cs.launch(ModalityState.current().asContextElement()) {
            try {
                val detail = runInterruptible(Dispatchers.IO) {
                    api.getModelDetails(modelName)
                }
                withContext(Dispatchers.EDT) {
                    onSuccess(detail)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.EDT) {
                    onError(e)
                }
            }
        }
    }
}