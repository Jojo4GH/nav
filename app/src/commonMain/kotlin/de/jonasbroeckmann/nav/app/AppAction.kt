package de.jonasbroeckmann.nav.app

import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.config.Config
import kotlinx.io.files.Path

sealed interface AppAction<out R> {
    fun runIn(app: App): R

    data object NoOp : AppAction<Unit> {
        override fun runIn(app: App) = app.perform(this)
    }

    data class UpdateState(val update: State.() -> State) : AppAction<Unit> {
        override fun runIn(app: App) = app.perform(this)
    }

    data class OpenFile(val file: Path) : AppAction<Int?> {
        override fun runIn(app: App) = app.perform(this)
    }

    data class RunCommand(
        val command: String,
        val collectOutput: Boolean = false,
        val collectError: Boolean = false
    ) : AppAction<RunCommand.Result?> {
        override fun runIn(app: App) = app.perform(this)

        data class Result(
            val exitCode: Int,
            val stdout: String?,
            val stderr: String?
        ) {
            val isSuccess get() = exitCode == 0
        }
    }

    data class RunEntryMacro(val entryMacro: Config.EntryMacro) : AppAction<Unit> {
        override fun runIn(app: App) = app.perform(this)
    }

    data class RunMacro(val macro: Macro) : AppAction<Unit> {
        override fun runIn(app: App) = app.perform(this)
    }

//    data class PromptText(
//        val title: String,
//        val default: String? = null,
//        val placeholder: String? = null,
//        val cancelable: Boolean = false,
//        val validate: (String) -> Boolean = { true }
//    ) : AppAction<String> {
//        override fun runIn(app: App) = app.perform(this)
//    }

    data class Exit(val atDirectory: Path?) : AppAction<Nothing> {
        override fun runIn(app: App) = app.perform(this)
    }
}
