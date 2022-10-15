package com.strumenta.javacc

import org.javacc.parser.Main as javacc
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class LexerTest {

    @Before
    fun setup() {
        javacc.reInitAll()
    }

    companion object {
        fun runTest(fileBaseName: String) {
            val file = File("src/test/resources/lexer/$fileBaseName.jj")
            val grammarName = file.nameWithoutExtension.replaceFirstChar(Char::titlecase)
            val javaCCGrammar = loadJavaCCGrammar(file)
            val antlrGrammar = javaCCGrammar.convertToAntlr(grammarName)
            val expectedLexerFilename = fileBaseName[0].uppercase() + fileBaseName.substring(1) + "Lexer.g4"
            val expectedLexer = File("src/test/resources/lexer/$expectedLexerFilename").inputStream().readBytes().toString(Charsets.UTF_8)
            assertEquals(expectedLexer, antlrGrammar.lexerDefinitions.generate())
        }
    }

    @Test
    fun pushPopStateFuncs() {
        runTest("pushPopStateFuncs")
    }

    @Test
    fun unnamedTokens() {
        runTest("unnamedTokens")
    }

    @Test
    fun multiStateTokens() {
        runTest("multiStateTokens")
    }

    @Test
    fun caseInsensitive() {
        // Note that unlike the other letters, rule A does not get generated as a fragment since there
        // is a real rule matching that literal (e.g. for parsing <a> HTML tags)
        runTest("caseInsensitive")
    }
}