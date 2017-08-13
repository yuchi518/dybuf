%{

    #include <stdio.h>
    #include <string.h>
    #include <stdlib.h>
    #include "cjson.h"
    #include "cjson_runtime.h"

    // bison json.y
    typedef struct jsobj* YYSTYPE;
    #define YYSTYPE_IS_DECLARED     1

    char *strconcat(char *str1, char *str2);
    int yylex();
    void yyerror (char const *s);
%}

%token  NUMBER
%token  STRING
%token  TRUE_T FALSE_T NULL_T
%left   O_BEGIN O_END A_BEGIN A_END
%left   COMMA
%left   COLON

%%

START
    : CODE_SEG
    | CODE_SEG CODE_SEG

CODE_SEG
    : ARRAY {
        $$ = cjson_rt_add_code_segment($1);
        cjson_release($1);
        printf("CODE_SEG(ARRAY)\n");
    }
    | OBJECT {
        $$ = cjson_rt_add_code_segment($1);
        cjson_release($1);
        printf("CODE_SEG(OBJECT)\n");
    }
    ;

OBJECT
    : O_BEGIN O_END {
        $$ = cjson_make_map();
        printf("OBJECT(O_BEGIN O_END)\n");
    }
    | O_BEGIN MEMBERS O_END {
        $$ = $2;
        printf("OBJECT(O_BEGIN MEMBERS O_END)\n");
    }
    ;

MEMBERS
    : PAIR {
        struct jsobj* map = cjson_make_map();
        cjson_map_add_tuple(map, _obj2inst_t($1));
        cjson_release($1);
        $$ = map;
        printf("MEMBERS(PAIR)\n");
    }
    | PAIR COMMA MEMBERS {
        struct jsobj* map = $3;
        cjson_map_add_tuple(map, _obj2inst_t($1));
        cjson_release($1);
        $$ = map;
        printf("MEMBERS(PAIR COMMA MEMBERS)\n");
    }
    ;

PAIR: KEY COLON VALUE {
        struct jsobj* tuple = cjson_make_tuple(2);
        cjson_tuple_set_object(tuple, 0, $1);
        cjson_tuple_set_object(tuple, 1, $3);
        cjson_release($1);
        cjson_release($3);
        $$ = tuple;
        printf("PAIR(KEY COLON VALUE)\n");
    }
    ;

ARRAY
    : A_BEGIN A_END {
        $$ = cjson_make_array();
        printf("ARRAY(A_BEGIN A_END)\n");
    }
    | A_BEGIN ELEMENTS A_END {
        $$ = $2;
        printf("ARRAY(A_BEGIN ELEMENTS A_END)\n");
    }
    ;

ELEMENTS
    : VALUE {
        struct jsobj* arr = cjson_make_array();
        cjson_array_add_object(arr, $1);
        cjson_release($1);
        $$ = arr;
        printf("ELEMENTS(VALUE)\n");
    }
    | VALUE COMMA ELEMENTS {
        // TO-DO: the order of items may not correct.
        struct jsobj* arr = (struct jsobj*)$3;
        cjson_array_add_object(arr, $1);
        cjson_release($1);
        $$ = arr;
        printf("ELEMENTS(VALUE COMMA ELEMENTS)\n");
    }
    ;

KEY
    : STRING { $$=yylval;printf("KEY(STRING)\n"); }
    | NUMBER { $$=yylval;printf("KEY(NUMBER)\n"); }
    | TRUE_T { $$=yylval;printf("KEY(TRUE_T)\n"); }
    | FALSE_T { $$=yylval;printf("KEY(FALSE_T)\n"); }
    | NULL_T { $$=yylval;printf("KEY(NULL_T)\n"); }
    ;

VALUE
    : STRING { $$=yylval;printf("VALUE(STRING)\n"); }
    | NUMBER { $$=yylval;printf("VALUE(NUMBER)\n"); }
    | OBJECT { $$=$1;printf("VALUE(OBJECT)\n"); }
    | ARRAY { $$=$1;printf("VALUE(ARRAY)\n"); }
    | TRUE_T { $$=yylval;printf("VALUE(TRUE_T)\n"); }
    | FALSE_T { $$=yylval;printf("VALUE(FALSE_T)\n"); }
    | NULL_T { $$=yylval;printf("VALUE(NULL_T)\n"); }
    ;

%%

int yywrap()
{
   return 1;
}

void yyerror (char const *s)
{
   fprintf (stderr, "%s\n", s);
}

char *strconcat(char *str1, char *str2)
{
    int len1 = strlen(str1);
    int len2 = strlen(str2);
    char *str3 = (char *)malloc(sizeof(char)*(len1+len2+1));
    strcpy(str3,str1);
    strcpy(&(str3[len1]),str2);
    return str3;
}
