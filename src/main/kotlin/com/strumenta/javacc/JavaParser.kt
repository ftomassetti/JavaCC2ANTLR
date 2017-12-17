package com.strumenta.javacc

import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream

fun main(args: Array<String>) {
    val code = "class A { }"

    val inputStream = ANTLRInputStream(code)
    val markupLexer = JavaLexer(inputStream)
    val commonTokenStream = CommonTokenStream(markupLexer)
    val markupParser = JavaParser(commonTokenStream)

    val cu = markupParser.compilationUnit()
    println(cu)
}