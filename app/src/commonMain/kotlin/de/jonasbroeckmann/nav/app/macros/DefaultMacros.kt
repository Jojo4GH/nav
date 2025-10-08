package de.jonasbroeckmann.nav.app.macros

object DefaultMacros {
    val RunCommand = Macro(
        id = "navRunCommand",
        hidden = true,
        actions = MacroActions(
            MacroAction.RunCommand(command = DefaultMacroVariables.Command.placeholder),
            MacroAction.If(
                condition = MacroCondition.Not(
                    MacroCondition.Equal(
                        listOf(
                            DefaultMacroVariables.ExitCode.placeholder,
                            StringWithPlaceholders("0")
                        )
                    )
                ),
                then = MacroActions(
                    MacroAction.Print(
                        print = StringWithPlaceholders("Received exit code ${DefaultMacroVariables.ExitCode.placeholder}"),
                        style = MacroAction.Print.Style.Error
                    )
                )
            ),
            MacroAction.Set(
                set = mapOf(
                    DefaultMacroVariables.Command.label to StringWithPlaceholders("")
                )
            )
        )
    )
}
