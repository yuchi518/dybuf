%{
    // flex -o json.lex.c json.l
    #include <stdio.h>
    #include "json.yacc.c"
    char *strclone(char *str);
%}

DIGIT1to9       [1-9]
DIGIT           [0-9]
DIGITS          {DIGIT}+
HEX_DIGIT       [0-9a-fA-F]
HEX_DIGITS      {HEX_DIGIT}+
INT             {DIGIT}|{DIGIT1to9}{DIGITS}|-{DIGIT}|-{DIGIT1to9}{DIGITS}|0x{HEX_DIGITS}
FRAC            [.]{DIGITS}
EXP             {E}{DIGITS}
E               [eE][+-]?
NUMBER          {INT}|{INT}{FRAC}|{INT}{EXP}|{INT}{FRAC}{EXP}
UNESCAPEDCHAR   [ -!#-\[\]-~]
ESCAPEDCHAR     \\["\\bfnrt/]
UNICODECHAR     \\u{HEX_DIGIT}{HEX_DIGIT}{HEX_DIGIT}{HEX_DIGIT}
CHAR            {UNESCAPEDCHAR}|{ESCAPEDCHAR}|{UNICODECHAR}
CHARS           {CHAR}+
DBL_QUOTE       ["]

%%

{DBL_QUOTE}{DBL_QUOTE}|{DBL_QUOTE}{CHARS}{DBL_QUOTE} {
    yylval=strclone(yytext);
    return STRING;
};

{NUMBER} {
    yylval=strclone(yytext);
    return NUMBER;
};

true {
    return true;
};

false {
    return false;
};

null {
    return null;
};

\{ {
    return O_BEGIN;
};

\} {
    return O_END;
};

\[ {
    return A_BEGIN;
};

\] {
    return A_END;
};

, {
    return COMMA;
};

: {
    return COLON;
};

[ \t\n]+ /* ignore whitespace */;

. {
    printf("Unexpected: %c\nExiting...\n",*yytext);
    exit(0);
};

%%

char *strclone(char *str)
{
    int len = strlen(str);
    char *clone = (char *)malloc(sizeof(char)*(len+1));
    strcpy(clone,str);
    return clone;
}
