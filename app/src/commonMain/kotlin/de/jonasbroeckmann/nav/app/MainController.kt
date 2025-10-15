package de.jonasbroeckmann.nav.app

import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.framework.input.InputController
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowController
import kotlinx.io.files.Path

interface MainController : InputController, DialogShowController, FullContext, StateProvider {
    fun updateState(updater: State.() -> State)

    fun openInEditor(file: Path): Int?

    fun runCommand(command: String, collectOutput: Boolean = false, collectError: Boolean = false): RunCommandResult?

    data class RunCommandResult(
        val exitCode: Int,
        val stdout: String?,
        val stderr: String?
    ) {
        val isSuccess get() = exitCode == 0
    }

    fun runMacro(macro: Macro)

    fun runEntryMacro(entryMacro: Config.EntryMacro)

    fun exit(atDirectory: Path? = null): Nothing
}

context(controller: MainController)
fun updateState(updater: State.() -> State) = controller.updateState(updater)

context(controller: MainController)
fun openInEditor(file: Path): Int? = controller.openInEditor(file)

context(controller: MainController)
fun runCommand(
    command: String,
    collectOutput: Boolean = false,
    collectError: Boolean = false
) = controller.runCommand(
    command = command,
    collectOutput = collectOutput,
    collectError = collectError
)

context(controller: MainController)
fun runMacro(macro: Macro) = controller.runMacro(macro)

context(controller: MainController)
fun runEntryMacro(entryMacro: Config.EntryMacro) = controller.runEntryMacro(entryMacro)

context(controller: MainController)
fun exit(atDirectory: Path? = null): Nothing = controller.exit(atDirectory)
