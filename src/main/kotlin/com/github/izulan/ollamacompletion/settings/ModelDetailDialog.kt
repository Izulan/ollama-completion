package com.github.izulan.ollamacompletion.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.LanguageTextField
import com.intellij.ui.dsl.builder.*
import io.github.ollama4j.OllamaAPI
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent

/**
 * Dialog for viewing or creating a modelfile.
 *
 * @param api Set this to value to allow model creation, else only view given modelfile.
 */
class ModelDetailDialog(
    private var modelName: String,
    private var modelfile: String,
    private val api: OllamaAPI? = null
) :
    DialogWrapper(true) {
    private val isReadonly = api == null
    private val coroutineHelper get() = service<OllamaSettingsCoroutineHelper>()
    private lateinit var dialogPanel: DialogPanel

    init {
        title = if (isReadonly) "View Model" else "Create New Model"
        rootPane.defaultButton = null
        super.init()
    }

    override fun createActions(): Array<Action> =
        if (isReadonly) arrayOf(cancelAction) else arrayOf(cancelAction, okAction)


    override fun createCenterPanel(): JComponent {
        dialogPanel = panel {
            row("Model Name:") {
                textField().bindText(::modelName).enabled(!isReadonly)
            }
            group("Modelfile") {
                row {
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
                        .enabled(!isReadonly)
                }.resizableRow()
            }
        }
        return dialogPanel
    }

    override fun doOKAction() {
        if (api == null) return

        dialogPanel.apply()
        super.setOKActionEnabled(false)
        super.setOKButtonIcon(AnimatedIcon.Default())
        coroutineHelper.createModel(api, modelName, modelfile, {
            super.setOKButtonIcon(null)
            super.setOKActionEnabled(true)
            super.doOKAction()
        }, {
            super.setOKButtonIcon(null)
            super.setOKActionEnabled(true)
            // This will probably be raw JSON, but that is fine
            super.setErrorText(it.message)
        })
    }
}