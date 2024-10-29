package com.github.izulan.ollamacompletion.services

import com.fasterxml.jackson.databind.JsonMappingException
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import io.github.ollama4j.models.generate.OllamaGenerateRequest
import io.github.ollama4j.models.generate.OllamaGenerateResponseModel
import io.github.ollama4j.utils.Utils
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import com.github.izulan.ollamacompletion.settings.OllamaSettings

@Service(Service.Level.APP)
class CompletionServiceImpl(private val cs: CoroutineScope) : CompletionService {
    @Volatile
    override var activePrompt: String? = null
    override val response = StringBuffer()

    @Volatile
    override var isResponseDone = false

    private val client = HttpClient()
    private val settings get() = service<OllamaSettings>()

    /**
     * Starts a new streaming request on Ollama; responses can be read from the service.
     * Make sure to only allow one job to run concurrently (Ollama would otherwise queue them anyway).
     * Cancellation will terminate the request immediately.
     *
     * Why ktor and not the Ollama4j way?
     * That would not work well with coroutines and is difficult to cancel.
     *
     * @return Job that can be cancelled and will terminate with completion of the request
     */
    override fun startStream(
        model: String,
        prompt: String,
        incompleteCompletion: String,
    ): Job {
        isResponseDone = false
        response.setLength(0)
        response.append(incompleteCompletion)
        activePrompt = prompt

        return cs.launch(Dispatchers.IO) {
            val ollamaRequestModel = OllamaGenerateRequest(model, prompt + incompleteCompletion)
            ollamaRequestModel.isStream = true
            ollamaRequestModel.isRaw = false
            ollamaRequestModel.system = settings.systemPrompt

            var host = settings.host
            if (host.endsWith("/")) {
                host = host.substring(0, host.length - 1)
            }

            val url = "${host}/api/generate"
            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(Utils.getObjectMapper().writeValueAsString(ollamaRequestModel))
            }

            val channel: ByteReadChannel = httpResponse.body()
            while (!channel.isClosedForRead) {
                val packet = channel.readUTF8Line() ?: continue

                // The packet may be an error (typically when the model is not in memory yet)
                // These resolve themselves and can be skipped
                try {
                    val response = Utils.getObjectMapper().readValue(packet, OllamaGenerateResponseModel::class.java)
                    this@CompletionServiceImpl.response.append(response.response)

                    if (response.isDone) {
                        isResponseDone = true
                        break
                    }
                } catch (ex: JsonMappingException) {

                }
            }
        }
    }
}