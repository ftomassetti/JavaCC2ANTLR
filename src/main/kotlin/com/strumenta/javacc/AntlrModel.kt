package com.strumenta.javacc

import org.snt.inmemantlr.GenericParser
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

data class RuleDefinition(val name: String, val body: String, val commandStr: String, val fragment: Boolean = false) {
    fun generate() : String {
        val prefix = if (fragment) "fragment " else ""
        val actionPostfix = if (commandStr.isEmpty()) "" else "-> $commandStr"
        return "$prefix$name : $body $actionPostfix ;"
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

class LexerDefinitions(val name: String, private val generateLetterFragments: Boolean) {

    private val rulesByMode = HashMap<String, MutableList<RuleDefinition>>()
    private val letterFragments: MutableList<RuleDefinition> = generateLetterFragments().toMutableList()

    fun getRuleByBody(ruleBody: String) : RuleDefinition? {
        return rulesByMode.values.flatten().firstOrNull {
            it.body == ruleBody
        }
    }

    fun addRuleDefinition(mode: String, ruleDefinition: RuleDefinition) {
        if (!rulesByMode.containsKey(mode)) {
            rulesByMode[mode] = LinkedList()
        }
        var ruleDefinitionCorrected = ruleDefinition
        if (ruleDefinition.name.isEmpty()) {
            throw UnsupportedOperationException(ruleDefinition.body)
        }
        if (ruleDefinitionCorrected.name.startsWith("_")) {
            // Antlr lexer rule names must begin with a capital letter
            ruleDefinitionCorrected = ruleDefinitionCorrected.copy(name = "US${ruleDefinitionCorrected.name}")
        }
        if (generateLetterFragments
                && ruleDefinitionCorrected.name.length == 1
                && ruleDefinitionCorrected.name in "A".."Z") {
            if (ruleDefinitionCorrected.body.uppercase() == ruleDefinitionCorrected.name.uppercase()) {
                // If the user's letter rule can serve in place of the letter fragment we would generate, preserve the
                // rule and skip generating the redundant letter fragment later
                if (!ruleDefinitionCorrected.fragment) {
                    val letterFragment = letterFragments.first { it.name == ruleDefinitionCorrected.name }
                    ruleDefinitionCorrected = ruleDefinitionCorrected.copy(body = letterFragment.body)
                    letterFragments.remove(letterFragment)
                }
            } else {
                throw UnsupportedOperationException("Rule conflicts with automatically generated case insensitive character fragment: '${ruleDefinitionCorrected.name}' -> ${ruleDefinitionCorrected.body}")
            }
        }
        if (ruleDefinitionCorrected.name == ruleDefinitionCorrected.body) {
            // Such a lexer rule would infinitely recurse
            return
        }
        if (ruleDefinitionCorrected.body.contains("~[]")) {
            ruleDefinitionCorrected = ruleDefinitionCorrected.copy(body = ruleDefinitionCorrected.body.replace("~[]", "."))
        }
        rulesByMode[mode]!!.add(ruleDefinitionCorrected)
    }

    private fun generateLetterFragments() : List<RuleDefinition> {
        // Generate e.g. fragment A: [aA] rules that literals will be rewritten to use to support case insensitivity
        return (0..25).map {
            RuleDefinition(('A' + it).toString(), "[" + ('a' + it).toString() + ('A' + it).toString() + "]", "", fragment = true)
        }
    }

    fun generate() : String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        printWriter.println("lexer grammar $name;")
        if (generateLetterFragments) {
            letterFragments.forEach { rulesByMode[JAVACC_DEFAULT_MODE_NAME]!!.add(it) }
        }
        printMode(JAVACC_DEFAULT_MODE_NAME, printWriter)
        rulesByMode.keys.filter { it != JAVACC_DEFAULT_MODE_NAME }.forEach { printMode(it, printWriter) }
        return stringWriter.toString()
    }

    private fun printMode(mode: String, printWriter: PrintWriter) {
        printWriter.println()
        if (mode != JAVACC_DEFAULT_MODE_NAME) {
            printWriter.println("mode $mode;")
        }
        rulesByMode[mode]!!.forEach {
            printWriter.println(it.generate())
        }
    }
}

class AntlrGrammar(val lexerDefinitions: LexerDefinitions, val parserDefinitions: ParserDefinitions) {
    private fun lexerCode() = lexerDefinitions.generate()
    private fun parserCode() = parserDefinitions.generate(lexerDefinitions.name)
    fun saveLexer(file: File) {
        file.printWriter().use { out -> out.print(lexerCode()) }
    }
    fun saveParser(file: File) {
        file.printWriter().use { out -> out.print(parserCode()) }
    }
    fun genericParser() : GenericParser {
        val genericParser = GenericParser(lexerCode(), parserCode())
        genericParser.compile()
        return genericParser
    }
}
