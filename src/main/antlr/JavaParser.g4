parser grammar JavaParser;

options { tokenVocab=JavaLexer; }

compilationUnit :   ( SEMICOLON)* ( packageDeclaration)? (( importDeclaration  |   modifiers ( classOrInterfaceDeclaration  |  recordDeclaration  |  enumDeclaration  |  annotationTypeDeclaration  |  moduleDeclaration |  SEMICOLON)))* ( EOF |  CTRL_Z)   ;

packageDeclaration :  annotations PACKAGE  name SEMICOLON   ;

importDeclaration :  IMPORT  ( STATIC )? name ( DOT STAR )? SEMICOLON   ;

modifiers :  ( ( PUBLIC  |  STATIC  |  PROTECTED  |  PRIVATE  |  FINAL  |  ABSTRACT  |  SYNCHRONIZED  |  NATIVE  |  TRANSIENT  |  VOLATILE  |  STRICTFP  |  TRANSITIVE  |  US__DEFAULT  |  annotation ))*   ;

classOrInterfaceDeclaration :  ( CLASS  |  INTERFACE )  simpleName ( typeParameters)? ( extendsList)? ( implementsList)? classOrInterfaceBody   ;

recordDeclaration :  RECORD  simpleName ( typeParameters)? parameters ( implementsList)? recordBody   ;

extendsList :  EXTENDS annotatedClassOrInterfaceType  ( COMMA annotatedClassOrInterfaceType )*   ;

implementsList :  IMPLEMENTS annotatedClassOrInterfaceType  ( COMMA annotatedClassOrInterfaceType )*   ;

enumDeclaration :  ENUM  simpleName ( implementsList)? LBRACE ( enumConstantDeclaration  ( COMMA enumConstantDeclaration )*)? ( COMMA)? ( SEMICOLON (( classOrInterfaceBodyDeclaration  |  SEMICOLON))*)? RBRACE   ;

enumConstantDeclaration :  ( annotation )* simpleName  ( arguments)? ( classOrInterfaceBody)?   ;

typeParameters :  LT  annotations typeParameter  ( COMMA annotations typeParameter )* GT    ;

typeParameter :  simpleName  ( typeBound)?   ;

typeBound :  EXTENDS annotatedClassOrInterfaceType  ( BIT_AND annotatedClassOrInterfaceType )*   ;

classOrInterfaceBody :  LBRACE (( classOrInterfaceBodyDeclaration  |  SEMICOLON))* RBRACE   ;

recordBody :  LBRACE (( recordBodyDeclaration  |  SEMICOLON))* RBRACE   ;

recordBodyDeclaration :  ( initializerDeclaration |  modifiers ( classOrInterfaceDeclaration |  enumDeclaration |  annotationTypeDeclaration |  compactConstructorDeclaration |  constructorDeclaration |  fieldDeclaration |  methodDeclaration))   ;

compactConstructorDeclaration :  ( typeParameters )? simpleName  ( THROWS annotatedReferenceType  ( COMMA annotatedReferenceType )*)? LBRACE  ( explicitConstructorInvocation)? statements RBRACE   ;

classOrInterfaceBodyDeclaration :  ( initializerDeclaration |  modifiers ( classOrInterfaceDeclaration |  recordDeclaration |  enumDeclaration |  annotationTypeDeclaration |  constructorDeclaration |  fieldDeclaration |  methodDeclaration))   ;

fieldDeclaration :  type variableDeclarator  ( COMMA variableDeclarator )* SEMICOLON   ;

variableDeclarator :  variableDeclaratorId ( ASSIGN variableInitializer)?   ;

variableDeclaratorId :  simpleName  ( arrayBracketPair )*   ;

variableInitializer :  ( arrayInitializer |  expression)   ;

arrayInitializer :  LBRACE  ( variableInitializer  ( COMMA variableInitializer )*)? ( COMMA)? RBRACE   ;

methodDeclaration :  ( typeParameters )? annotations  resultType  simpleName parameters ( arrayBracketPair )* ( THROWS annotatedReferenceType  ( COMMA annotatedReferenceType )*)? ( block |  SEMICOLON)   ;

annotatedReferenceType :  annotations referenceType   ;

annotatedType :  annotations type   ;

parameters :  LPAREN ( ( receiverParameter |  parameter ) ( COMMA parameter )*)? RPAREN   ;

lambdaParameters :  parameter  ( COMMA parameter )*   ;

inferredLambdaParameters :  variableDeclaratorId  ( COMMA variableDeclaratorId )*   ;

parameter :  modifiers type ( annotations ELLIPSIS )? variableDeclaratorId   ;

receiverParameter :  annotations type receiverParameterId   ;

receiverParameterId :  ( name DOT)? THIS   ;

constructorDeclaration :  ( typeParameters )? simpleName  parameters ( THROWS annotatedReferenceType  ( COMMA annotatedReferenceType )*)? LBRACE  ( explicitConstructorInvocation)? statements RBRACE   ;

explicitConstructorInvocation :  ( ( typeArguments )? THIS  arguments SEMICOLON |  ( primaryExpressionWithoutSuperSuffix DOT )? ( typeArguments )? SUPER  arguments SEMICOLON)   ;

statements :  ( blockStatement )*   ;

initializerDeclaration :  ( STATIC )? block    ;

type :  ( referenceType |  primitiveType)   ;

referenceType :  ( primitiveType ( arrayBracketPair )+ |  classOrInterfaceType ( arrayBracketPair )*)   ;

arrayBracketPair :  annotations LBRACKET  RBRACKET   ;

intersectionType :  referenceType  BIT_AND ( annotatedReferenceType )+   ;

annotatedClassOrInterfaceType :  annotations classOrInterfaceType   ;

classOrInterfaceType :  simpleName  ( typeArguments)?  ( DOT annotations simpleName ( typeArguments)? )*   ;

typeArguments :  LT  ( typeArgument  ( COMMA typeArgument )*)? GT    ;

typeArgument :  annotations ( type |  wildcard)   ;

wildcard :  HOOK  (( EXTENDS annotations referenceType |  SUPER annotations referenceType))?   ;

primitiveType :  ( BOOLEAN  |  CHAR  |  BYTE  |  SHORT  |  INT  |  LONG  |  FLOAT  |  DOUBLE )   ;

resultType :  ( VOID  |  type)   ;

name :  identifier  ( DOT identifier )*   ;

simpleName :  identifier    ;

identifier :  ( MODULE |  REQUIRES |  TO |  WITH |  OPEN |  OPENS |  USES |  EXPORTS |  PROVIDES |  TRANSITIVE |  ENUM |  STRICTFP |  YIELD |  RECORD |  IDENTIFIER)    ;

expression :  conditionalExpression ( ( assignmentOperator expression  |  ARROW lambdaBody  |  DOUBLECOLON ( typeArguments)? ( identifier |  NEW) ))?   ;

assignmentOperator :  ( ASSIGN  |  STARASSIGN  |  SLASHASSIGN  |  REMASSIGN  |  PLUSASSIGN  |  MINUSASSIGN  |  LSHIFTASSIGN  |  RSIGNEDSHIFTASSIGN  |  RUNSIGNEDSHIFTASSIGN  |  ANDASSIGN  |  XORASSIGN  |  ORASSIGN )   ;

conditionalExpression :  conditionalOrExpression ( HOOK expression COLON expression )?   ;

conditionalOrExpression :  conditionalAndExpression ( SC_OR conditionalAndExpression )*   ;

conditionalAndExpression :  inclusiveOrExpression ( SC_AND inclusiveOrExpression )*   ;

inclusiveOrExpression :  exclusiveOrExpression ( BIT_OR exclusiveOrExpression )*   ;

exclusiveOrExpression :  andExpression ( XOR andExpression )*   ;

andExpression :  equalityExpression ( BIT_AND equalityExpression )*   ;

equalityExpression :  instanceOfExpression ( ( EQ  |  NE ) instanceOfExpression )*   ;

patternExpression :  annotatedReferenceType simpleName   ;

instanceOfExpression :  relationalExpression ( INSTANCEOF ( patternExpression  |  annotatedReferenceType ))?   ;

relationalExpression :  shiftExpression ( ( LT  |  GT  |  LE  |  GE ) shiftExpression )*   ;

shiftExpression :  additiveExpression ( ( LSHIFT  |  rSIGNEDSHIFT  |  rUNSIGNEDSHIFT ) additiveExpression )*   ;

additiveExpression :  multiplicativeExpression ( ( PLUS  |  MINUS ) multiplicativeExpression )*   ;

multiplicativeExpression :  unaryExpression ( ( STAR  |  SLASH  |  REM ) unaryExpression )*   ;

unaryExpression :  ( preIncrementExpression |  preDecrementExpression |  ( PLUS  |  MINUS ) unaryExpression  |  unaryExpressionNotPlusMinus)   ;

preIncrementExpression :  INCR  unaryExpression    ;

preDecrementExpression :  DECR  unaryExpression    ;

unaryExpressionNotPlusMinus :  ( ( TILDE  |  BANG ) unaryExpression  |  castExpression |  postfixExpression |  switchExpression)   ;

postfixExpression :  primaryExpression ( ( INCR  |  DECR ) )?   ;

castExpression :  LPAREN  annotations ( primitiveType RPAREN unaryExpression  |  referenceType  ( BIT_AND annotatedReferenceType )* RPAREN unaryExpressionNotPlusMinus )   ;

primaryExpression :  primaryPrefix ( primarySuffix)*   ;

primaryExpressionWithoutSuperSuffix :  primaryPrefix ( primarySuffixWithoutSuper)*   ;

primaryPrefix :  ( literal |  THIS  |  SUPER  ( DOT ( typeArguments)? simpleName ( arguments )?  |  DOUBLECOLON ( typeArguments)? ( identifier |  NEW) ) |  LPAREN  ( RPAREN  |  lambdaParameters RPAREN  |  inferredLambdaParameters RPAREN  |  expression RPAREN ) |  allocationExpression |  resultType DOT CLASS  |  annotatedType DOUBLECOLON ( typeArguments)? ( identifier |  NEW)  |  simpleName  ( arguments )? )   ;

primarySuffix :  ( primarySuffixWithoutSuper |  DOT SUPER )   ;

primarySuffixWithoutSuper :  ( DOT ( THIS  |  allocationExpression |  ( typeArguments)? simpleName ( arguments )? ) |  LBRACKET expression RBRACKET )   ;

literal :  ( INTEGER_LITERAL  |  LONG_LITERAL  |  FLOATING_POINT_LITERAL  |  CHARACTER_LITERAL  |  STRING_LITERAL  |  TEXT_BLOCK_LITERAL  |  booleanLiteral |  nullLiteral)   ;

booleanLiteral :  ( TRUE  |  FALSE )   ;

nullLiteral :  NULL   ;

arguments :  LPAREN ( argumentList)? RPAREN   ;

argumentList :  expression  ( COMMA expression )*   ;

allocationExpression :  NEW  ( typeArguments)? annotations ( primitiveType arrayCreation |  classOrInterfaceType ( arrayCreation |  arguments ( classOrInterfaceBody)? ))   ;

arrayCreation :  ( annotations LBRACKET  ( expression)?  RBRACKET )+ ( arrayInitializer)?   ;

statement :   ( labeledStatement |  assertStatement |  yieldStatement |  block |  emptyStatement |  statementExpression |  switchStatement |  ifStatement |  whileStatement |  doStatement |  forStatement |  breakStatement |  continueStatement |  returnStatement |  throwStatement |  synchronizedStatement |  tryStatement)   ;

assertStatement :  ASSERT  expression ( COLON expression)? SEMICOLON   ;

labeledStatement :  simpleName  COLON statement   ;

block :  LBRACE   statements RBRACE   ;

blockStatement :   ( modifiers classOrInterfaceDeclaration  |  modifiers recordDeclaration  |  variableDeclarationExpression SEMICOLON  |  statement)   ;

variableDeclarationExpression :  modifiers type variableDeclarator  ( COMMA variableDeclarator )*   ;

emptyStatement :  SEMICOLON   ;

lambdaBody :  ( expression  |  block)   ;

statementExpression :  ( preIncrementExpression |  preDecrementExpression |  primaryExpression (( INCR  |  DECR  |  assignmentOperator expression ))?) SEMICOLON   ;

switchStatement :  SWITCH  LPAREN expression RPAREN LBRACE ( switchEntry )* RBRACE   ;

switchExpression :  SWITCH  LPAREN expression RPAREN LBRACE ( switchEntry )* RBRACE   ;

switchEntry :  ( CASE  conditionalExpression  ( COMMA conditionalExpression )* |  US__DEFAULT ) ( COLON statements  |  ARROW ( expression SEMICOLON  |  block  |  throwStatement ))   ;

ifStatement :  IF  LPAREN expression RPAREN statement ( ELSE statement)?   ;

whileStatement :  WHILE  LPAREN expression RPAREN statement   ;

doStatement :  DO  statement WHILE LPAREN expression RPAREN SEMICOLON   ;

forStatement :  FOR  LPAREN ( variableDeclarationExpression COLON expression |  ( forInit)? SEMICOLON ( expression)? SEMICOLON ( forUpdate)?) RPAREN statement   ;

forInit :  ( variableDeclarationExpression  |  expressionList)   ;

expressionList :  expression  ( COMMA expression )*   ;

forUpdate :  expressionList   ;

breakStatement :  BREAK  ( simpleName)? SEMICOLON   ;

yieldStatement :  YIELD  expression SEMICOLON   ;

continueStatement :  CONTINUE  ( simpleName)? SEMICOLON   ;

returnStatement :  RETURN  ( expression)? SEMICOLON   ;

throwStatement :  THROW  expression SEMICOLON   ;

synchronizedStatement :  SYNCHRONIZED  LPAREN expression RPAREN block   ;

tryStatement :  TRY  ( resourceSpecification)? block ( ( CATCH  LPAREN modifiers  referenceType  ( BIT_OR annotatedReferenceType )* variableDeclaratorId  RPAREN block )* ( FINALLY block)? |  FINALLY block)   ;

resourceSpecification :  LPAREN resources ( SEMICOLON)? RPAREN   ;

resources :  resource  ( SEMICOLON resource )*   ;

resource :  ( variableDeclarationExpression |  primaryExpression)   ;

rUNSIGNEDSHIFT :  GT GT GT  ;

rSIGNEDSHIFT :  GT GT  ;

annotations :  ( annotation )*   ;

annotation :  AT  name ( LPAREN ( memberValuePairs)? RPAREN  |  LPAREN memberValue RPAREN  |  )   ;

memberValuePairs :  memberValuePair  ( COMMA memberValuePair )*   ;

memberValuePair :  simpleName  ASSIGN memberValue   ;

memberValue :  ( annotation |  memberValueArrayInitializer |  conditionalExpression)   ;

memberValueArrayInitializer :  LBRACE  ( memberValue  ( COMMA memberValue )*)? ( COMMA)? RBRACE   ;

annotationTypeDeclaration :  AT  INTERFACE simpleName annotationTypeBody   ;

annotationTypeBody :  LBRACE (( annotationBodyDeclaration  |  SEMICOLON))* RBRACE   ;

annotationBodyDeclaration :  modifiers ( annotationTypeMemberDeclaration |  classOrInterfaceDeclaration |  enumDeclaration |  annotationTypeDeclaration |  fieldDeclaration)   ;

annotationTypeMemberDeclaration :  type simpleName LPAREN RPAREN ( defaultValue)? SEMICOLON   ;

defaultValue :  US__DEFAULT memberValue   ;

moduleDirective :  ( REQUIRES  TRANSITIVE  SEMICOLON  |  REQUIRES  modifiers name SEMICOLON  |  EXPORTS  name ( TO name  ( COMMA name )*)? SEMICOLON  |  OPENS  name ( TO name  ( COMMA name )*)? SEMICOLON  |  USES  name SEMICOLON  |  PROVIDES  name WITH name  ( COMMA name )* SEMICOLON )   ;

moduleDeclaration :  ( OPEN )? MODULE  name LBRACE ( moduleDirective )* RBRACE   ;

blockParseStart :  block EOF   ;

blockStatementParseStart :  ( blockStatement |  explicitConstructorInvocation) EOF   ;

importDeclarationParseStart :  importDeclaration EOF   ;

expressionParseStart :  expression EOF   ;

annotationParseStart :  annotation EOF   ;

annotationBodyDeclarationParseStart :  annotationBodyDeclaration EOF   ;

classOrInterfaceBodyDeclarationParseStart :  classOrInterfaceBodyDeclaration EOF   ;

classOrInterfaceTypeParseStart :  annotatedClassOrInterfaceType EOF   ;

resultTypeParseStart :  annotations resultType EOF   ;

variableDeclarationExpressionParseStart :  variableDeclarationExpression EOF   ;

explicitConstructorInvocationParseStart :  explicitConstructorInvocation EOF   ;

nameParseStart :  name EOF   ;

simpleNameParseStart :  simpleName EOF   ;

parameterParseStart :  parameter EOF   ;

packageDeclarationParseStart :  packageDeclaration EOF   ;

typeDeclarationParseStart :  modifiers ( classOrInterfaceDeclaration |  enumDeclaration |  annotationTypeDeclaration) EOF   ;

moduleDeclarationParseStart :  modifiers moduleDeclaration EOF   ;

moduleDirectiveParseStart :  moduleDirective EOF   ;

typeParameterParseStart :  annotations typeParameter EOF   ;

methodDeclarationParseStart :  modifiers methodDeclaration EOF   ;
