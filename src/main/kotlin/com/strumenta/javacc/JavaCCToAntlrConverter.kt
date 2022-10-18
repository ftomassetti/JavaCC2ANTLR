@file:JvmName("JavaCCToAntlrConverter")
package com.strumenta.javacc

import org.javacc.parser.*
import java.io.File
import java.util.*

abstract class AntlrRuleComponent {
    abstract var quantifier: Char?

    abstract override fun toString(): String

    fun quantify(newQuantifier: Char): AntlrRuleComponent {
        // Note that we nest it within a sequence if it's already quantified, to prevent concatenating quantifiers as doing
        // so leads to unpredictable runtime behavior in ANTLR. Example: ( (<TOKEN>)+ )? needs to serialize to (TOKEN+)?
        // and not as TOKEN+? for ANTLR to logically process it as TOKEN* (same as javacc handles it) and not as TOKEN+
        return if (this.quantifier == null) {
            this.apply { quantifier = newQuantifier}
        } else {
            AntlrSequence(listOf(this), newQuantifier)
        }
    }
}

class AntlrSequence(val elements: List<AntlrRuleComponent>, override var quantifier: Char? = null): AntlrRuleComponent() {
    init {
        // Only allow single element sequence if the element is quantified and the sequence is quantified
        check(elements.size > 1 || (elements.size == 1 && elements[0].quantifier != null && quantifier != null))
    }

    override fun toString(): String {
        val joined = elements.joinToString(" ")
        return if (quantifier != null) "($joined)$quantifier" else joined
    }
}

class AntlrChoice(val elements: List<AntlrRuleComponent>, override var quantifier: Char? = null): AntlrRuleComponent() {
    init {
        check(elements.size > 1)
    }

    override fun toString(): String {
        val joined = elements.joinToString(" | ")
        return if (quantifier != null) "($joined)$quantifier" else "($joined)"
    }
}

class AntlrSingleElement(val element: String, override var quantifier: Char? = null): AntlrRuleComponent() {
    init {
        check(element.isNotBlank())
    }

    override fun toString(): String {
        return element + (quantifier ?: "")
    }
}

private fun Expansion.processRule(lexerDefinitions: LexerDefinitions, parserRuleNames: Set<String>): AntlrRuleComponent? {
    return when (this) {
        is Sequence -> {
            if (this.units.any { it !is Expansion }) {
                throw UnsupportedOperationException("Sequence element is not an Expansion")
            }
            val processedRules = this.units.mapNotNull { (it as Expansion).processRule(lexerDefinitions, parserRuleNames) }
            when {
                processedRules.isEmpty() -> null // e.g. { .. java code ..} { .. java code .. }
                processedRules.size == 1 -> processedRules[0] // If only one element (e.g. <TOKEN> { .. java code .. }), promote it
                else -> AntlrSequence(processedRules)
            }
        }
        is Lookahead -> null
        is Choice -> {
            check(this.choices.size > 1)
            if (this.choices.any { it !is Expansion }) {
                throw UnsupportedOperationException("Choice element is not an Expansion")
            }
            val processedRules = this.choices.mapNotNull { (it as Expansion).processRule(lexerDefinitions, parserRuleNames) }
            // If one of the choice options didn't map to a subrule or element (e.g. it was an action without parser function
            // terminals, it was a JAVA_CODE non-terminal reference, etc), then the entire choice must be considered optional
            // because the "null" option is effectively a fallback case
            val quantifier = if (processedRules.size != this.choices.size) '?' else null
            val isSequence = processedRules.size == 1
            return when {
                processedRules.isEmpty() -> null // Epsilon() | Epsilon()
                isSequence -> processedRules[0].quantify(quantifier!!) // e.g. <TOKEN> | { .. java code .. }
                else -> AntlrChoice(processedRules, quantifier)
            }
        }
        is RStringLiteral -> {
            // Find the lexer rule that covers this literal (if it's case insensitive grammar it will be fragmentized)
            // Note we don't need to consider the case where the grammar is globally case sensitive but the individual
            // lexer rule that would cover this literal is marked case insensitive because that's prohibited in javacc
            val ruleBody = image.toLexerLiteralRuleElement(Options.getIgnoreCase())
            val element = lexerDefinitions.getRuleByBody(ruleBody)?.name
                    ?: throw UnsupportedOperationException("Cannot generate rule element for string literal " +
                            "'${image}' as ANTLR parser rules can only contain string literals that exactly match " +
                            "lexer rules and none was found")
            AntlrSingleElement(element)
        }
        is Action -> {
            var prevToken = Token()
            // Some non terminals may appear inside actions, e.g. { refValue = PackageName(); } where PackageName is a
            // parser rule. We use a heuristic to find them and process them as a sequence of NonTerminal expansions.
            // WARNING: This will not properly handle the case where the action does not call a parser rule exactly once
            // due to it appearing inside if statements, for loops, etc.
            // For example, AVOID: { if (bool) refValue = PackageName(); } and { for (expr; expr; expr) PackageName(); }
            // You would need to hand-write an ANTLR action to emulate that conditional behavior
            val parserFunctionCalls = this.actionTokens.filter {
                val isParserFuncCall =
                        it.kind == JavaCCParserConstants.IDENTIFIER
                                && parserRuleNames.contains(it.image)
                                && prevToken.kind != JavaCCParserConstants.NEW // new Blah( is object construction
                                && it.next?.kind == JavaCCParserConstants.LPAREN
                prevToken = it
                isParserFuncCall
            }
            return when (parserFunctionCalls.size) {
                0 -> null // e.g. { .. java code .. }
                1 -> AntlrSingleElement(parserFunctionCalls[0].image.uncapitalize()) // e.g. { str = ParserRule(); }
                else -> AntlrSequence(parserFunctionCalls.map { AntlrSingleElement(it.image.uncapitalize()) } ) // e.g. { str1 = ParserRule1(); str2 = ParserRule2(); }
            }
        }
        is NonTerminal -> {
            // If the non terminal is not a known rule name it should be ignored (probably a JavaCodeProduction call)
            // JAVACODE void E() {}
            // void Rule1() : {} { E() }  <--- Ignore the E NonTerminal
            // Note: This means that parser rules with empty body must still be printed otherwise here we might reference
            // a nonexistent rule, producing a compilation errors in ANTLR
            if (parserRuleNames.contains(this.name)) AntlrSingleElement(this.name.uncapitalize()) else null
        }
        is ZeroOrOne -> this.expansion.processRule(lexerDefinitions, parserRuleNames)?.quantify('?')
        is ZeroOrMore -> this.expansion.processRule(lexerDefinitions, parserRuleNames)?.quantify('*')
        is OneOrMore -> this.expansion.processRule(lexerDefinitions, parserRuleNames)?.quantify('+')
        is TryBlock -> this.exp.processRule(lexerDefinitions, parserRuleNames)
        is RJustName -> AntlrSingleElement(this.label)
        is REndOfFile -> AntlrSingleElement("EOF")
        else -> throw UnsupportedOperationException("Not sure: ${this.javaClass.simpleName}")
    }
}

private fun Expansion.process(lexerDefinitions: LexerDefinitions, parserRuleNames: Set<String>): String? {
    return this.processRule(lexerDefinitions, parserRuleNames)?.toString()
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
    val bnfProductions = rulesDefinitions.filterIsInstance<BNFProduction>()
    val parserRuleNames = buildSet<String> { bnfProductions.forEach { add(it.lhs) } }

    bnfProductions.forEach {
        val antlrRuleBody = it.expansion.process(lexerDefinitions, parserRuleNames) ?: ""
        parserDefinitions.addRuleDefinition(RuleDefinition(it.lhs.uncapitalize(), antlrRuleBody, ""))
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
    return identifier != null && regExprSpec.act.actionTokens.any {
        token -> token.kind == JavaCCParserConstants.IDENTIFIER && token.image == identifier
    }
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
    val generateLetterFragments = ignoreCaseAll || tokenDefinitions.any { it.ignoreCase }
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