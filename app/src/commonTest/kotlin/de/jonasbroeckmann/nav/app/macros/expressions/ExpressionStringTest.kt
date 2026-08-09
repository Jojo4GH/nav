package de.jonasbroeckmann.nav.app.macros.expressions

import de.jonasbroeckmann.nav.app.macros.ParserException
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression.Operator
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ExpressionStringTest : FunSpec({
    test("empty") {
        val exception = shouldThrow<ParserException> {
            ExpressionString.Empty.tryEvaluateScopeless().shouldNotBeNull()
        }
        exception.message shouldBe "Expression strings must not be empty"
    }
    test("key") {
        val expression = ExpressionString("foo").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression("foo")
    }
    test("key with spaces") {
        val expression = ExpressionString(" foobar ").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression("foobar")
    }
    test("property access") {
        val expression = ExpressionString("foo.bar").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression(Operator.Key("foo"), Operator.Key("bar"))
    }
    test("property access with spaces") {
        val expression = ExpressionString(" foo . barbaz ").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression(Operator.Key("foo"), Operator.Key("barbaz"))
    }
    test("index access") {
        val expression = ExpressionString("foo[42]").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression(Operator.Key("foo"), Operator.Index(42))
    }
    test("function call") {
        val expression = ExpressionString("size(foo)").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression(Operator.Key("foo"), Operator.Function("size"))
    }
    test("unknown function call") {
        val exception = shouldThrow<ParserException> {
            ExpressionString("bar(foo)").tryEvaluateScopeless().shouldNotBeNull()
        }
        exception.message shouldBe "Unknown function: bar"
    }
    test("multiple expressions") {
        val expression = ExpressionString("size(foo.bar[42].foo2).foo3").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression(
            Operator.Key("foo"),
            Operator.Key("bar"),
            Operator.Index(42),
            Operator.Key("foo2"),
            Operator.Function("size"),
            Operator.Key("foo3")
        )
    }
    test("known storage type") {
        val expression = ExpressionString("local:foo.bar").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression(MacroValueStorageType.Local, Operator.Key("foo"), Operator.Key("bar"))
    }
    test("custom storage type") {
        val expression = ExpressionString("storage:foo").tryEvaluateScopeless().shouldNotBeNull()
        expression shouldBe MacroExpression(MacroValueStorageType.Custom("storage"), "foo")
    }
    test("invalid expression") {
        val exception = shouldThrow<ParserException> {
            ExpressionString("foo.bar[42").tryEvaluateScopeless().shouldNotBeNull()
        }
        exception.message shouldBe "Unmatched token at offset=7, when expected: Token(EOF)"
    }
})
