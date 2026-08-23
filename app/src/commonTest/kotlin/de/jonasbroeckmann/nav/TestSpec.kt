package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.actions.MenuActions
import de.jonasbroeckmann.nav.app.actions.NormalModeActions
import de.jonasbroeckmann.nav.app.actions.QuickMacroModeActions
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.context.MacroSessionContext
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.command.CommandOptions
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.framework.input.InputController
import de.jonasbroeckmann.nav.framework.input.InputMode
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogOptions
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowController
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import de.jonasbroeckmann.nav.utils.Paths
import io.kotest.core.spec.style.FunSpec
import kotlinx.io.files.Path

abstract class TestSpec(
    body: context(FullContext) FunSpec.() -> Unit = {}
) : FunSpec({ context(TestFullContext()) { body() } })

context(fullContext: FullContext)
inline fun withMainController(body: context(MainController) () -> Unit) = context(TestMainController(fullContext), body)

class TestMainController(
    fullContext: FullContext,
) : MainController,
    InputController by TestInputController(),
    DialogShowController by TestDialogShowController(),
    FullContext by fullContext,
    MacroSessionContext by MacroSessionContext(
        partialContext = fullContext,
        configProvider = fullContext
    ) {
    override var state = State.initial(
        startingDirectory = startingDirectory,
        showHiddenEntries = commandOptions.showHiddenEntries ?: config.showHiddenEntries,
        normalModeActions = NormalModeActions(this),
        quickMacroModeActions = QuickMacroModeActions(this),
        menuActions = MenuActions(this)
    )
        private set

    override fun updateState(updater: State.() -> State) {
        state = state.updater()
    }

    override fun openInEditor(file: Path) = throw UnsupportedOperationException()

    override fun runCommand(
        command: String,
        collectOutput: Boolean,
        collectError: Boolean
    ) = throw UnsupportedOperationException()

    override fun runMacro(macro: Macro) = throw UnsupportedOperationException()

    override fun runEntryMacro(entryMacro: Config.EntryMacro) = throw UnsupportedOperationException()

    override fun exit(exitCode: Int, atDirectory: Path?) = throw UnsupportedOperationException()
}

class TestInputController : InputController {
    override fun enterInputMode(mode: InputMode) = throw UnsupportedOperationException()
}

class TestDialogShowController : DialogShowController {
    override fun <R> showDialog(
        options: DialogOptions,
        block: DialogShowScope.() -> R
    ) = throw UnsupportedOperationException()
}

class TestFullContext : FullContext by FullContext(
    partialContext = TestPartialContext(),
    configProvider = TestConfigProvider()
)

@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
class TestPartialContext(
    override val commandOptions: CommandOptions = CommandOptions(),
    override val startingDirectory: Path = Paths.WorkingDirectory
) : PartialContext, Logger by TestLogger(debugMode = true) {
    override val terminal get() = throw UnsupportedOperationException()
}

class TestConfigProvider : ConfigProvider {
    override val config get() = Config.Default

    override val configPath get() = null
}
