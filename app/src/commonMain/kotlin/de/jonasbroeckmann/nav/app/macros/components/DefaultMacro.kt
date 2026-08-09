package de.jonasbroeckmann.nav.app.macros.components

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.MacroProvider
import de.jonasbroeckmann.nav.app.macros.components.MacroAction.*
import de.jonasbroeckmann.nav.app.macros.components.MacroCondition.*
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.templates.TemplateString
import de.jonasbroeckmann.nav.config.StyleString.Companion.styleString
import de.jonasbroeckmann.nav.config.Styles

sealed class DefaultMacro(
    val macro: Macro
) {
    object RunCommand : DefaultMacro(
        Macro(
            id = "nav_runCommand",
            actions = MacroActions(
                RunCommand(command = KnownMacroProperty.Command.templateString),
                If(
                    condition = NotEqual(
                        DefaultMacroExpressions.ExitCode.templateString,
                        TemplateString("0")
                    ),
                    then = MacroActions(
                        Print(
                            print = TemplateString("Received exit code ${DefaultMacroExpressions.ExitCode}"),
                            style = Print.Style.Error
                        )
                    )
                ),
                Set(KnownMacroProperty.Command.expressionString to TemplateString.Empty)
            )
        )
    )

    object NewFile : DefaultMacro(
        Macro(
            id = "nav_newFile",
            description = TemplateString("new file: ${KnownMacroProperty.Filter}"),
            style = Styles::file.styleString,
            menuOrder = 200,
            condition = All(
                NotBlank(KnownMacroProperty.Filter.templateString),
                NotExists(KnownMacroProperty.Filter.templateString)
            ),
            actions = MacroActions(
                WriteFile(writeFile = KnownMacroProperty.Filter.templateString),
                Set(KnownMacroProperty.Filter.expressionString to TemplateString.Empty)
            )
        )
    )

    object NewDirectory : DefaultMacro(
        Macro(
            id = "nav_newDirectory",
            description = TemplateString("new directory: ${KnownMacroProperty.Filter}"),
            style = Styles::directory.styleString,
            menuOrder = 210,
            condition = All(
                NotBlank(KnownMacroProperty.Filter.templateString),
                NotExists(KnownMacroProperty.Filter.templateString)
            ),
            actions = MacroActions(
                CreateDirectory(createDirectory = KnownMacroProperty.Filter.templateString),
                Set(KnownMacroProperty.Filter.expressionString to TemplateString.Empty)
            )
        )
    )

    object Rename : DefaultMacro(
        Macro(
            id = "nav_rename",
            description = TemplateString("rename ${KnownMacroProperty.EntryName}"),
            menuOrder = 250,
            condition = NotEmpty(KnownMacroProperty.EntryName.templateString),
            actions = run {
                val newNameVar = MacroExpression("nav_rename_newName")
                MacroActions(
                    Prompt(
                        prompt = TemplateString("New name:"),
                        format = Regex("""[^:*?"<>|]+"""),
                        default = KnownMacroProperty.EntryName.templateString,
                        resultTo = newNameVar.expressionString
                    ),
                    If(
                        condition = Exists(newNameVar.templateString),
                        then = MacroActions(
                            Prompt(
                                prompt = TemplateString(
                                    """
                                    $newNameVar already exists.
                                    Do you want to overwrite it?
                                    """.trimIndent()
                                ),
                                choices = listOf(
                                    TemplateString("No"),
                                    TemplateString("Yes")
                                ),
                                default = TemplateString("No"),
                                onChoice = mapOf(
                                    TemplateString("No") to MacroActions(Return())
                                )
                            ),
                            Move(
                                move = KnownMacroProperty.EntryPath.templateString,
                                to = newNameVar.templateString,
                                overwrite = true,
                            )
                        ),
                        otherwise = MacroActions(
                            Move(
                                move = KnownMacroProperty.EntryPath.templateString,
                                to = newNameVar.templateString,
                            )
                        )
                    )
                )
            }
        )
    )

    object Delete : DefaultMacro(
        Macro(
            id = "nav_delete",
            description = TemplateString("delete ${KnownMacroProperty.EntryName}"),
            key = KeyboardEvent("Delete"),
            menuOrder = 300,
            condition = NotEmpty(KnownMacroProperty.EntryName.templateString),
            actions = run {
                val childrenVar = MacroExpression("nav_delete_children")
                val promptVar = MacroExpression("nav_delete_prompt")
                MacroActions(
                    If(
                        condition = IsDirectory(KnownMacroProperty.EntryPath.templateString),
                        then = MacroActions(
                            ChildrenOf(
                                childrenOf = KnownMacroProperty.EntryPath.templateString,
                                resultTo = childrenVar.expressionString
                            ),
                            If(
                                condition = NotEmpty(childrenVar.templateString),
                                then = MacroActions(
                                    Prompt(
                                        prompt = TemplateString(
                                            """
                                            The directory ${KnownMacroProperty.EntryName} is not empty.
                                            Do you want to delete it recursively?
                                            """.trimIndent()
                                        ),
                                        choices = listOf(
                                            TemplateString("No"),
                                            TemplateString("Yes")
                                        ),
                                        default = TemplateString("No"),
                                        resultTo = promptVar.expressionString
                                    ),
                                    If(
                                        condition = NotEqual(
                                            promptVar.templateString,
                                            TemplateString("Yes")
                                        ),
                                        then = MacroActions(
                                            Return()
                                        )
                                    ),
                                    Delete(
                                        delete = KnownMacroProperty.EntryPath.templateString,
                                        recursive = true
                                    )
                                ),
                                otherwise = MacroActions(
                                    Delete(delete = KnownMacroProperty.EntryPath.templateString)
                                )
                            )
                        ),
                        otherwise = MacroActions(
                            Delete(delete = KnownMacroProperty.EntryPath.templateString)
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
