package com.github.izulan.ollamacompletion.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import io.github.ollama4j.OllamaAPI
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListSelectionModel
import io.github.ollama4j.models.response.Model as OllamaModel

/**
 * Main settings page for the plugin.
 */
class OllamaSettingsConfigurable : BoundConfigurable("Ollama Settings") {
    private val settings get() = service<OllamaSettings>()
    private val coroutineHelper get() = service<OllamaSettingsCoroutineHelper>()

    private lateinit var connectionStatusLabel: Cell<JLabel>
    private lateinit var hostnameTextField: Cell<JBTextField>
    private lateinit var modelList: JBList<OllamaModel>
    private lateinit var listGroup: Row
    private var api = OllamaAPI(settings.host)
    private var selectedModel: String? = null

    private inner class ModelListRenderer : ColoredListCellRenderer<OllamaModel>() {
        override fun customizeCellRenderer(
            list: JList<out OllamaModel>,
            value: OllamaModel,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            icon = if (selectedModel == value.name) AllIcons.Actions.Checked else null
            append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            append(" " + value.model, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            append(" " + value.modelMeta.quantizationLevel, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    override fun reset() {
        selectedModel = settings.state.selectedModel
        super.reset()
    }

    override fun apply() {
        settings.state.selectedModel = selectedModel
        super.apply()
    }

    override fun isModified(): Boolean {
        return super.isModified() || selectedModel != settings.state.selectedModel
    }

    private fun createModelListWithToolbar(): ToolbarDecorator {
        modelList = JBList<OllamaModel>().apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = ModelListRenderer()
        }

        val modelListToolbar = ToolbarDecorator.createDecorator(modelList)
        modelListToolbar.run {
            setAddAction {
                if (ModelDetailDialog("", "", api).showAndGet()) {
                    fetchModels()
                }
            }
            disableRemoveAction()
            disableUpAction()
            disableDownAction()
            addExtraActions(
                object : AnAction("Show Modelfile", "Show modelfile", AllIcons.Actions.Show) {
                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }

                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = modelList.selectedIndex != -1
                    }

                    override fun actionPerformed(e: AnActionEvent) {
                        openModelDetail()
                    }
                },
                object : AnAction("Set as Completion Model", "Set as completion model", AllIcons.Actions.Checked) {
                    override fun getActionUpdateThread(): ActionUpdateThread {
                        return ActionUpdateThread.EDT
                    }

                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = modelList.selectedIndex != -1
                    }

                    override fun actionPerformed(e: AnActionEvent) {
                        selectedModel = modelList.selectedValue.name
                        modelList.repaint()
                    }
                },
            )
        }

        return modelListToolbar
    }

    override fun createPanel(): DialogPanel {
        selectedModel = settings.state.selectedModel
        val modelList = createModelListWithToolbar()

        return panel {
            group("Connection") {
                row("Ollama hostname:") {
                    hostnameTextField = textField().bindText(settings::host)
                }
                row {
                    button("Connect") {
                        api = OllamaAPI(hostnameTextField.component.text)
                        fetchModels()
                    }
                    connectionStatusLabel = label("")
                }
            }
            listGroup = group("Completion Model") {
                row {
                    cell(modelList.createPanel()).align(AlignX.FILL)
                }
            }.enabled(false)
            row("System prompt:") {
                textArea()
                    .bindText(settings::systemPrompt)
                    .align(AlignX.FILL)
                    .rows(5)
                    .comment("May be ignored depending on the model.")
            }
            row {
                checkBox("Delete tail")
                    .bindSelected(settings.state::deleteLineTail)
                    .comment("If a completion is mid-line, this setting will replace the rest of the line with the completion.")
            }
            group("Parameters (Independent of Model)") {
                row("Context size:") {
                    spinner(0..Int.MAX_VALUE, 1).bindIntValue(settings.state::contextSize)
                        .comment("Sets the size of the context window used to generate the next token. (Default: 2048)")
                }
                row("Temperature:") {
                    spinner(0.0..4.0, 0.01).bindValue(settings::temperature)
                        .comment("[0,4] The temperature of the model. Increasing the temperature will make the model answer more creatively. (Default: 0.8)")
                }
                row("TopK:") {
                    spinner(0..200, 1).bindIntValue(settings.state::topK)
                        .comment("[0,200] Reduces the probability of generating nonsense. A higher value (e.g. 100) will give more diverse answers, while a lower value (e.g. 10) will be more conservative. (Default: 40)")
                }
                row("TopP:") {
                    spinner(0.0..4.0, 0.01).bindValue(settings::topP)
                        .comment("[0,4] Works together with top-k. A higher value (e.g., 0.95) will lead to more diverse text, while a lower value (e.g., 0.5) will generate more focused and conservative text. (Default: 0.9)")
                }
            }
        }
    }

    private fun fetchModels() {
        connectionStatusLabel.component.icon = AnimatedIcon.Default()
        connectionStatusLabel.component.text = "Fetching Models..."
        listGroup.enabled(false)

        coroutineHelper.getModelList(
            api,
            onSuccess = { models ->
                connectionStatusLabel.component.icon = AllIcons.General.InspectionsOK
                connectionStatusLabel.component.text = "Connection successful"
                modelList.model = CollectionListModel(models)
                listGroup.enabled(true)
            },
            onError = { exn ->
                connectionStatusLabel.component.icon = AllIcons.General.Error
                connectionStatusLabel.component.text = "Connection failed: ${exn.message ?: "Connection failed"}"
            }
        )
    }


    private fun openModelDetail() {
        val name = modelList.selectedValue.name
        coroutineHelper.getModelDetails(api, name, {
            ModelDetailDialog(name, it.modelFile).show()
        }, { thisLogger().warn(it) })
    }
}