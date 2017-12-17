package com.strumenta.javacc

import org.javacc.parser.*
import java.io.File
import java.io.FileInputStream
import java.util.*

fun Expansion.process() : String {
    return when (this) {
        is Sequence -> {
            if (this.units.any { it !is Expansion }) {
                throw UnsupportedOperationException("Sequence element is not an Expansion")
            }
            this.units.map { (it as Expansion).process() }.joinToString(separator = " ")
        }
        is Lookahead -> "" //println("Lookahead")
        is Choice -> {
            if (this.choices.any { it !is Expansion }) {
                throw UnsupportedOperationException("Choice element is not an Expansion")
            }
            "(" + this.choices.joinToString(separator = " | ") { (it as Expansion).process() } + ")"
        }
        is RStringLiteral -> "\"$image\""
        is Action -> "" // println("Action")
        is NonTerminal -> this.name // println("NonTerminal ${this.name}")
        is ZeroOrOne -> "(${expansion.process()})?"
        is ZeroOrMore -> "(${expansion.process()})*"
        is OneOrMore -> "(${expansion.process()})+"
        is TryBlock -> this.exp.process()
        is RJustName -> this.label
        is REndOfFile -> "EOF"
        else -> throw UnsupportedOperationException("Not sure: ${this.javaClass.simpleName}")
    }
}

private fun Any.regExpDescriptorProcess() : String {
    return when (this) {
        is SingleCharacter -> "'${this.ch.toRegExp()}'"
        is CharacterRange -> "${this.left}-${this.right}"
        else -> throw UnsupportedOperationException("Not sure: ${this.javaClass.simpleName}")
    }
}

private fun Char.toRegExp(): String {
    if (this.toInt() == 12) {
        return "\\f"
    }
    return when (this) {
        ' ' -> " "
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

private fun String.toRegExp() = this.toCharArray().map { it.toRegExp() }.joinToString(separator = "")


private fun RegularExpression.tokenProcess() : String {
    return when (this) {
        is RCharacterList -> "${if (this.negated_list) "~" else ""}[" + this.descriptors.map { it!!.regExpDescriptorProcess() }.joinToString(separator = " ") + "]"
        is RStringLiteral -> "\"${this.image.toRegExp()}\""
        is RSequence -> {
            if (this.units.any { it !is RegularExpression }) {
                throw UnsupportedOperationException("Sequence element is not an RegularExpression")
            }
            this.units.map { (it as RegularExpression).tokenProcess() }.joinToString(separator = " ")
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

class RuleDefinition(val name: String, val body: String, val action: String?) {
    fun generate() : String {
        val actionPostfix = if (action == null) "" else "-> $action"
        return "$name : $body $actionPostfix ;"
    }
}

val DEFAULT_MODE_NAME = "DEFAULT"

class LexerDefinitions(val name: String) {

    private val rulesByMode  = HashMap<String, MutableList<RuleDefinition>>()

    fun addRuleDefinition(mode: String, ruleDefinition: RuleDefinition) {
        if (!rulesByMode.containsKey(mode)) {
            rulesByMode[mode] = LinkedList<RuleDefinition>()
        }
        rulesByMode[mode]!!.add(ruleDefinition)
    }

    fun generate() {
        println("lexer grammar $name")
        printMode(DEFAULT_MODE_NAME)
        rulesByMode.keys.filter { it != DEFAULT_MODE_NAME }.forEach { printMode(it) }
    }

    private fun printMode(mode: String) {
        println()
        println("// Mode $mode")
        rulesByMode[mode]!!.forEach {
            println(it.generate())
        }
    }
}

private fun RegExprSpec.toRuleDefinition(action: String? = null) : RuleDefinition{
    return RuleDefinition(rexp.label, rexp.tokenProcess(), action)
}

fun generateLexerDefinitions(name: String, tokenDefinitions: List<TokenProduction>) : LexerDefinitions {
    val lexerDefinitions = LexerDefinitions(name)
    tokenDefinitions.forEach {
        val lexStates = it.lexStates
        val kindImage = TokenProduction.kindImage[it.kind]
        when (kindImage) {
            "SPECIAL" -> {
                it.respecs.forEach {
                    lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition("skip")) }
                }
            }
            "MORE" -> {
                it.respecs.forEach {
                    if (it.nextState != null) {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition("pushMode(${it.nextState})")) }
                    } else {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition()) }
                    }
                }
            }
            "TOKEN" -> {
                it.respecs.forEach {
                    if (it.nextState != null) {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition("pushMode(${it.nextState})")) }
                    } else {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition()) }
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

    generateLexerDefinitions("Java", JavaCCGlobals.rexprlist).generate()


//    productionsByName.forEach {
//        try {
//            println("${it.key} : ${it.value.expansion.process()} ;")
//        } catch (e: UnsupportedOperationException) {
//            System.err.println("Issue processing ${it.key}: ${e.message}")
//        }
//    }
}
