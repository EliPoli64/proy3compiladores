/* JFlex example: partial Java language lexer specification */
import java_cup.runtime.*;

/**
  * This class is a simple example lexer.
  */
%%

%class LexerProyUno
%unicode
%cup
%line
%column

%{
  StringBuffer string = new StringBuffer();

  private Symbol symbol(int type) {
    return new Symbol(type, yyline, yycolumn);
  }
  private Symbol symbol(int type, Object value) {
    return new Symbol(type, yyline, yycolumn, value);
  }
%}

WhiteSpace     = [ \t\f\r\n]

/* comments */
EndOfLineComment        = \|.*
MultiLineCommentStart   = є
MultiLineCommentEnd     = э

IntegerLiteral    = 0|[1-9][0-9]*
FloatLiteral      = [0-9]+"."[0-9]+
BooleanLiteral    = true|false
LabelLiteral      = {IntegerLiteral}":"
FloatLabel        = {FloatLiteral}":"
IdentLabel        = {Identificador}":"
BooleanLabel      = {BooleanLiteral}":"

Identificador     = [a-zA-Z_][a-zA-Z0-9_]*

%state STRING
%state CHAR
%state MULTILINECOMMENT
%%

/* keywords */
<YYINITIAL> "navidad"        { return symbol(sym.NAVIDAD); }
<YYINITIAL> "coal"           { return symbol(sym.COAL); }
<YYINITIAL> "gift"           { return symbol(sym.GIFT); }
<YYINITIAL> "world"          { return symbol(sym.WORLD); }
<YYINITIAL> "int"            { return symbol(sym.INT); }
<YYINITIAL> "float"          { return symbol(sym.FLOAT); }
<YYINITIAL> "bool"           { return symbol(sym.BOOL); }
<YYINITIAL> "char"           { return symbol(sym.CHAR); }
<YYINITIAL> "string"         { return symbol(sym.STRING); }
<YYINITIAL> "void"           { return symbol(sym.VOID); }
<YYINITIAL> "show"           { return symbol(sym.SHOW); }
<YYINITIAL> "get"            { return symbol(sym.GET); }

<YYINITIAL> {
  /* literals */
  {IntegerLiteral}    { return symbol(sym.INTEGER_LITERAL, Integer.parseInt(yytext())); }
  {FloatLiteral}      { return symbol(sym.FLOAT_LITERAL, Double.parseDouble(yytext())); }
  {BooleanLiteral}    { return symbol(sym.BOOLEAN_LITERAL, Boolean.parseBoolean(yytext())); }
  
  /* operadores y simbolos */
  ":"                 { return symbol(sym.COLON); }
  "¿"                 { return symbol(sym.LPAREN); }
  "?"                 { return symbol(sym.RPAREN); }
  "+"                 { return symbol(sym.PLUS); }
  "-"                 { return symbol(sym.MINUS); }
  "*"                 { return symbol(sym.TIMES); }
  "//"                { return symbol(sym.DIV_INT); }
  "/"                 { return symbol(sym.DIV_FLOAT); }
  "%"                 { return symbol(sym.MOD); }
  "^"                 { return symbol(sym.POW); }
  "++"                { return symbol(sym.INC); }
  "--"                { return symbol(sym.DEC); }
  "<"                 { return symbol(sym.LT); }
  "<="                { return symbol(sym.LE); }
  ">"                 { return symbol(sym.GT); }
  ">="                { return symbol(sym.GE); }
  "=="                { return symbol(sym.EQ); }
  "!="                { return symbol(sym.NE); }
  "@"                 { return symbol(sym.AND); }
  "~"                 { return symbol(sym.OR); }
  "Σ"                 { return symbol(sym.NOT); }
  "="                 { return symbol(sym.ASSIGN); }
  ","                 { return symbol(sym.COMMA); }
  "¡"                 { return symbol(sym.LBRACKET); }
  "!"                 { return symbol(sym.RBRACKET); }
  "["                 { return symbol(sym.DECLBRACKETL, yytext()); }
  "]"                 { return symbol(sym.DECLBRACKETR, yytext()); }
  "endl"              { return symbol(sym.ENDL); }

  \'                  { string.setLength(0); yybegin(CHAR); }
  \"                  { string.setLength(0); yybegin(STRING); }
  /* operadores y simbolos */
  "¿"             { return symbol(sym.LPAREN); }
  "?"             { return symbol(sym.RPAREN); }
  "+"             { return symbol(sym.PLUS); }
  "-"             { return symbol(sym.MINUS); }
  "*"             { return symbol(sym.TIMES); }
  "//"            { return symbol(sym.DIV_INT); }
  "/"             { return symbol(sym.DIV_FLOAT); }
  "%"             { return symbol(sym.MOD); }
  "^"             { return symbol(sym.POW); }
  "++"            { return symbol(sym.INC); }
  "--"            { return symbol(sym.DEC); }
  "<"             { return symbol(sym.LT); }
  "<="            { return symbol(sym.LE); }
  ">"             { return symbol(sym.GT); }
  ">="            { return symbol(sym.GE); }
  "=="            { return symbol(sym.EQ); }
  "!="            { return symbol(sym.NE); }
  "@"             { return symbol(sym.AND); }
  "~"             { return symbol(sym.OR); }
  "Σ"             { return symbol(sym.NOT); }
  "="             { return symbol(sym.ASSIGN); }
  ","             { return symbol(sym.COMMA); }
  "¡"             { return symbol(sym.LBRACKET); }
  "!"             { return symbol(sym.RBRACKET); }
  "endl"          { return symbol(sym.ENDL); }
  ";"             { return symbol(sym.SEMI); }
  

  /* control structures */
  "decide"                  { return symbol(sym.DECIDE); }
  "of"                      { return symbol(sym.OF); }
  "else"                    { return symbol(sym.ELSE); }
  "end"                     { return symbol(sym.END); }
  "loop"                    { return symbol(sym.LOOP); }
  "exit"                    { return symbol(sym.EXIT); }
  "when"                    { return symbol(sym.WHEN); }
  "for"                     { return symbol(sym.FOR); }
  "return"                  { return symbol(sym.RETURN); }
  "break"                   { return symbol(sym.BREAK); }
  "to"                      { return symbol(sym.TO); }
  "local"                   { return symbol(sym.LOCAL); }
  "->"                      { return symbol(sym.ARROW); }

  /* identifiers */ 
  {Identificador}                   { return symbol(sym.IDENTIFIER, yytext()); }

  /* comments */
  {EndOfLineComment}     { /* ignore */ }
  {MultiLineCommentStart} { yybegin(MULTILINECOMMENT); }

  /* whitespace */
  {WhiteSpace}                   { /* ignore */ }
}

<MULTILINECOMMENT> {
  {MultiLineCommentEnd}   { yybegin(YYINITIAL); }
  . | [^э]                   { /* ignore */ }
  "endl"                  { /* ignore */ }
  <<EOF>>                    { 
                               System.err.println("Error, no fue cerrado el comentario"); 
                               yybegin(YYINITIAL); 
                             }
}

<CHAR> {
  /* escape para caracteres */
  \\t                        { string.append('\t'); }
  \\n                        { string.append('\n'); }
  \\r                        { string.append('\r'); }
  \\'                        { string.append('\''); }
  \\\\                       { string.append('\\'); }

  [^'\\]                     { string.append(yytext()); }
  
  /* fin */
  \'                         { 
                               yybegin(YYINITIAL); 
                               return symbol(sym.CHAR_LITERAL, string.length() > 0 ? string.charAt(0) : '\0'); 
                             }
  
  <<EOF>>                    { 
                               System.err.println("Error: no se cerro el caracter");
                               yybegin(YYINITIAL); 
                               return symbol(sym.CHAR_LITERAL, '\0');
                             }
}

<STRING> {
  \"                             { yybegin(YYINITIAL); 
                                    return symbol(sym.STRING_LITERAL, 
                                    string.toString()); }
  [^\n\r\"\\]+                   { string.append( yytext() ); }
  \\t                            { string.append('\t'); }
  \\n                            { string.append('\n'); }

  \\r                            { string.append('\r'); }
  \\\"                           { string.append('\"'); }
  \\                             { string.append('\\'); }

  <<EOF>>                    { 
                               System.err.println("Error: no se cerro el string");
                               yybegin(YYINITIAL); 
                               return symbol(sym.STRING_LITERAL, string.toString());
                             }
}

/* error fallback */
[^] {
    System.err.println("error en línea " + (yyline+1) + ", columna " + (yycolumn+1) + ": Carácter inválido '" + yytext() + "'");
}