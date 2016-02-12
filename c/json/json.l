%option noyywrap
%x IFILE

%{
    // flex -o json.lex.c json.l
    #include "json.yacc.c"
    #include "plat_io.h"
    #include "cjson_runtime.h"

    char *strclone(char *str);

    struct src_stack
    {
        struct src_stack *prev;
        YY_BUFFER_STATE bs;
        int lineno;
        char* src_buffer;
        unsigned int src_size;
    } *curbs = 0;

%}

DIGIT1to9       [1-9]
DIGIT           [0-9]
DIGITS          {DIGIT}+
HEX_DIGIT       [0-9a-fA-F]
HEX_DIGITS      {HEX_DIGIT}+
INT10           {DIGIT}|{DIGIT1to9}{DIGITS}|-{DIGIT}|-{DIGIT1to9}{DIGITS}
INT16           0x{HEX_DIGITS}
INT             {INT10}|{INT16}
FRAC            [.]{DIGITS}
EXP             {E}{DIGITS}
E               [eE][+-]?
DOUBLE          {INT10}{FRAC}|{INT10}{EXP}|{INT10}{FRAC}{EXP}
UNESCAPEDCHAR   [ -!#-\[\]-~]
ESCAPEDCHAR     \\["\\bfnrt/]
UNICODECHAR     \\u{HEX_DIGIT}{HEX_DIGIT}{HEX_DIGIT}{HEX_DIGIT}
CHAR            {UNESCAPEDCHAR}|{ESCAPEDCHAR}|{UNICODECHAR}
CHARS           {CHAR}+
DBL_QUOTE       ["]


%%

{DBL_QUOTE}{DBL_QUOTE}|{DBL_QUOTE}{CHARS}{DBL_QUOTE} {
    yylval = cjson_make_string(yytext);
    return STRING;
};

{INT} {
    yylval = cjson_make_int_from_string(yytext);
    return NUMBER;
};

{DOUBLE} {
    yylval = cjson_make_double_from_string(yytext);
    return NUMBER;
};

true {
    yylval = cjson_make_bool(true);
    return TRUE_T;
};

false {
    yylval = cjson_make_bool(false);
    return FALSE_T;
};

null {
    yylval = cjson_make_nil();
    return NULL_T;
};

nil {
    yylval = cjson_make_nil();
    return NULL_T;
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

[ \t]+ /* ignore whitespace */;

^"#"[ \t]*include[ \t]*\[\"<] {
    BEGIN IFILE;
}

<IFILE>[^ \t\n\">]+ {
    {
        int c;
        while((c = input()) && c != '\n') ;
    }
    yylineno++;
    if(cjson_source_push_from_resource(yytext) != jserr_no_error)
        yyterminate(); /* no such file */ BEGIN INITIAL;
}

<IFILE>.|\n {
    fprintf(stderr, "%4d bad include line\n", yylineno);
}

<<EOF>> {
    if(cjson_source_pop()!=jserr_no_error)
        yyterminate();
}

^\n { fprintf(yyout, "%4d %s", yylineno++, yytext); }

^. { fprintf(yyout, "%4d %s", yylineno, yytext); }

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
