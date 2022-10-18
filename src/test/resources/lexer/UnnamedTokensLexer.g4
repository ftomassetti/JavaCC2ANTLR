lexer grammar UnnamedTokensLexer;

SKIP0 : ' ' -> skip ;
SKIP1 : '\t' -> skip ;
SKIP2 : '\n' -> skip ;
SKIP3 : '\r' -> skip ;
SKIP4 : '\f' -> skip ;
MORE0 : '/*' -> more, mode(IN_COMMENT) ;

mode IN_COMMENT;
IN_COMMENT_SKIP0 : SKIP0 -> skip ;
IN_COMMENT_SKIP1 : SKIP1 -> skip ;
IN_COMMENT_SKIP2 : SKIP2 -> skip ;
IN_COMMENT_SKIP3 : SKIP3 -> skip ;
IN_COMMENT_SKIP4 : SKIP4 -> skip ;
COMMENT : '*/' -> channel(HIDDEN), mode(DEFAULT_MODE) ;
