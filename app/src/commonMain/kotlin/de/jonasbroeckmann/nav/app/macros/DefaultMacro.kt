package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.MacroAction.*
import de.jonasbroeckmann.nav.app.macros.MacroCondition.*
import de.jonasbroeckmann.nav.config.StyleString.Companion.styleString
import de.jonasbroeckmann.nav.config.Styles

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

    object NewFile : DefaultMacro(
        Macro(
            id = "nav:newFile",
            description = StringWithPlaceholders("new file: ${DefaultMacroProperty.Filter}"),
            style = Styles::file.styleString,
            menuOrder = 200,
            condition = All(
                NotBlank(DefaultMacroProperty.Filter.placeholder),
                NotExists(DefaultMacroProperty.Filter.placeholder)
            ),
            actions = MacroActions(
                WriteFile(writeFile = DefaultMacroProperty.Filter.placeholder),
                Set(DefaultMacroProperty.Filter.symbol.name to StringWithPlaceholders.Empty)
            )
        )
    )

    object NewDirectory : DefaultMacro(
        Macro(
            id = "nav:newDirectory",
            description = StringWithPlaceholders("new directory: ${DefaultMacroProperty.Filter}"),
            style = Styles::directory.styleString,
            menuOrder = 210,
            condition = All(
                NotBlank(DefaultMacroProperty.Filter.placeholder),
                NotExists(DefaultMacroProperty.Filter.placeholder)
            ),
            actions = MacroActions(
                CreateDirectory(createDirectory = DefaultMacroProperty.Filter.placeholder),
                Set(DefaultMacroProperty.Filter.symbol.name to StringWithPlaceholders.Empty)
            )
        )
    )

    object Rename : DefaultMacro(
        Macro(
            id = "nav:rename",
            description = StringWithPlaceholders("rename ${DefaultMacroProperty.EntryName}"),
            menuOrder = 250,
            condition = NotEmpty(DefaultMacroProperty.EntryName.placeholder),
            actions = run {
                val newNameVar = MacroSymbol.Generic("nav:rename:newName")
                MacroActions(
                    Prompt(
                        prompt = StringWithPlaceholders("New name:"),
                        format = Regex("""[^:*?"<>|]+"""),
                        default = DefaultMacroProperty.EntryName.placeholder,
                        resultTo = newNameVar.name
                    ),
                    If(
                        condition = Exists(newNameVar.placeholder),
                        then = MacroActions(
                            Prompt(
                                prompt = StringWithPlaceholders(
                                    """
                                    ${newNameVar.placeholder} already exists.
                                    Do you want to overwrite it?
                                    """.trimIndent()
                                ),
                                choices = listOf(
                                    StringWithPlaceholders("No"),
                                    StringWithPlaceholders("Yes")
                                ),
                                default = StringWithPlaceholders("No"),
                                onChoice = mapOf(
                                    StringWithPlaceholders("No") to MacroActions(Return())
                                )
                            ),
                            Move(
                                move = DefaultMacroProperty.EntryPath.placeholder,
                                to = newNameVar.placeholder,
                                overwrite = true,
                            )
                        ),
                        otherwise = MacroActions(
                            Move(
                                move = DefaultMacroProperty.EntryPath.placeholder,
                                to = newNameVar.placeholder,
                            )
                        )
                    )
                )
            }
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

    context(context: FullContext)
    fun get(): Macro = macro.id?.let { context.macroById(it) } ?: macro

    companion object : MacroProvider {
        override val macros = listOf(
            RunCommand.macro,
            NewFile.macro,
            NewDirectory.macro,
            Rename.macro,
            Delete.macro,
        )
    }
}
