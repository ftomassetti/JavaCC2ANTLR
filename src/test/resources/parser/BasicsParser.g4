parser grammar BasicsParser;

options { tokenVocab=BasicsLexer; }

singleToken : AMET  ;

singleTokenZeroOrOne : AMET?  ;

singleTokenZeroOrMore : AMET*  ;

singleTokenOneOrMore : AMET+  ;

singleTokenDoubleQuantified : (AMET+)?  ;

sequence : LOREM IPSUM  ;

sequenceNestedWithAction : LOREM IPSUM DOLOR SIT AMET  ;

sequenceSingle : AMET  ;

sequenceZeroOrOne : (LOREM IPSUM)?  ;

sequenceZeroOrMore : (LOREM IPSUM)*  ;

sequenceOneOrMore : (LOREM IPSUM)+  ;

choice : (DOLOR | SIT)  ;

choiceWithLookahead : (DOLOR SIT | DOLOR AMET)  ;

tripleNestedChoice : (DOLOR | SIT)  ;

choiceWithDefaultAction : (DOLOR | SIT)?  ;

choiceWithEmptySequence : (DOLOR | SIT)?  ;

choiceOfSequences : (LOREM IPSUM | DOLOR SIT)  ;

choiceWithOptional : (LOREM IPSUM | (DOLOR SIT)?)  ;

choiceEmpty :   ;

choiceZeroOrOne : (DOLOR | SIT)?  ;

choiceZeroOrMore : (DOLOR | SIT)*  ;

choiceOneOrMore : (DOLOR | SIT)+  ;

sequenceAndChoice : LOREM IPSUM (DOLOR | SIT)  ;

literalCaseMismatch : BEGIN  ;

literalCaseMatch : BEGIN  ;

nonTerminals : choiceEmpty sequence  ;

nonTerminalsInActions : choice  ;

start : BEGIN singleToken sequence choice EOF  ;
