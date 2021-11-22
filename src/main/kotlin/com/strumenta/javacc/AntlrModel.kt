package com.strumenta.javacc

import org.snt.inmemantlr.GenericParser
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*

data class RuleDefinition(val name: String, val body: String, val action: String?, val fragment: Boolean = false) {
    fun generate() : String {
        val prefix = if (fragment) "fragment " else ""
        val actionPostfix = if (action == null) "" else "-> $action"
        val body= if(action!=null && body.contains("|")) "($body)" else body
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

class LexerDefinitions(val name: String) {

    private val rulesByMode  = HashMap<String, MutableList<RuleDefinition>>()

    fun ruleForImage(image: String, mode: String = DEFAULT_MODE_NAME) : RuleDefinition? {
        return rulesByMode[mode]?.firstOrNull {
            it.body == "'$image'"
        }
    }

    fun addRuleDefinition(mode: String, ruleDefinition: RuleDefinition) {
        if(ruleDefinition.name.contains("TEXT_BL")){
            println(ruleDefinition.name)
        }
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
        if (ruleDefinitionCorrected.body.contains("~[]")) {
            ruleDefinitionCorrected = ruleDefinitionCorrected.copy(body = ruleDefinitionCorrected.body.replace("~[]", "."))
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
            val specialHandlingRequired = it.name.contains("COMMENT") || it.name.contains("TEXT_BLOCK")
            if (specialHandlingRequired && it.action == null) {
                printWriter.println(it.copy(action = "skip").generate())
            } else if (specialHandlingRequired && it.action != null && !it.action.contains("skip")) {
                printWriter.println(it.copy(action = "skip, ${it.action}").generate())
            } else {
                printWriter.println(it.generate())
            }
        }
    }
}

class AntlrGrammar(val lexerDefinitions: LexerDefinitions, val parserDefinitions: ParserDefinitions) {
    fun lexerCode() = lexerDefinitions.generate()
    fun parserCode() = parserDefinitions.generate(lexerDefinitions.name)
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
