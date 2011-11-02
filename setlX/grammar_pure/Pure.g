grammar Pure;

block
    :
      statement*
    ;

statement
    :
      'var' variable ';'
    | expr ';'
    | 'if'         '(' condition ')' '{' block '}'
      ('else' 'if' '(' condition ')' '{' block '}' )*
      ('else'                        '{' block '}' )?
    | 'switch' '{'
          ('case' condition ':' block )*
          ('default'        ':' block )?
      '}'
    | 'for'   '(' iterator  ')' '{' block '}'
    | 'while' '(' condition ')' '{' block '}'
    | 'return' expr? ';'
    | 'continue' ';'
    | 'break' ';'
    | 'exit' ';'
    ;

variable
    :
      ID
    ;

condition
    :
      expr
    ;

expr
    :
      ( assignment       )=> assignment
    | 'forall' '(' iterator '|' condition ')'
    | 'exists' '(' iterator '|' condition ')'
    | implication
    ;

assignment
    :
      (variable ('(' sum ')')* | idList) (':=' | '+=' | '-=' | '*=' | '/=' | '%=') expr
    ;

idList
    :
      '[' explicitIdList ']'
    ;

explicitIdList
    :
      (assignable | '-') (',' (assignable | '-'))*
    ;

assignable
    :
      variable
    | idList
    ;

implication
    :
      disjunction ('->' implication)?
    ;

disjunction
    :
      conjunction ('||' conjunction)*
    ;

conjunction
    :
      equation ('&&' equation)*
    ;

equation
    :
      comparison (('==' | '!=') comparison)*
    ;

comparison
    :
      inclusion (('<' | '<=' | '>' | '>=') inclusion)*
    ;

inclusion
    :
      sum (('in' | 'notin') sum)*
    ;

sum
    :
      product (('+' | '-' | '+/') product)*
    ;

product
    :
      power (('*' | '/' | '%') power)*
    ;

power
    :
      minmax ('**' power)?
    ;

minmax
    :
      factor (('min' | 'min/' | 'max' | 'max/') factor)?
    ;

factor
    :
      '(' expr ')'
    | 'min/'       factor
    | 'max/'       factor
    | '+/'         factor
    | '*/'         factor
    | '-'          factor
    | '!'          factor
    | '#'          factor
    | call
    | list
    | set
    | value
    ;

// this could be either 'variable' or 'call' or 'element of collection'
// decide at runtime
call
    :
      variable ( '(' callParameters ')' | '{' expr '}' )*
    ;

callParameters
    :
      (
         expr ((',' expr)* | '..' sum?)
       | '..' sum
      )?
    ;

list
    :
      '[' constructor? ']'
    ;

set
    :
      '{' constructor? '}'
    ;

constructor
    :
      ( range   )=> range
    | ( iterate )=> iterate
    | explicitList
    ;

range
    :
      expr (',' expr)? '..' expr
    ;

iterate
    :
      ( shortIterate )=> shortIterate
    | expr ':' iterator ('|' condition)?
    ;

shortIterate
    :
      assignable 'in' expr '|' condition
    ;

iterator
    :
      assignable 'in' expr (',' assignable 'in' expr )*
    ;

explicitList
    :
      expr (',' expr)*
    ;

value
    :
      definition
    | lambdaDefinition
    | atomicValue
    ;

definition
    :
      'procedure' '(' definitionParameters ')' '{' block '}'
    ;

definitionParameters
    :
      ( definitionParameter (',' definitionParameter)* )?
    ;

lambdaDefinition
    :
      'f(' variable (',' variable)* ')>(' expr ')'
    ;

definitionParameter
    :
      'rw' variable
    | variable
    ;

atomicValue
    :
      NUMBER
    | real
    | STRING
    | 'true'
    | 'false'
    | 'om'
    ;

real
    :
      NUMBER? REAL
    ;

ID              : ('a' .. 'z' | 'A' .. 'Z')('a' .. 'z' | 'A' .. 'Z'|'_'|'0' .. '9')* ;
NUMBER          : '0'|('1' .. '9')('0' .. '9')*;
REAL            : '.'('0' .. '9')+ (('e'|'E') '-'? ('0' .. '9')+)?;
STRING          : '"' ('\\"'|~('"'))* '"';

LINE_COMMENT    : '//' ~('\n')*                             { skip(); } ;
MULTI_COMMENT   : '/*' (~('*') | '*'+ ~('*'|'/'))* '*'+ '/' { skip(); } ;
WS              : (' '|'\t'|'\n'|'r')                       { skip(); } ;

