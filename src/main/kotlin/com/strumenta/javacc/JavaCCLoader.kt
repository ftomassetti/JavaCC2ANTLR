package com.strumenta.javacc

import org.javacc.parser.*
import org.snt.inmemantlr.GenericParser
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

fun Expansion.process(lexerDefinitions: LexerDefinitions, namesToUncapitalize: List<String>): String {
    return when (this) {
        is Sequence -> {
            if (this.units.any { it !is Expansion }) {
                throw UnsupportedOperationException("Sequence element is not an Expansion")
            }
            this.units.map { (it as Expansion).process(lexerDefinitions, namesToUncapitalize) }.joinToString(separator = " ")
        }
        is Lookahead -> "" //println("Lookahead")
        is Choice -> {
            if (this.choices.any { it !is Expansion }) {
                throw UnsupportedOperationException("Choice element is not an Expansion")
            }
            "(" + this.choices.joinToString(separator = " | ") { (it as Expansion).process(lexerDefinitions, namesToUncapitalize) } + ")"
        }
        is RStringLiteral -> lexerDefinitions.ruleForImage(image)?.name ?: "\"$image\""
        is Action -> "" // println("Action")
        is NonTerminal -> this.name.uncapitalize() // println("NonTerminal ${this.name}")
        is ZeroOrOne -> "(${this.expansion.process(lexerDefinitions, namesToUncapitalize)})?"
        is ZeroOrMore -> "(${this.expansion.process(lexerDefinitions, namesToUncapitalize)})*"
        is OneOrMore -> "(${this.expansion.process(lexerDefinitions, namesToUncapitalize)})+"
        is TryBlock -> this.exp.process(lexerDefinitions, namesToUncapitalize)
        is RJustName -> this.label
        is REndOfFile -> "EOF"
        else -> throw UnsupportedOperationException("Not sure: ${this.javaClass.simpleName}")
    }
}

private fun Any.regExpDescriptorProcess() : String {
    return when (this) {
        is SingleCharacter -> "${this.ch.toRegExp()}"
        is CharacterRange -> "${this.left}-${this.right}"
        else -> throw UnsupportedOperationException("Not sure: ${this.javaClass.simpleName}")
    }
}

private fun Char.toRegExp(): String {
    if (this.toInt() == 12) {
        return "\\f"
    }
    return when (this) {
        '\\' -> "\\\\"
        ' ' -> " "
        //'\'' -> "\\'"
        '\r' -> "\\r"
        '\n' -> "\\n"
        '\t' -> "\\t"
        else -> if (this.isWhitespace() || this.isISOControl() || this.category == CharCategory.FORMAT) {
            return "\\u${String.format("%04X", this.toLong())}"
            } else {
                this.toString()
            }

    }
}

private fun String.toRegExp() = this.toCharArray().joinToString(separator = "") { it.toRegExp() }


private fun RegularExpression.tokenProcess() : String {
    return when (this) {
        is RCharacterList -> "${if (this.negated_list) "~" else ""}[" + this.descriptors.map { it!!.regExpDescriptorProcess() }.joinToString(separator = "") + "]"
        is RStringLiteral -> if (this.image == "'") "'\\''" else "'${this.image.toRegExp()}'"
        is RSequence -> {
            if (this.units.any { it !is RegularExpression }) {
                throw UnsupportedOperationException("Sequence element is not an RegularExpression")
            }
            this.units.joinToString(separator = " ") { (it as RegularExpression).tokenProcess() }
        }
        is RZeroOrMore -> "(${this.regexpr.tokenProcess()})*"
        is ROneOrMore -> "(${this.regexpr.tokenProcess()})+"
        is RZeroOrOne -> "(${this.regexpr.tokenProcess()})?"
        is RJustName -> this.label
        is RChoice -> {
            if (this.choices.any { it !is RegularExpression }) {
                throw UnsupportedOperationException("Sequence element is not an RegularExpression")
            }
            this.choices.map { (it as RegularExpression).tokenProcess() }.joinToString(separator = " | ")
        }
        else -> throw UnsupportedOperationException("Not sure: ${this.javaClass.simpleName}")
    }
}

private fun RegExprSpec.process(): String {
    return this.rexp.tokenProcess()
}

data class RuleDefinition(val name: String, val body: String, val action: String?) {
    fun generate() : String {
        val actionPostfix = if (action == null) "" else "-> $action"
        return "$name : $body $actionPostfix ;"
    }
}

val DEFAULT_MODE_NAME = "DEFAULT"

class LexerDefinitions(val name: String) {

    private val rulesByMode  = HashMap<String, MutableList<RuleDefinition>>()

    fun ruleForImage(image: String, mode: String = DEFAULT_MODE_NAME) : RuleDefinition? {
        return rulesByMode[mode]?.firstOrNull {
            it.body == "'$image'"
        }
    }

    fun addRuleDefinition(mode: String, ruleDefinition: RuleDefinition) {
        if (!rulesByMode.containsKey(mode)) {
            rulesByMode[mode] = LinkedList()
        }
        var ruleDefinitionCorrected = ruleDefinition
        if (ruleDefinition.name.isEmpty()) {
            if (rulesByMode[mode]!!.any { it.body == ruleDefinition.body }) {
                return
            }
            ruleDefinitionCorrected = ruleDefinition.copy(name = generateName(ruleDefinition.body, rulesByMode[mode]!!.map { it.name }.toSet()))
        }
        if (ruleDefinitionCorrected.name.startsWith("_")) {
            ruleDefinitionCorrected = ruleDefinitionCorrected.copy(name = "US_${ruleDefinitionCorrected.name}")
        }
        if (ruleDefinitionCorrected.name == ruleDefinitionCorrected.body) {
            return
        }
        if (ruleDefinitionCorrected.body == "~[]") {
            ruleDefinitionCorrected = ruleDefinitionCorrected.copy(body = ".")
        }
        rulesByMode[mode]!!.add(ruleDefinitionCorrected)
    }

    private fun generateName(body: String, usedNames: Set<String>) : String {
        throw UnsupportedOperationException(body)
    }

    fun generate() : String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        printWriter.println("lexer grammar $name;")
        printMode(DEFAULT_MODE_NAME, printWriter)
        rulesByMode.keys.filter { it != DEFAULT_MODE_NAME }.forEach { printMode(it, printWriter) }
        return stringWriter.toString()
    }

    private fun printMode(mode: String, printWriter: PrintWriter) {
        printWriter.println()
        if (mode != DEFAULT_MODE_NAME) {
            printWriter.println("mode $mode;")
        }
        rulesByMode[mode]!!.forEach {
            printWriter.println(it.generate())
        }
    }
}

class ParserDefinitions(val name: String) {

    private val rules = LinkedList<RuleDefinition>()

    fun generate(lexerName: String? = null): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        printWriter.println("parser grammar $name;")
        if (lexerName != null) {
            printWriter.println()
            printWriter.println("options { tokenVocab=$lexerName; }")
        }
        rules.forEach {
            printWriter.println()

            printWriter.println(it.generate())
        }
        return stringWriter.toString()
    }

    fun addRuleDefinition(ruleDefinition: RuleDefinition) {
        rules.add(ruleDefinition)
    }
}

private fun RegExprSpec.toRuleDefinition(lexerState:String, action: String? = null) : RuleDefinition{
    val prefix = if (lexerState== DEFAULT_MODE_NAME) "" else "${lexerState}_"
    return RuleDefinition(prefix + rexp.label, rexp.tokenProcess(), action)
}

fun generateParserDefinitions(name: String, rulesDefinitions: List<NormalProduction>, lexerDefinitions: LexerDefinitions) : ParserDefinitions {
    val parserDefinitions = ParserDefinitions(name)
    val namesToUncapitalize = rulesDefinitions.map { it.lhs }

    rulesDefinitions.forEach {
        parserDefinitions.addRuleDefinition(RuleDefinition(it.lhs.uncapitalize(), it.expansion.process(lexerDefinitions, namesToUncapitalize), null))
    }

    return parserDefinitions
}

private fun String.uncapitalize(): String {
    return if (this.isNotEmpty() && this[0].isUpperCase()) {
        this[0].toLowerCase() + this.substring(1)
    } else {
        this
    }
}

fun generateLexerDefinitions(name: String, tokenDefinitions: List<TokenProduction>) : LexerDefinitions {
    val lexerDefinitions = LexerDefinitions(name)
    tokenDefinitions.forEach {
        val lexStates = it.lexStates
        val kindImage = TokenProduction.kindImage[it.kind]
        when (kindImage) {
            "SPECIAL" -> {
                it.respecs.forEach {
                    lexStates.forEach { ls ->
                        val action = if (ls == DEFAULT_MODE_NAME) "skip" else "popMode"
                        lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(ls, action)) }
                }
            }
            "MORE" -> {
                it.respecs.forEach {
                    if (it.nextState != null) {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(ls, "pushMode(${it.nextState})")) }
                    } else {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(ls)) }
                    }
                }
            }
            "TOKEN" -> {
                it.respecs.forEach {
                    if (it.nextState != null) {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(ls, "pushMode(${it.nextState})")) }
                    } else {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(ls)) }
                    }
                }
            }
            else -> throw UnsupportedOperationException(kindImage)
        }
    }
    return lexerDefinitions
}

fun main(args: Array<String>) {
    val file = File("src/test/resources/java.jj")
    val javaccParser = JavaCCParser(FileInputStream(file))
    Options.init()
    javaccParser.javacc_input()

    val tokenDefinitions = JavaCCGlobals.rexprlist

    val productionsByName = JavaCCGlobals.bnfproductions.associateBy( {it.lhs}, {it} )

    val lexerDefinitions = generateLexerDefinitions("JavaLexer", JavaCCGlobals.rexprlist)
    val lexerCode = lexerDefinitions.generate()
    //println(lexerCode)

    val parserCode = generateParserDefinitions("JavaParser", JavaCCGlobals.bnfproductions, lexerDefinitions).generate("JavaLexer")

    File("JavaLexer.g4").printWriter().use { out -> out.print(lexerCode) }
    File("JavaParser.g4").printWriter().use { out -> out.print(parserCode) }

    val genericParser = GenericParser(lexerCode, parserCode)
    genericParser.compile()
    //genericParser.parse("class A { }")

//    productionsByName.forEach {
//        try {
//            println("${it.key} : ${it.value.expansion.process()} ;")
//        } catch (e: UnsupportedOperationException) {
//            System.err.println("Issue processing ${it.key}: ${e.message}")
//        }
//    }
}
