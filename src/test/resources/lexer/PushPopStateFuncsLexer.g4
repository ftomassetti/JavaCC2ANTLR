lexer grammar PushPopStateFuncsLexer;

TEST : 'TEST(' -> pushMode(INSIDE) ;
END : 'END'  ;

mode INSIDE;
LPAREN : '(' -> skip, pushMode(INSIDE) ;
RPAREN : ')' -> skip, popMode ;
