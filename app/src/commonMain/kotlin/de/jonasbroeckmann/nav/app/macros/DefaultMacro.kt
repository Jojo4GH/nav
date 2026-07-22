package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.MacroAction.*
import de.jonasbroeckmann.nav.app.macros.MacroCondition.*

sealed class DefaultMacro(
    val macro: Macro
) {
    object RunCommand : DefaultMacro(
        Macro(
            id = "nav:runCommand",
            actions = MacroActions(
                RunCommand(command = DefaultMacroProperty.Command.placeholder),
                If(
                    condition = Not(
                        Equal(
                            listOf(
                                DefaultMacroSymbols.ExitCode.placeholder,
                                StringWithPlaceholders("0")
                            )
                        )
                    ),
                    then = MacroActions(
                        Print(
                            print = StringWithPlaceholders("Received exit code ${DefaultMacroSymbols.ExitCode.placeholder}"),
                            style = Print.Style.Error
                        )
                    )
                ),
                Set(
                    set = mapOf(
                        DefaultMacroProperty.Command.symbol.name to StringWithPlaceholders("")
                    )
                )
            )
        )
    )

    object Delete : DefaultMacro(
        Macro(
            id = "nav:delete",
            description = StringWithPlaceholders("delete ${DefaultMacroProperty.EntryName.placeholder}"),
            key = KeyboardEvent("Delete"),
            menuOrder = 300,
            condition = NotEmpty(DefaultMacroProperty.EntryName.placeholder),
            actions = run {
                val childrenVar = MacroSymbol.Generic("nav:delete:children")
                val promptVar = MacroSymbol.Generic("nav:delete:prompt")
                MacroActions(
                    If(
                        condition = IsDirectory(DefaultMacroProperty.EntryPath.placeholder),
                        then = MacroActions(
                            ChildrenOf(
                                childrenOf = DefaultMacroProperty.EntryPath.placeholder,
                                resultTo = childrenVar.name
                            ),
                            If(
                                condition = NotEmpty(childrenVar.placeholder),
                                then = MacroActions(
                                    Prompt(
                                        prompt = StringWithPlaceholders(
                                            """
                                            The directory ${DefaultMacroProperty.EntryName.placeholder} is not empty.
                                            Do you want to delete it recursively?
                                            """.trimIndent()
                                        ),
                                        choices = listOf(
                                            StringWithPlaceholders("No"),
                                            StringWithPlaceholders("Yes")
                                        ),
                                        default = StringWithPlaceholders("No"),
                                        resultTo = promptVar.name
                                    ),
                                    If(
                                        condition = NotEqual(
                                            promptVar.placeholder,
                                            StringWithPlaceholders("Yes")
                                        ),
                                        then = MacroActions(
                                            Return()
                                        )
                                    ),
                                    Delete(
                                        delete = DefaultMacroProperty.EntryPath.placeholder,
                                        recursive = true
                                    )
                                ),
                                otherwise = MacroActions(
                                    Delete(delete = DefaultMacroProperty.EntryPath.placeholder)
                                )
                            )
                        ),
                        otherwise = MacroActions(
                            Delete(delete = DefaultMacroProperty.EntryPath.placeholder)
                        )
                    )
                )
            }
        )
    )

    object NewFile : DefaultMacro(
        Macro(
            id = "nav:newFile",
            description = StringWithPlaceholders("new file: ${DefaultMacroProperty.Filter}"),
            menuOrder = 200,
            condition = NotBlank(DefaultMacroProperty.Filter.placeholder),
            actions = MacroActions(
                WriteFile(
                    StringWithPlaceholders(
                        "${DefaultMacroProperty.Directory}${DefaultMacroProperty.Separator}${DefaultMacroProperty.Filter}"
                    )
                ),
                Set(
                    DefaultMacroProperty.Filter.symbol.name to StringWithPlaceholders.Empty
                )
            )
        )
    )

    context(context: FullContext)
    fun get(): Macro = macro.id?.let { context.macroById(it) } ?: macro

    companion object : MacroProvider {
        override val macros = listOf(
            RunCommand.macro,
            Delete.macro,
            NewFile.macro,
        )
    }
}
