package com.strumenta.javacc

import org.javacc.parser.*
import java.io.File
import java.io.FileInputStream

data class JavaCCGrammar(val tokenRules: List<TokenProduction>, val parserRules: List<NormalProduction>)

fun loadJavaCCGrammar(javaCCGrammarFile: File) : JavaCCGrammar{
    val javaccParser = JavaCCParser(FileInputStream(javaCCGrammarFile))
    Options.init()
    javaccParser.javacc_input()
    return JavaCCGrammar(JavaCCGlobals.rexprlist, JavaCCGlobals.bnfproductions)
}
