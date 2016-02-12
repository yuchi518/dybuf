%{

    #include <stdio.h>
    #include <string.h>
    #include <stdlib.h>
    #include "cjson.h"
    #include "cjson_runtime.h"

    // bison json.y
    typedef void* YYSTYPE;
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
    : ARRAY { /*printf("%s",$1);*/printf("START(ARRAY)\n"); }
    | OBJECT { /*printf("%s",$1);*/printf("START(OBJECT)\n"); }
    ;

OBJECT
    : O_BEGIN O_END { $$ = cjson_make_map();printf("OBJECT(O_BEGIN O_END)\n"); }
    | O_BEGIN MEMBERS O_END
        {
            printf("OBJECT(O_BEGIN MEMBERS O_END)\n");
            /*$$ = (char *)malloc(sizeof(char)*(1+strlen($2)+1+1));
            sprintf($$,"{%s}",$2);*/
        }
    ;

MEMBERS
    : PAIR { $$ = $1;printf("MEMBERS(PAIR)\n"); }
    | PAIR COMMA MEMBERS
        {
            printf("MEMBERS(PAIR COMMA MEMBERS)\n");
            //$$ = (char *)malloc(sizeof(char)*(strlen($1)+1+strlen($3)+1));
            //sprintf($$,"%s,%s",$1,$3);
        }
    ;

PAIR: KEY COLON VALUE {
    printf("PAIR(STRING COLON VALUE)\n");
    //$$ = (char *)malloc(sizeof(char)*(strlen($1)+1+strlen($3)+1));
    //sprintf($$,"%s:%s",$1,$3);
  }
;

ARRAY
    : A_BEGIN A_END { $$ = cjson_make_array();printf("ARRAY(A_BEGIN A_END)\n"); }
    | A_BEGIN ELEMENTS A_END
        {
            printf("ARRAY(A_BEGIN ELEMENTS A_END)\n");
            //$$ = (char *)malloc(sizeof(char)*(1+strlen($2)+1+1));
            //sprintf($$,"[%s]",$2);
        }
    ;

ELEMENTS
    : VALUE { $$ = $1; printf("ELEMENTS(VALUE)\n"); }
    | VALUE COMMA ELEMENTS
        {
            printf("ELEMENTS(VALUE COMMA ELEMENTS)\n");
            //$$ = (char *)malloc(sizeof(char)*(strlen($1)+1+strlen($3)+1));
            //sprintf($$,"%s,%s",$1,$3);
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

/*int main()
{
   printf("\n");
   yyparse();
   printf("\n");
   return 0;
}*/

/*int yywrap()
{
   return 1;
}*/

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
