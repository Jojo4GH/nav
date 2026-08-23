package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.TestSpec
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.components.MacroAction
import de.jonasbroeckmann.nav.app.macros.components.MacroActions
import de.jonasbroeckmann.nav.app.macros.components.MacroCallable
import de.jonasbroeckmann.nav.app.macros.expressions.ExpressionString
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.templates.TemplateString
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.withMainController
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe

class MacroScopeTest : TestSpec({
    test("local private and shared variables") {
        withMainController {
            val macro = Macro(
                id = "macro",
                actions = MacroActions(
                    MacroAction.Set(
                        ExpressionString("my_private_var") to TemplateString("Hello private"),
                        ExpressionString("local_shared:my_shared_var") to TemplateString("Hello shared"),
                    )
                )
            )
            MacroRunContext.testRun(macro) {
                get(MacroExpression(PrivateLocal, "my_private_var")) shouldBe null
                get(MacroExpression(SharedLocal, "my_private_var")) shouldBe null
                get(MacroExpression(PrivateLocal, "my_shared_var")) shouldBe null
                get(MacroExpression(SharedLocal, "my_shared_var")) shouldBe null
                macro.run()
                get(MacroExpression(PrivateLocal, "my_private_var")) shouldBe MacroValue.Text("Hello private")
                get(MacroExpression(SharedLocal, "my_private_var")) shouldBe null
                get(MacroExpression(PrivateLocal, "my_shared_var")) shouldBe null
                get(MacroExpression(SharedLocal, "my_shared_var")) shouldBe MacroValue.Text("Hello shared")
            }
        }
    }
    test("local private and shared variables with multiple") {
        withMainController {
            val macro = Macro(
                id = "macro",
                actions = MacroActions(
                    MacroAction.Set(
                        ExpressionString("my_private_var") to TemplateString("Hello private"),
                        ExpressionString("local_shared:my_shared_var") to TemplateString("Hello shared"),
                    ),
                    MacroAction.RunMacro(TemplateString("subMacro"))
                )
            )
            val subMacro = Macro(
                id = "subMacro",
                actions = MacroActions(
                    MacroAction.Set(
                        ExpressionString("my_private_var") to TemplateString("Hello private from sub"),
                        ExpressionString("local_shared:my_shared_var") to TemplateString("Hello shared from sub"),
                    )
                )
            )
            MacroRunContext.testRun(macro, subMacro) {
                macro.run()
                get(MacroExpression(PrivateLocal, "my_private_var")) shouldBe MacroValue.Text("Hello private")
                get(MacroExpression(SharedLocal, "my_private_var")) shouldBe null
                get(MacroExpression(PrivateLocal, "my_shared_var")) shouldBe null
                get(MacroExpression(SharedLocal, "my_shared_var")) shouldBe MacroValue.Text("Hello shared from sub")
            }
        }
    }
    test("session private and shared variables") {
        withMainController {
            val macro = Macro(
                id = "macro",
                actions = MacroActions(
                    MacroAction.Set(
                        ExpressionString("session:my_var") to TemplateString("{{session:my_var}}Hello private from outer"),
                        ExpressionString("session_shared:my_var") to TemplateString("{{session_shared:my_var}}Hello shared from outer"),
                    ),
                    MacroAction.RunMacro(TemplateString("subMacro"))
                )
            )
            val subMacro = Macro(
                id = "subMacro",
                actions = MacroActions(
                    MacroAction.Set(
                        ExpressionString("session:my_var") to TemplateString("{{session:my_var}}Hello private from inner"),
                        ExpressionString("session_shared:my_var") to TemplateString("{{session_shared:my_var}}Hello shared from inner"),
                    )
                )
            )
            MacroRunContext.testRun(macro, subMacro) {
                get(MacroExpression(PrivateSession, "my_var")) shouldBe null
                get(MacroExpression(SharedSession, "my_var")) shouldBe null
                macro.run()
                get(MacroExpression(PrivateSession, "my_var")) shouldBe MacroValue.Text(
                    "Hello private from outer"
                )
                get(MacroExpression(SharedSession, "my_var")) shouldBe MacroValue.Text(
                    "Hello shared from outer" +
                            "Hello shared from inner"
                )
                call(subMacro) {
                    get(MacroExpression(PrivateSession, "my_var")) shouldBe MacroValue.Text(
                        "Hello private from inner"
                    )
                    get(MacroExpression(SharedSession, "my_var")) shouldBe MacroValue.Text(
                        "Hello shared from outer" +
                                "Hello shared from inner"
                    )
                    subMacro.run()
                    get(MacroExpression(PrivateSession, "my_var")) shouldBe MacroValue.Text(
                        "Hello private from inner" +
                                "Hello private from inner"
                    )
                    get(MacroExpression(SharedSession, "my_var")) shouldBe MacroValue.Text(
                        "Hello shared from outer" +
                                "Hello shared from inner" +
                                "Hello shared from inner"
                    )
                }
                get(MacroExpression(PrivateSession, "my_var")) shouldBe MacroValue.Text(
                    "Hello private from outer"
                )
                get(MacroExpression(SharedSession, "my_var")) shouldBe MacroValue.Text(
                    "Hello shared from outer" +
                            "Hello shared from inner" +
                            "Hello shared from inner"
                )
            }
            MacroRunContext.testRun(macro, subMacro) {
                macro.run()
                get(MacroExpression(PrivateSession, "my_var")) shouldBe MacroValue.Text(
                    "Hello private from outer" +
                            "Hello private from outer"
                )
                get(MacroExpression(SharedSession, "my_var")) shouldBe MacroValue.Text(
                    "Hello shared from outer" +
                            "Hello shared from inner" +
                            "Hello shared from inner" +
                            "Hello shared from outer" +
                            "Hello shared from inner"
                )
            }
        }
    }
})

context(_: TestScope, _: MainController)
private fun MacroRunContext.Companion.testRun(
    macro: Macro,
    vararg additionalMacros: Macro,
    run: context(MacroTraceContext) MacroCallScope.() -> Unit
) {
    run(MacroCallable(macro) { contextOf<MacroCallScope>().run() }, additionalMacros = listOf(macro, *additionalMacros)) shouldBe true
}

context(_: MacroTraceContext)
private fun MacroCallScope.call(
    macro: Macro,
    run: context(MacroTraceContext) MacroCallScope.() -> Unit
) = call(
    parameters = emptyList(),
    capture = emptyList(),
    returnToRoot = false,
    callable = MacroCallable(macro) { contextOf<MacroCallScope>().run() }
)
