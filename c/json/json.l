%option noyywrap
%x IFILE

%{
    // flex -o json.lex.c json.l
    #include "json.yacc.c"
    #include "plat_io.h"

    char *strclone(char *str);

    struct src_stack
    {
        struct src_stack *prev;
        YY_BUFFER_STATE bs;
        int lineno;
        char* src_buffer;
        unsigned int src_size;
    } *curbs = 0;

    int new_src(char* buff, unsigned int size);
    int new_file(char* filename);
    int pop_src(void);
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
    return TRUE_T;
};

false {
    return FALSE_T;
};

null {
    return NULL_T;
};

nil {
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
    if(!new_file(yytext)) yyterminate(); /* no such file */ BEGIN INITIAL;
}

<IFILE>.|\n {
    fprintf(stderr, "%4d bad include line\n", yylineno);
}

<<EOF>> {
    if(!pop_src()) yyterminate();
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

int new_src(char *buff, unsigned int size)
{
    struct src_stack *bs = malloc(sizeof(struct src_stack));
    if(!bs) { perror("malloc"); exit(1); }
    bs->src_buffer = malloc(size);
    if(!bs->src_buffer) { perror("malloc"); exit(1); }
    bs->src_size = size;
    memcpy(bs->src_buffer, buff, size);

    /* remember state */
    if(curbs) curbs->lineno = yylineno;
    bs->prev = curbs;

    /* set up current entry */
    bs->bs = yy_scan_bytes(bs->src_buffer, bs->src_size);
    yy_switch_to_buffer(bs->bs);
    curbs = bs;
    yylineno = 1;

    return 1;
}

int new_file(char* filename)
{
    unsigned int size;
    char *buf;

    int r = plat_io_get_resource(filename, (void**)&buf, &size);

    if (r) return r;
    if (!size) return 0;  // no content

    r = new_src(buf, size);
    free(buf);

    return r;
}

int pop_src(void)
{
    struct src_stack *bs = curbs;
    struct src_stack *prevbs;

    if(!bs) return 0;

    /* get rid of current entry */

    free(bs->src_buffer);
    yy_delete_buffer(bs->bs);

    /* switch back to previous */
    prevbs = bs->prev;
    free(bs);

    if(!prevbs) return 0;

    yy_switch_to_buffer(prevbs->bs);
    curbs = prevbs;
    yylineno = curbs->lineno;

    return 1;
}