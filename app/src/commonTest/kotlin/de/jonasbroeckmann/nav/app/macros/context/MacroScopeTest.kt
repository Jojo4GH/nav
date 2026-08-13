package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.TestSpec
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.components.MacroAction
import de.jonasbroeckmann.nav.app.macros.components.MacroActions
import de.jonasbroeckmann.nav.app.macros.components.MacroCallable
import de.jonasbroeckmann.nav.app.macros.components.MacroRunnable
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
                        ExpressionString("sharedLocal:my_shared_var") to TemplateString("Hello shared"),
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
})

context(_: TestScope, _: MainController)
private fun MacroRunContext.Companion.testRun(macro: Macro, run: MacroRunnable.Action) {
    run(MacroCallable(macro, run)) shouldBe true
}

context(evaluationScope: MacroEvaluationScope)
private fun get(expression: MacroExpression) = evaluationScope[expression]
