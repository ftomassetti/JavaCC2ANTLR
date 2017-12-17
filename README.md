# JavaCC2ANTLR

JavaCC is an old and venerable tool, used in so many projects. In recent years however ANTLR seems to have a growing community and
there are different tools to support ANTLR. Also, ANTLR can be generate a parser for so many target languages that
are not supported by JavaCC.

So I hacked together this little project, in Kotlin.

For now it basically get a JavaCC grammar and produces a lexer and a parser ANTLR grammar which should hopefully be equivalent.
