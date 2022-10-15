package com.strumenta.javacc

import org.javacc.parser.Main
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class ParserTest {

    @Before
    fun setup() {
        Main.reInitAll()
    }

    companion object {
        fun runTest(fileBaseName: String) {
            val file = File("src/test/resources/parser/$fileBaseName.jj")
            val grammarName = file.nameWithoutExtension.replaceFirstChar(Char::titlecase)
            val javaCCGrammar = loadJavaCCGrammar(file)
            val antlrGrammar = javaCCGrammar.convertToAntlr(grammarName)
            val expectedLexerFilename = fileBaseName[0].uppercase() + fileBaseName.substring(1) + "Parser.g4"
            val expectedLexer = File("src/test/resources/parser/$expectedLexerFilename").inputStream().readBytes().toString(Charsets.UTF_8)
            assertEquals(expectedLexer, antlrGrammar.parserDefinitions.generate(antlrGrammar.lexerDefinitions.name))
        }
    }

    @Test
    fun basics() {
        runTest("basics")
    }
}