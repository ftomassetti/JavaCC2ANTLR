# JavaCC2ANTLR

[![Build Status](https://travis-ci.org/ftomassetti/JavaCC2ANTLR.svg?branch=master)](https://travis-ci.org/ftomassetti/JavaCC2ANTLR)

JavaCC is an old and venerable tool, used in so many projects. In recent years however ANTLR seems to have a growing community and
there are different tools to support ANTLR. Also, ANTLR can be used to generate a parser for so many target languages that
are not supported by JavaCC.

So I hacked together this little project, in Kotlin.

For now it basically get a JavaCC grammar and produces a lexer and a parser ANTLR grammar which should hopefully be equivalent.

## Generate ANTLR Lexer & Parser

Simply look at the class `JavaCCToAntlrConverter`. It takes the file name of the JavaCC grammar and outputs
a Lexer and a parser Grammar.

## Generate an ANTLR in memory

```kotlin
val file = File("src/test/resources/java.jj")
val grammarName = file.nameWithoutExtension.capitalize()

val javaCCGrammar = loadJavaCCGrammar(file)
val antlrGrammar = javaCCGrammar.convertToAntlr(grammarName)
this.genericParser = antlrGrammar.genericParser()
val ast = genericParser.parse("class A { }")
```

## Push/Pop Mode Commands

JavaCC by default does not have a way for tokens to change the token manager lexical state with memory, like ANTLR provides
with the `pushMode` and `popMode` commands. For example, to parse as a single token a balanced set of parentheses such as
`((()) ())` you might have the following JavaCC parser:
```
TOKEN_MGR_DECLS : {
    static List<Integer> lexicalStateStack = new ArrayList<Integer>();

    static void openParen() {
        lexicalStateStack.add(curLexState);
    }

    static void closeParen() {
        SwitchTo(lexicalStateStack.remove(lexicalStateStack.size() - 1));
    }
}

<DEFAULT, LEVEL1, LEVELN> SKIP : {
    < " " >
}

<LEVELN> MORE : {
    <LPAREN:    "("> { openParen(); }
|   <RPAREN:    ")"> { closeParen(); }
}

MORE : {
    < "(" > { openParen(); } : LEVEL1
}

<LEVEL1> MORE : {
    < "(" > { openParen(); } : LEVELN
}

<LEVEL1> TOKEN : {
    <BALANCED_PARENS: ")" > { closeParen(); } : DEFAULT
}

void Start(): {} { <BALANCED_PARENS> <EOF> }
```

However, the ANTLR lexer would not behave correctly because we cannot infer when, according to the `SwitchTo` statements
executed as part of the actions, the corresponding ANTLR rules should use `mode`, `pushMode`, or `popMode` commands:

```
lexer grammar Lexer;

SKIP0 : ' ' -> skip ;
MORE0 : '(' -> more, mode(LEVEL1) ;

mode LEVEL1;
LEVEL1_SKIP0 : SKIP0 -> skip ;
MORE1 : '(' -> more, mode(LEVELN) ;
BALANCED_PARENS : ')' -> mode(DEFAULT_MODE) ;

mode LEVELN;
LEVELN_SKIP0 : SKIP0 -> skip ;
LPAREN : '(' -> more ;
RPAREN : ')' -> more ;  // PROBLEM: Cannot escape this mode!


parser grammar Parser;

options { tokenVocab=Lexer; }

start :  BALANCED_PARENS EOF  ;
```

In order to handle such actions, you must add the following fields to your `TOKEN_MGR_DECLS` with values set to the name
of your functions that should map to `pushMode` and `popMode` commands respectively:

```
TOKEN_MGR_DECLS : {
    ...
    final static String pushStateFunc = "openParen";
    final static String popStateFunc = "closeParen";
}
```

Now the lexer gets generated correctly:

```
SKIP0 : ' ' -> skip ;
MORE0 : '(' -> more, pushMode(LEVEL1) ;

mode LEVEL1;
LEVEL1_SKIP0 : SKIP0 -> skip ;
MORE1 : '(' -> more, pushMode(LEVELN) ;
BALANCED_PARENS : ')' -> popMode ;

mode LEVELN;
LEVELN_SKIP0 : SKIP0 -> skip ;
LPAREN : '(' -> more, pushMode(LEVELN) ;
RPAREN : ')' -> more, popMode ;
```

## Licensing

The project is made available under the Apache Public License V2.0. Please see the file called [LICENSE](LICENSE).
