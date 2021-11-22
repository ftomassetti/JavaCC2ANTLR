package com.strumenta.javacc

import me.tomassetti.kolasu.parsing.ParseTreeLeaf
import me.tomassetti.kolasu.parsing.ParseTreeNode
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.tree.TerminalNode
import org.junit.BeforeClass
import org.slf4j.LoggerFactory
import org.snt.inmemantlr.GenericParser
import java.io.File
import kotlin.test.assertEquals
import org.junit.Test as test

internal class SpecialClassLoader(parent: ClassLoader) : ClassLoader(parent) {

    private val m = HashMap<String, ByteArray>()

    /**
     * find a class that is already loaded
     *
     * @param name class name
     * @return the actual class
     * @throws ClassNotFoundException if the class could not be found
     */
    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        var mbc: ByteArray? = m[name]
        if (mbc == null) {
            mbc = m[name.replace(".", "/")]
            if (mbc == null) {
                LOGGER.error("Could not find {}", name)
                return super.findClass(name)
            }
        }
        val bseq = mbc
        return defineClass(name, bseq, 0, bseq.size)
    }

    /**
     * add class to class loader
     */
    fun addClass(className: String, bytes: ByteArray) {
        m[className] = bytes
    }

    companion object {

        private val LOGGER = LoggerFactory.getLogger(SpecialClassLoader::class.java)
    }

}

fun toParseTree(node: ParserRuleContext, vocabulary: Vocabulary) : ParseTreeNode {
    val res = ParseTreeNode(node.javaClass.simpleName.removeSuffix("Context"))
    node.children?.forEach { c ->
        when (c) {
            is ParserRuleContext -> res.child(toParseTree(c, vocabulary))
            is TerminalNode -> res.child(ParseTreeLeaf(vocabulary.getSymbolicName(c.symbol.type), c.text))
        }
    }
    return res
}

class JavaGrammarTest {

    companion object {

        private lateinit var genericParser : GenericParser
        private lateinit var vocabulary : Vocabulary

        @BeforeClass
        @JvmStatic fun setup() {
            val file = File("src/test/resources/java.jj")
            val grammarName = file.nameWithoutExtension.replaceFirstChar(Char::titlecase)

            val javaCCGrammar = loadJavaCCGrammar(file)
            val antlrGrammar = javaCCGrammar.convertToAntlr(grammarName)
            this.genericParser = antlrGrammar.genericParser()
            val element = genericParser.allCompiledObjects.find { it.isLexer }
            val specialClassLoader = SpecialClassLoader(this::class.java.classLoader)
            element!!.byteCodeObjects.forEach { specialClassLoader.addClass(it.className, it.bytes) }
            val cl = specialClassLoader.loadClass("JavaLexer")
            this.vocabulary = cl.getField("VOCABULARY").get(null) as Vocabulary
        }

    }

    @test
    fun loadJavaParser() {
        val code = "class A { }"
        val ast = genericParser.parse(code)
        val parseTree = toParseTree(ast, vocabulary)
        assertEquals("CompilationUnit\n" +
                "  Modifiers\n" +
                "  ClassOrInterfaceDeclaration\n" +
                "    T:CLASS[class]\n" +
                "    SimpleName\n" +
                "      Identifier\n" +
                "        T:IDENTIFIER[A]\n" +
                "    ClassOrInterfaceBody\n" +
                "      T:LBRACE[{]\n" +
                "      T:RBRACE[}]\n" +
                "  T:EOF[<EOF>]\n", parseTree.multiLineString())
    }

    @test
    fun canParseAllGuava() {
        val guavaSrc = File("src/test/resources/guava-src")
        parseDir(guavaSrc)
    }

    private fun parseDir(src: File) {
        src.listFiles().forEach {
            if (it.isDirectory) {
                parseDir(it)
            } else if (it.isFile && it.extension == "java") {
                parseJavaFile(it)
            }
        }
    }

    private fun parseJavaFile(javaFile: File) {
        println("Parsing $javaFile")
        try {
            val ast = genericParser.parse(javaFile)
        } catch (e: Exception) {
            throw RuntimeException("Issue parsing $javaFile", e)
        }
    }
}