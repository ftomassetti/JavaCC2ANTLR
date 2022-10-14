package com.strumenta.javacc

import org.javacc.parser.*
import java.io.File
import java.io.FileInputStream

data class JavaCCGrammar(val tokenRules: List<TokenProduction>, val parserRules: List<NormalProduction>, val changeStateFunctions: ChangeStateFunctions)

fun loadJavaCCGrammar(javaCCGrammarFile: File) : JavaCCGrammar{
    val javaccParser = JavaCCParser(FileInputStream(javaCCGrammarFile))
    Options.init()
    javaccParser.javacc_input()
    return JavaCCGrammar(JavaCCGlobals.rexprlist, JavaCCGlobals.bnfproductions, getChangeStateFunctions())
}

const val PUSH_STATE_FUNC = "pushStateFunc"
const val POP_STATE_FUNC = "popStateFunc"
data class ChangeStateFunctions(val pushState: String?, val popState: String?)

/**
 * Support user-defined functions that when they appear in lexer rule actions, tell us that we need to generate either
 * "popMode" or "pushMode" commands, rather than "mode" commands. You must create variables pushStateFunc and popStateFunc
 * assigned to the name of the corresponding functions used by your actions, so those actions can be identified.
 * We support this because it is otherwise impossible to accurately identify when a rule necessitates a pushMode vs popMode.
 *
 * Example:
 * TOKEN_MGR_DECLS : {
 *   List<Integer> stateStack = new ArrayList<Integer>();
 *
 *   void push() {
 *     stateStack.add(curLexState);
 *   }
 *
 *   void pop() {
 *     SwitchTo(stateStack.remove(stateStack.size() - 1));
 *   }
 *
 *   private String pushStateFunc = "push";   <----- Your javacc grammar must define this variable
 *   private String popStateFunc = "pop";     <----- Your javacc grammar must define this variable
 * }
 *
 * <JAVA> MORE :
 * {
 *   "/*" { push(); } : IN_JAVA_COMMENT
 * }
 * <IN_JAVA_COMMENT> MORE :
 * {
 *   < ~[] >
 * }
 * <IN_JAVA_COMMENT> SPECIAL_TOKEN :
 * {
 *   <JAVA_COMMENT: "*/" > { pop(); }
 * }
 */
private fun getChangeStateFunctions(): ChangeStateFunctions {
    val tokenMgrDecls = JavaCCGlobals.token_mgr_decls
    val findStateChangeFunction = { func: String ->
        (tokenMgrDecls?.find {
            (it as Token).kind == JavaCCParserConstants.IDENTIFIER && it.image == func
                    && it.next.kind == JavaCCParserConstants.ASSIGN
                    && it.next.next.kind == JavaCCParserConstants.STRING_LITERAL
        } as Token?)?.next?.next?.image?.removePrefix("\"")?.removeSuffix("\"")
    }
    return ChangeStateFunctions(findStateChangeFunction(PUSH_STATE_FUNC), findStateChangeFunction(POP_STATE_FUNC))
}
