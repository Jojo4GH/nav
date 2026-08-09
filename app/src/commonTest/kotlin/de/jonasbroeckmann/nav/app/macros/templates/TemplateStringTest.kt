package de.jonasbroeckmann.nav.app.macros.templates

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TemplateStringTest : FunSpec({
    test("empty") {
        val parsed = TemplateString.Empty.parsed()
        parsed shouldBe MacroTemplate()
    }
    test("text only") {
        val parsed = TemplateString("""Hello world!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello world!")
        )
    }
    test("text only with backslash") {
        val parsed = TemplateString("""Hello \world!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello \\world!")
        )
    }
    test("placeholder only") {
        val parsed = TemplateString("""{{foo}}""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Placeholder("foo")
        )
    }
    test("text and placeholder") {
        val parsed = TemplateString("""Hello {{foo}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello "),
            MacroTemplate.Placeholder("foo"),
            MacroTemplate.Text("!")
        )
    }
    test("placeholder and escaping 1") {
        val parsed = TemplateString("""Hello \{{{foo}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {"),
            MacroTemplate.Placeholder("foo"),
            MacroTemplate.Text("!")
        )
    }
    test("placeholder and escaping 2") {
        val parsed = TemplateString("""Hello {{\{foo}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello "),
            MacroTemplate.Placeholder("{foo"),
            MacroTemplate.Text("!")
        )
    }
    test("placeholder and escaping 3") {
        val parsed = TemplateString("""Hello {{foo\}}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello "),
            MacroTemplate.Placeholder("foo}"),
            MacroTemplate.Text("!")
        )
    }
    test("placeholder and escaping 4") {
        val parsed = TemplateString("""Hello {{foo}}\}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello "),
            MacroTemplate.Placeholder("foo"),
            MacroTemplate.Text("}!")
        )
    }
    test("placeholder and escaping 5") {
        val parsed = TemplateString("""Hello \\{{foo}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello \\"),
            MacroTemplate.Placeholder("foo"),
            MacroTemplate.Text("!")
        )
    }
    test("placeholder and brace 1") {
        val parsed = TemplateString("""Hello {{{foo}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {"),
            MacroTemplate.Placeholder("foo"),
            MacroTemplate.Text("!")
        )
    }
    test("placeholder and brace 2") {
        val parsed = TemplateString("""Hello {{foo}}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello "),
            MacroTemplate.Placeholder("foo"),
            MacroTemplate.Text("}!")
        )
    }
    test("fully escaped placeholder") {
        val parsed = TemplateString("""Hello \{\{foo\}\}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {{foo}}!")
        )
    }
    test("escaped placeholder 1") {
        val parsed = TemplateString("""Hello \{{foo}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {{foo}}!")
        )
    }
    test("escaped placeholder 2") {
        val parsed = TemplateString("""Hello {\{foo}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {{foo}}!")
        )
    }
    test("escaped placeholder 3") {
        val parsed = TemplateString("""Hello {{foo\}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {{foo}}!")
        )
    }
    test("escaped placeholder 4") {
        val parsed = TemplateString("""Hello {{foo}\}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {{foo}}!")
        )
    }
    test("single braces") {
        val parsed = TemplateString("""Hello {foo}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {foo}!")
        )
    }
    test("double and single braces") {
        val parsed = TemplateString("""Hello {{foo}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {{foo}!")
        )
    }
    test("single and double braces") {
        val parsed = TemplateString("""Hello {foo}}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {foo}}!")
        )
    }
    test("escaped left brace") {
        val parsed = TemplateString("""Hello \{foo}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {foo}!")
        )
    }
    test("escaped right brace") {
        val parsed = TemplateString("""Hello {foo\}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {foo}!")
        )
    }
    test("escaped braces") {
        val parsed = TemplateString("""Hello \{foo\}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {foo}!")
        )
    }
    test("left brace") {
        val parsed = TemplateString("""Hello {!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {!")
        )
    }
    test("left braces") {
        val parsed = TemplateString("""Hello {{!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello {{!")
        )
    }
    test("right brace") {
        val parsed = TemplateString("""Hello }!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello }!")
        )
    }
    test("right braces") {
        val parsed = TemplateString("""Hello }}!""").parsed()
        parsed shouldBe MacroTemplate(
            MacroTemplate.Text("Hello }}!")
        )
    }
    context("nested") {
        test("placeholder") {
            val parsed = TemplateString("""Hello {{foo{{bar}}foo}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello "),
                MacroTemplate.Placeholder(MacroTemplate.Text("foo"), MacroTemplate.Placeholder("bar"), MacroTemplate.Text("foo")),
                MacroTemplate.Text("!")
            )
        }
        test("placeholder start") {
            val parsed = TemplateString("""Hello {{{{bar}}foo}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello "),
                MacroTemplate.Placeholder(MacroTemplate.Placeholder("bar"), MacroTemplate.Text("foo")),
                MacroTemplate.Text("!")
            )
        }
        test("placeholder end") {
            val parsed = TemplateString("""Hello {{foo{{bar}}}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello "),
                MacroTemplate.Placeholder(MacroTemplate.Text("foo"), MacroTemplate.Placeholder("bar")),
                MacroTemplate.Text("!")
            )
        }
        test("placeholder start and end") {
            val parsed = TemplateString("""Hello {{{{bar}}}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello "),
                MacroTemplate.Placeholder(MacroTemplate.Placeholder("bar")),
                MacroTemplate.Text("!")
            )
        }
        test("braces in expression 1") {
            val parsed = TemplateString("""Hello {{foo{bar}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello {{foo{bar}}!")
            )
        }
        test("braces in expression 2") {
            val parsed = TemplateString("""Hello {{foo}bar}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello {{foo}bar}}!")
            )
        }
        test("braces in expression 3") {
            val parsed = TemplateString("""Hello {{foo{{bar}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello {{foo"),
                MacroTemplate.Placeholder("bar"),
                MacroTemplate.Text("!")
            )
        }
        test("braces in expression 4") {
            val parsed = TemplateString("""Hello {{foo}}bar}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello "),
                MacroTemplate.Placeholder("foo"),
                MacroTemplate.Text("bar}}!")
            )
        }
        test("escaped braces in expression 1") {
            val parsed = TemplateString("""Hello {{foo\{bar}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello "),
                MacroTemplate.Placeholder("foo{bar"),
                MacroTemplate.Text("!")
            )
        }
        test("escaped braces in expression 2") {
            val parsed = TemplateString("""Hello {{foo\}bar}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello "),
                MacroTemplate.Placeholder("foo}bar"),
                MacroTemplate.Text("!")
            )
        }
        test("escaped braces in expression 3") {
            val parsed = TemplateString("""Hello {{foo\{{bar}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello {{foo{{bar}}!")
            )
        }
        test("escaped braces in expression 4") {
            val parsed = TemplateString("""Hello {{foo\}}bar}}!""").parsed()
            parsed shouldBe MacroTemplate(
                MacroTemplate.Text("Hello {{foo}}bar}}!")
            )
        }
    }
})
