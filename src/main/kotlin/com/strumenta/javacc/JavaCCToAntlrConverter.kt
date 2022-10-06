@file:JvmName("JavaCCToAntlrConverter")
package com.strumenta.javacc

import org.javacc.parser.*
import java.io.File
import java.util.*

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

private fun Any.toLexerCharSetRuleElement() : String {
    return when (this) {
        is SingleCharacter -> this.ch.toLexerRuleElement(LexerElement.CharSet)
        is CharacterRange -> "${this.left}-${this.right}"
        else -> throw UnsupportedOperationException("Not sure: ${this.javaClass.simpleName}")
    }
}

private enum class LexerElement { Literal, CharSet }

/**
 * Escapes characters per ANTLR lexer requirements https://github.com/antlr/antlr4/blob/master/doc/lexer-rules.md
 */
private fun Char.toLexerRuleElement(elementType: LexerElement): String {
    if (this.code == 12) {
        return "\\f"
    }

    val escapeLexerElementCommon = {
        when (this) {
            '\\' -> "\\\\"
            ' ' -> " "
            '\r' -> "\\r"
            '\n' -> "\\n"
            '\t' -> "\\t"
            '\b' -> "\\b"
            else -> if (this.isWhitespace() || this.isISOControl() || this.category == CharCategory.FORMAT) {
                "\\u${String.format("%04X", this.code.toLong())}"
            } else {
                this.toString()
            }
        }
    }

    return when (elementType) {
        LexerElement.Literal -> when (this) {
            '\'' -> "\\'"
            else -> escapeLexerElementCommon()
        }
        LexerElement.CharSet -> when (this) {
            ']' -> "\\]"
            '-' -> "\\-"
            else -> escapeLexerElementCommon()
        }
    }
}

data class DefinitionPart(var body: String, val literal: Boolean)
private fun convertDefinitionToCI(ruleDefinitionBody: String): List<DefinitionPart> {
    val definitionParts: MutableList<DefinitionPart> = mutableListOf()
    // Build up list of sequential (non-letter) literals and case-insensitive letter fragments
    // E.g. [('!', true), ('D O C T Y P E', false)] to represent HTML !doctype tag case-insensitively
    ruleDefinitionBody.map { it.toString() }.forEach { char ->
        if ((char in "a".."z" || char in "A".."Z")) {
            if (definitionParts.isEmpty() || definitionParts.last().literal) {
                definitionParts.add(DefinitionPart(char.uppercase(), false))
            } else {
                definitionParts.last().body = "${definitionParts.last().body} ${char.uppercase()}"
            }
        } else {
            if (definitionParts.isEmpty() || !definitionParts.last().literal) {
                definitionParts.add(DefinitionPart(char, true))
            } else {
                definitionParts.last().body = "${definitionParts.last().body}$char"
            }
        }
    }
    return definitionParts
}

private fun String.toLexerLiteralRuleElement(ignoreCase: Boolean): String {
    val definitionParts = if (ignoreCase) {
        convertDefinitionToCI(this)
    } else {
        listOf(DefinitionPart(this, true))
    }
    return definitionParts.joinToString(" ") {
        if (it.literal) {
            "'" + it.body.toCharArray().joinToString(separator = "") { it.toLexerRuleElement(LexerElement.Literal) } + "'"
        } else {
            it.body
        }
    }
}

private fun RegularExpression.tokenProcess(ignoreCase: Boolean) : String {
    return when (this) {
        is RCharacterList -> "${if (this.negated_list) "~" else ""}[" + this.descriptors.map { it!!.toLexerCharSetRuleElement() }.joinToString(separator = "") + "]"
        is RStringLiteral -> if (this.image == "'") "'\\''" else this.image.toLexerLiteralRuleElement(ignoreCase)
        is RSequence -> {
            if (this.units.any { it !is RegularExpression }) {
                throw UnsupportedOperationException("Sequence element is not an RegularExpression")
            }
            this.units.joinToString(separator = " ") { (it as RegularExpression).tokenProcess(ignoreCase) }
        }
        is RZeroOrMore -> "(${this.regexpr.tokenProcess(ignoreCase)})*"
        is ROneOrMore -> "(${this.regexpr.tokenProcess(ignoreCase)})+"
        is RZeroOrOne -> "(${this.regexpr.tokenProcess(ignoreCase)})?"
        is RJustName -> this.getAntlrTokenName()
        is RChoice -> {
            if (this.choices.any { it !is RegularExpression }) {
                throw UnsupportedOperationException("Sequence element is not an RegularExpression")
            }
            "(" + this.choices.map { (it as RegularExpression).tokenProcess(ignoreCase) }.joinToString(separator = " | ") + ")"
        }
        else -> throw UnsupportedOperationException("Not sure: ${this.javaClass.simpleName}")
    }
}

private fun RegularExpression.getAntlrTokenName(): String {
    return if (this.label.startsWith('_')) "US${this.label}" else this.label
}

const val JAVACC_DEFAULT_MODE_NAME = "DEFAULT"
const val ANTLR_DEFAULT_MODE_NAME = "DEFAULT_MODE"

private fun RegExprSpec.toRuleDefinition(name: String, body: String, commands: List<String>) : RuleDefinition {
    return RuleDefinition(name, body, commands.joinToString(", "), fragment=this.rexp.private_rexp)
}

private fun generateParserDefinitions(name: String, rulesDefinitions: List<NormalProduction>, lexerDefinitions: LexerDefinitions) : ParserDefinitions {
    val parserDefinitions = ParserDefinitions(name)
    val namesToUncapitalize = rulesDefinitions.map { it.lhs }

    rulesDefinitions.filterIsInstance<BNFProduction>().forEach {
        parserDefinitions.addRuleDefinition(RuleDefinition(it.lhs.uncapitalize(), it.expansion.process(lexerDefinitions, namesToUncapitalize), ""))
    }

    return parserDefinitions
}

private fun String.uncapitalize(): String {
    return if (this.isNotEmpty() && this[0].isUpperCase()) {
        this[0].lowercaseChar() + this.substring(1)
    } else {
        this
    }
}

private enum class RuleType { MORE, SKIP }

private fun getAndUpdateTypeCount(typeCounter: EnumMap<RuleType, Int>, ruleType: RuleType): Int {
    val count = typeCounter[ruleType]!!
    typeCounter[ruleType] = count + 1
    return count
}

/**
 * For rules that are defined in multiple lexical states, return the state that the rule will be defined in without
 * prefixing its name with the antlr mode name, since antlr lexer rule names must be unique. Normally this will be the
 * default state, unless the rule is only defined in some non-default state(s).
 */
private fun getCanonicalLexicalState(states: List<String>): String {
    return if (states.contains(JAVACC_DEFAULT_MODE_NAME)) {
        JAVACC_DEFAULT_MODE_NAME
    } else {
        val alphabeticalStates = states.sorted()
        // Return alphabetically first but giving precedence to states named with DEFAULT in them (e.g. LUCENE_DEFAULT)
        alphabeticalStates.firstOrNull { it.contains("DEFAULT") } ?: alphabeticalStates[0]
    }
}

private fun hasIdentifierActionToken(regExprSpec: RegExprSpec, identifier: String?): Boolean {
    return identifier != null && regExprSpec.act.actionTokens.find {
        token -> token.kind == JavaCCParserConstants.IDENTIFIER && token.image == identifier
    } != null
}

private fun javaCCStateToAntlrMode(state: String?): String? {
    return if (state == JAVACC_DEFAULT_MODE_NAME) {
        ANTLR_DEFAULT_MODE_NAME
    } else {
        state
    }
}

private fun generateLexerDefinitions(name: String, tokenDefinitions: List<TokenProduction>, changeStateFunctions: ChangeStateFunctions) : LexerDefinitions {
    val ignoreCaseAll = Options.getIgnoreCase()
    // Generate letter fragments if some or all tokens should be generated as case-insensitive
    val generateLetterFragments = ignoreCaseAll || tokenDefinitions.find { it.ignoreCase } != null
    val lexerDefinitions = LexerDefinitions(name, generateLetterFragments )
    val typeCounter: EnumMap<RuleType, Int> = EnumMap(RuleType.values().associateWith { 0 })
    tokenDefinitions.forEach { production ->
        if (!production.isExplicit) {
            // Don't add lexer definitions for tokens encountered as part of processing parser rules
            return@forEach
        }
        val lexStates = production.lexStates
        val kindImage = TokenProduction.kindImage[production.kind]
        production.respecs.forEach processSpec@{
            val commands = when (kindImage) {
                "SPECIAL" -> listOf("channel(HIDDEN)") // Lexer will create token not directly usable by parser
                "MORE" -> listOf("more") // Lexer will get another token while preserving the current text
                "SKIP" -> listOf("skip") // Lexer will throw out the text
                "TOKEN" -> listOf()
                else -> throw UnsupportedOperationException(kindImage)
            }
            val pushState = hasIdentifierActionToken(it, changeStateFunctions.pushState)
            val popState = hasIdentifierActionToken(it, changeStateFunctions.popState)

            val ruleName = it.rexp.getAntlrTokenName().let { name ->
                name.ifBlank {
                    when (kindImage) {
                        "MORE" -> "MORE${getAndUpdateTypeCount(typeCounter, RuleType.MORE)}"
                        "SKIP" -> "SKIP${getAndUpdateTypeCount(typeCounter, RuleType.SKIP)}"
                        else -> throw UnsupportedOperationException(kindImage)
                    }
                }
            }
            val canonicalState = getCanonicalLexicalState(lexStates.toList())
            lexStates.forEach processStates@{ ls ->
                if (it.rexp.private_rexp && ls != canonicalState) {
                    // Fragments are not used by parser therefore lexer rules across all states can share a single fragment rule
                    return@processStates
                }
                // Handling for rules being generated in their non-canonical states:
                // - Rule names must be unique, so we prefix their names with their state name
                // - Rule body will refer to their canonical state's token to reduce duplication
                // - Type command will be generated to refer to the canonical state's token so the parser can find it
                // despite it having a different name in these states
                //
                // fragment HEADER_NUM : [123456] ;
                // HEADER : 'H' HEADER_NUM ;
                // WS : ' ' -> skip ;
                // mode MODE1;
                //   MODE1_HEADER : HEADER -> type(H1) ;
                //   MODE1_WS : WS -> skip ;
                val stateRuleName = if (ls == canonicalState) ruleName else "${ls}_$ruleName"
                val stateRuleBody = if (ls == canonicalState) it.rexp.tokenProcess(production.ignoreCase || ignoreCaseAll) else ruleName
                val stateRuleCommands = commands.toMutableList()
                if (kindImage == "TOKEN" && ls != canonicalState) {
                    stateRuleCommands.add("type($ruleName)")
                }
                // it.nextState may be null with a change mode command, if there is a pushState action without a next state
                // defined (e.g. if rule stays in the same state but increases state stack depth e.g. to track balanced parentheses)
                val nextMode = javaCCStateToAntlrMode(it.nextState) ?: javaCCStateToAntlrMode(ls)
                when {
                    pushState -> "pushMode($nextMode)"
                    popState -> "popMode"
                    (it.nextState != null) -> "mode($nextMode)"
                    else -> null
                }?.let { changeModeCommand -> stateRuleCommands.add(changeModeCommand)}

                lexerDefinitions.addRuleDefinition(ls, it.toRuleDefinition(stateRuleName, stateRuleBody, stateRuleCommands))
            }
        }
    }
    return lexerDefinitions
}

fun JavaCCGrammar.convertToAntlr(name: String) : AntlrGrammar {
    val lexerDefinitions = generateLexerDefinitions("${name}Lexer", this.tokenRules, this.changeStateFunctions)
    val parserDefinitions = generateParserDefinitions("${name}Parser", this.parserRules, lexerDefinitions)
    return AntlrGrammar(lexerDefinitions, parserDefinitions)
}

fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("Specify the name of the JavaCC grammar to load")
        return
    }
    val file = File(args[0])
    val grammarName = file.nameWithoutExtension.replaceFirstChar(Char::titlecase)

    val javaCCGrammar = loadJavaCCGrammar(file)
    val antlrGrammar = javaCCGrammar.convertToAntlr(grammarName)

    antlrGrammar.saveLexer(File("${grammarName}Lexer.g4"))
    antlrGrammar.saveParser(File("${grammarName}Parser.g4"))
}