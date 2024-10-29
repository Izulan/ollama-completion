package com.github.izulan.ollamacompletion.settings

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.response.ModelDetail
import io.github.ollama4j.models.response.Model as OllamaModel
import kotlinx.coroutines.*

@Service(Service.Level.APP)
//TODO change to non-callback
class OllamaSettingsCoroutineHelper(
    private val cs: CoroutineScope
) {

    fun testConnection(
        api: OllamaAPI,
        onSuccess: suspend (List<OllamaModel>) -> Unit,
        onError: suspend (Exception) -> Unit
    ) {
        cs.launch(ModalityState.current().asContextElement()) {
            try {
                val result = api.listModels()
                onSuccess(result)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    fun createModel(
        api: OllamaAPI, modelName: String, content: String,
        onSuccess: suspend () -> Unit,
        onError: suspend (Exception) -> Unit
    ) {
        cs.launch(ModalityState.current().asContextElement()) {
            try {
                val detail = withContext(Dispatchers.IO) {
                    api.createModelWithModelFileContents(modelName, content)
                }
                onSuccess()
            } catch (e: Exception) {
                println(e.message)
                onError(e)
            }
        }
    }

    fun getModelDetails(
        api: OllamaAPI,
        modelName: String,
        onSuccess: suspend (ModelDetail) -> Unit,
        onError: suspend (Exception) -> Unit
    ) {
        cs.launch(ModalityState.current().asContextElement()) {
            try {
                val detail = withContext(Dispatchers.IO) {
                    api.getModelDetails(modelName)
                }
                onSuccess(detail)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}