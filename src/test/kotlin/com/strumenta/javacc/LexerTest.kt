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
        fun runTest(javaCCFileName: String, antlrFileName: String) {
            val file = File("src/test/resources/${javaCCFileName}")
            val grammarName = file.nameWithoutExtension.replaceFirstChar(Char::titlecase)
            val javaCCGrammar = loadJavaCCGrammar(file)
            val antlrGrammar = javaCCGrammar.convertToAntlr(grammarName)
            val expectedLexer = File("src/test/resources/${antlrFileName}").inputStream().readBytes().toString(Charsets.UTF_8)
            assertEquals(expectedLexer, antlrGrammar.lexerDefinitions.generate())
        }
    }

    @Test
    fun pushPopStateFuncs() {
        runTest("pushPopStateFuncs.jj", "PushPopStateFuncs.g4")
    }

    @Test
    fun unnamedTokens() {
        runTest("unnamedTokens.jj", "UnnamedTokens.g4")
    }

    @Test
    fun multiStateTokens() {
        runTest("multiStateTokens.jj", "MultiStateTokens.g4")
    }

    @Test
    fun caseInsensitive() {
        // Note that unlike the other letters, rule A does not get generated as a fragment since there
        // is a real rule matching that literal (e.g. for parsing <a> HTML tags)
        runTest("caseInsensitive.jj", "CaseInsensitive.g4")
    }
}