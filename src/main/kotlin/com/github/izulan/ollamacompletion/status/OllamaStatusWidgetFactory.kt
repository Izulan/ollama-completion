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
            getComponent().text = "Connecting"
            getComponent().icon = AllIcons.General.Web
        }

        override fun onComplete(totalDuration: Long) {
            val secs = totalDuration / 1e9
            getComponent().text = "Done in %.2fs".format(secs)
            getComponent().icon = AllIcons.Actions.Checked
        }

        override fun onCancel() {
            getComponent().text = "Cancelled"
            getComponent().icon = AllIcons.Actions.Cancel
        }

        override fun onConnected() {
            getComponent().text = "Generating"
            getComponent().icon = AllIcons.Actions.Lightning
        }

        override fun onError(message: String) {
            getComponent().text = message
            getComponent().icon = AllIcons.General.Error
        }
    }

    companion object {
        const val ID = "OllamaStatus"
    }
}