package de.jonasbroeckmann.nav.app.macros

object DefaultMacros {
    val RunCommand = Macro(
        id = "navRunCommand",
        hidden = true,
        actions = MacroActions(
            MacroAction.RunCommand(command = DefaultMacroProperties.Command.property.symbol.placeholder),
            MacroAction.If(
                condition = MacroCondition.Not(
                    MacroCondition.Equal(
                        listOf(
                            DefaultMacroSymbols.ExitCode.placeholder,
                            StringWithPlaceholders("0")
                        )
                    )
                ),
                then = MacroActions(
                    MacroAction.Print(
                        print = StringWithPlaceholders("Received exit code ${DefaultMacroSymbols.ExitCode.placeholder}"),
                        style = MacroAction.Print.Style.Error
                    )
                )
            ),
            MacroAction.Set(
                set = mapOf(
                    DefaultMacroProperties.Command.property.symbol.name to StringWithPlaceholders("")
                )
            )
        )
    )
}
