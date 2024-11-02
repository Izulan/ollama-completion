package com.github.izulan.ollamacompletion.services

import com.fasterxml.jackson.databind.JsonMappingException
import com.github.izulan.ollamacompletion.settings.OllamaSettings
import com.github.izulan.ollamacompletion.topics.OllamaStatusNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import io.github.ollama4j.models.generate.OllamaGenerateRequestBuilder
import io.github.ollama4j.models.generate.OllamaGenerateResponseModel
import io.github.ollama4j.models.response.OllamaErrorResponse
import io.github.ollama4j.utils.OptionsBuilder
import io.github.ollama4j.utils.Utils
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*


@Service(Service.Level.APP)
class CompletionServiceImpl(private val cs: CoroutineScope) : CompletionService, Disposable {
    @Volatile
    override var isResponseDone = false

    @Volatile
    override var activePrompt: String? = null
    override val response = StringBuffer()


    private val client = HttpClient(CIO) {
        install(HttpRequestRetry) {
            maxRetries = 2
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 1000 * 60
            connectTimeoutMillis = 1000 * 10
        }
    }
    private val settings get() = service<OllamaSettings>()

    /**
     * Starts a new streaming request on Ollama; responses can be read from the service.
     * Make sure to only allow one job to run concurrently (Ollama would otherwise queue them anyway).
     * Cancellation will terminate the request immediately.
     *
     * Why ktor and not the Ollama4j way?
     * That would not work well with coroutines and is challenging to cancel.
     *
     * @return Job that can be canceled and will terminate with completion of the request
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
            val publisher = ApplicationManager.getApplication().messageBus.syncPublisher(OllamaStatusNotifier.TOPIC)
            publisher.onStart()

            try {
                val options = OptionsBuilder().setNumCtx(settings.state.contextSize)
                    .setTopP(settings.state.topP)
                    .setTopK(settings.state.topK)
                    .setTemperature(settings.state.temperature)
                    .build()

                val ollamaRequest = OllamaGenerateRequestBuilder.getInstance(model)
                    .withStreaming()
                    .withPrompt(prompt + incompleteCompletion)
                    .withOptions(options)
                    .build()

                ollamaRequest.system = settings.systemPrompt

                var host = settings.host
                if (host.endsWith("/")) {
                    host = host.substring(0, host.length - 1)
                }

                val url = "${host}/api/generate"
                client.preparePost(url) {
                    contentType(ContentType.Application.Json)
                    setBody(Utils.getObjectMapper().writeValueAsString(ollamaRequest))
                }.execute { httpResponse ->
                    publisher.onConnected()
                    val channel: ByteReadChannel = httpResponse.body()

                    while (!channel.isClosedForRead) {
                        val packet = channel.readUTF8Line() ?: continue

                        // The packet may be an error (typically when the model is not in memory yet)
                        // These resolve themselves and can be skipped
                        try {
                            val ollamaRes =
                                Utils.getObjectMapper().readValue(packet, OllamaGenerateResponseModel::class.java)
                            response.append(ollamaRes.response)

                            if (ollamaRes.isDone) {
                                isResponseDone = true
                                publisher.onComplete(ollamaRes.totalDuration)
                                break
                            }
                        } catch (ex: JsonMappingException) {
                            val errorRes = Utils.getObjectMapper().readValue(packet, OllamaErrorResponse::class.java)
                            publisher.onError(errorRes.error)
                            break
                        }
                    }
                }
            } catch (_: CancellationException) {
                publisher.onCancel()
            } catch (_: Exception) {
                publisher.onError("Error")
            }
        }
    }

    override fun dispose() {
        client.close()
    }
}