package com.github.izulan.ollamacompletion.settings

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.LanguageTextField
import com.intellij.ui.dsl.builder.*
import io.github.ollama4j.OllamaAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

class ModelDetailDialog(var modelName: String, var modelfile: String, val api: OllamaAPI? = null) :
    DialogWrapper(true) {
    private val coroutineHelper get() = service<OllamaSettingsCoroutineHelper>()
    private lateinit var dialogPanel: DialogPanel

    init {
        title = if (api == null) "View Model" else "Create New Model"
        super.init()
        rootPane.defaultButton = null
    }

    override fun createActions(): Array<Action> {
        if (api == null) {
            return arrayOf(cancelAction)
        }

        return arrayOf(cancelAction, okAction)
    }

    override fun createCenterPanel(): JComponent {
        dialogPanel = panel {
            row("Model Name:") {
                textField().bindText(::modelName).enabled(api != null)
            }
            group("Modelfile") {
                row() {
                    cell(LanguageTextField(PlainTextLanguage.INSTANCE, null, modelfile).apply {
                        preferredSize = Dimension(800, 600)
                        setOneLineMode(false)
                        addSettingsProvider { editor: EditorEx ->
                            editor.setVerticalScrollbarVisible(true)
                            editor.setHorizontalScrollbarVisible(true)
                            editor.settings.apply {
                                isLineNumbersShown = true
                            }
                        }
                    }).bind({ c -> c.text }, { c, v -> c.text = v }, ::modelfile.toMutableProperty())
                        .align(Align.FILL)
                        .focused()
                        .resizableColumn()
                        .enabled(api != null)
                }.resizableRow()
            }
        }
        return dialogPanel
    }

    override fun doOKAction() {
        dialogPanel.apply()
        super.setOKActionEnabled(false)
        if (api != null) {
            coroutineHelper.createModel(api, modelName, modelfile, {
                withContext(Dispatchers.EDT) {
                    super.doOKAction()
                }
            }, {
                withContext(Dispatchers.EDT) {
                    super.setErrorText(it.message)
                    super.setOKActionEnabled(true)
                }
            })
        }
    }
}