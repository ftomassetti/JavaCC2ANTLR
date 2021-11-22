@file:JvmName("JavaCCToAntlrConverter")
package com.strumenta.javacc

import org.javacc.parser.*
import java.io.File

private fun Expansion.process(lexerDefinitions: LexerDefinitions, namesToUncapitalize: List<String>): String {
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

val DEFAULT_MODE_NAME = "DEFAULT"

private fun RegExprSpec.toRuleDefinition(lexerState:String, action: String? = null) : RuleDefinition {
    val prefix = if (lexerState== DEFAULT_MODE_NAME) "" else "${lexerState}_"
    return RuleDefinition(prefix + rexp.label, rexp.tokenProcess(), action, fragment=this.rexp.private_rexp)
}

private fun generateParserDefinitions(name: String, rulesDefinitions: List<NormalProduction>, lexerDefinitions: LexerDefinitions) : ParserDefinitions {
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

private fun generateLexerDefinitions(name: String, tokenDefinitions: List<TokenProduction>) : LexerDefinitions {
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
                        val action = if (it.nextState == DEFAULT_MODE_NAME) "popMode" else  "pushMode(${it.nextState})"
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(ls, action)) }
                    } else {
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(ls)) }
                    }
                }
            }
            "TOKEN" -> {
                it.respecs.forEach {
                    if (it.nextState != null) {
                        val action = if (it.nextState == DEFAULT_MODE_NAME) "popMode" else  "pushMode(${it.nextState})"
                        lexStates.forEach { ls -> lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(ls, action)) }
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

fun JavaCCGrammar.convertToAntlr(name: String) : AntlrGrammar {
    val lexerDefinitions = generateLexerDefinitions("${name}Lexer", this.tokenRules)
    val parserDefinitions = generateParserDefinitions("${name}Parser", this.parserRules, lexerDefinitions)
    return AntlrGrammar(lexerDefinitions, parserDefinitions)
}

fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("Specify the name of the JavaCC grammar to load")
        return
    }
    val file = File(args[0])
    val grammarName = file.nameWithoutExtension.capitalize()

    val javaCCGrammar = loadJavaCCGrammar(file)
    val antlrGrammar = javaCCGrammar.convertToAntlr(grammarName)

    antlrGrammar.saveLexer(File("${grammarName}Lexer.g4"))
    antlrGrammar.saveParser(File("${grammarName}Parser.g4"))
}