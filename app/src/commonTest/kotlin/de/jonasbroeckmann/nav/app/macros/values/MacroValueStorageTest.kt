package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.TestSpec
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression.Operator
import io.kotest.matchers.shouldBe
import io.ktor.client.request.invoke

class MacroValueStorageTest : TestSpec({
    test("empty storage") {
        val storage = InMemoryMacroValueStorage()
        storage.value() shouldBe MacroValue.Dictionary()
        storage[MacroPathExpression("foo")] shouldBe null
    }
    test("set and get") {
        val storage = InMemoryMacroValueStorage()
        storage[MacroPathExpression("foo")] = MacroValue.Text("bar")
        storage.value() shouldBe MacroValue.Dictionary("foo" to MacroValue.Text("bar"))
        storage[MacroPathExpression("foo")] shouldBe MacroValue.Text("bar")
        storage[MacroPathExpression(Operator.Key("foo"), Operator.Key("bar"))] shouldBe null
    }
    test("update") {
        val storage = InMemoryMacroValueStorage()
        storage[MacroPathExpression("foo")] = MacroValue.Text("bar")
        storage[MacroPathExpression("foo2")] = MacroValue.Text("bar2")
        storage[MacroPathExpression("foo")] = MacroValue.Text("baz")
        storage[MacroPathExpression("foo2")] = MacroValue.Dictionary("k1" to MacroValue.Text("v1"))
        storage.value() shouldBe MacroValue.Dictionary(
            "foo" to MacroValue.Text("baz"),
            "foo2" to MacroValue.Dictionary("k1" to MacroValue.Text("v1"))
        )
    }
    test("unset") {
        val storage = InMemoryMacroValueStorage()
        storage[MacroPathExpression("foo")] = MacroValue.Text("bar")
        storage[MacroPathExpression("foo2")] = MacroValue.Text("bar2")
        storage[MacroPathExpression("foo")] = null
        storage.value() shouldBe MacroValue.Dictionary("foo2" to MacroValue.Text("bar2"))
    }
    context("dictionary") {
        test("nested dictionary") {
            val storage = InMemoryMacroValueStorage()
            storage[MacroPathExpression(Operator.Key("foo"), Operator.Key("bar"), Operator.Key("baz"))] = MacroValue.Text("qux")
            storage.value() shouldBe MacroValue.Dictionary(
                "foo" to MacroValue.Dictionary(
                    "bar" to MacroValue.Dictionary(
                        "baz" to MacroValue.Text("qux")
                    )
                )
            )
        }
    }
    context("arrays") {
        test("implicit creation") {
            val storage = InMemoryMacroValueStorage()
            storage[MacroPathExpression(Operator.Key("foo"), Operator.Index(0))] = MacroValue.Text("qux")
            storage.value() shouldBe MacroValue.Dictionary(
                "foo" to MacroValue.Array(
                    MacroValue.Text("qux")
                )
            )
        }
        test("implicit creation with advanced index") {
            val storage = InMemoryMacroValueStorage()
            storage[MacroPathExpression(Operator.Key("foo"), Operator.Index(2))] = MacroValue.Text("qux")
            storage.value() shouldBe MacroValue.Dictionary(
                "foo" to MacroValue.Array(
                    null,
                    null,
                    MacroValue.Text("qux")
                )
            )
        }
    }
    context("functions") {
        test("last") {
            val storage = InMemoryMacroValueStorage()
            storage[MacroPathExpression("foo")] = MacroValue.Array(List(10) { MacroValue.Text("bar$it") })
            storage[MacroPathExpression(Operator.Key("foo"), Operator.Function.Last)] shouldBe MacroValue.Text("bar9")
        }
        test("last on dictionary") {
            val storage = InMemoryMacroValueStorage()
            storage[MacroPathExpression("foo")] = MacroValue.Dictionary("k1" to MacroValue.Text("v1"))
            storage[MacroPathExpression(Operator.Key("foo"), Operator.Function.Last)] shouldBe null
        }
        test("type") {
            val storage = InMemoryMacroValueStorage()
            storage[MacroPathExpression("myText")] = MacroValue.Text("bar")
            storage[MacroPathExpression("myList")] = MacroValue.Array()
            storage[MacroPathExpression("myDict")] = MacroValue.Dictionary()
            storage[MacroPathExpression(Operator.Key("myText"), Operator.Function.Type)] shouldBe MacroValue.Text("text")
            storage[MacroPathExpression(Operator.Key("myList"), Operator.Function.Type)] shouldBe MacroValue.Text("list")
            storage[MacroPathExpression(Operator.Key("myDict"), Operator.Function.Type)] shouldBe MacroValue.Text("dictionary")
        }
    }
})
