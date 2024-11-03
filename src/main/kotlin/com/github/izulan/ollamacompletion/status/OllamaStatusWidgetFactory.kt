package com.github.izulan.ollamacompletion.status

import com.github.izulan.ollamacompletion.topics.OllamaStatusNotifier
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.LazyInitializer
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable

class OllamaStatusWidgetFactory : StatusBarWidgetFactory, Activatable {
    override fun getId() = ID
    override fun getDisplayName() = "Ollama Completion Status"

    override fun createWidget(project: Project): StatusBarWidget {
        return OllamaWidget()
    }

    private class OllamaWidget : CustomStatusBarWidget, StatusBarWidget, OllamaStatusNotifier {
        val component =
            LazyInitializer.create {
                ApplicationManager.getApplication().messageBus.connect().subscribe(OllamaStatusNotifier.TOPIC, this)
                JBLabel("", UIUtil.ComponentStyle.REGULAR, UIUtil.FontColor.BRIGHTER)
            }

        override fun getComponent(): JBLabel = component.get()
        override fun ID() = ID

        override fun onStart() {
            getComponent().let {
                it.text = "Connecting"
                it.icon = AllIcons.General.Web
                it.toolTipText = ""
            }
        }

        override fun onComplete(totalDuration: Long) {
            val secs = totalDuration / 1e9
            getComponent().let {
                it.text = "Done in %.2fs".format(secs)
                it.icon = AllIcons.Actions.Checked
                it.toolTipText = ""
            }
        }

        override fun onCancel() {
            getComponent().let {
                it.text = "Cancelled"
                it.icon = AllIcons.Actions.Cancel
                it.toolTipText = ""
            }
        }

        override fun onConnected() {
            getComponent().let {
                it.text = "Generating"
                it.icon = AllIcons.Actions.Lightning
                it.toolTipText = ""
            }
        }

        override fun onError(message: String) {
            getComponent().let {
                it.text = "Error"
                it.icon = AllIcons.General.Error
                it.toolTipText = message
            }
        }
    }

    companion object {
        const val ID = "OllamaStatus"
    }
}