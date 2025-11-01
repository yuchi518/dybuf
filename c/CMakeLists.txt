cmake_minimum_required(VERSION 3.3)
project(dybuf_c)

set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c99")
set (CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99")

include_directories(platform json)

add_custom_command(OUTPUT json/json.yacc.c
        PRE_BUILD
        COMMAND bison -o json.yacc.c json.y
        DEPENDS json/json.y
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}/json
        COMMENT "bison generate json.yacc.c" )

add_custom_command(OUTPUT json/json.lex.c
        PRE_BUILD
        COMMAND flex -o json.lex.c json.l
        DEPENDS json/json.l
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}/json
        COMMENT "flex generate json.lex.c" )

add_custom_target(RunGenerator DEPENDS json/json.yacc.c json/json.lex.c
        COMMENT "flex & bison")

set(SOURCE_FILES main.c platform/plat_mgn_mem.h)
add_library(json json/cjson.c json/cjson_runtime.c)
add_dependencies(json RunGenerator)

add_executable(dybuf_c ${SOURCE_FILES})

target_link_libraries(dybuf_c json)

add_executable(dybuf_fixtures fixtures/generate_fixtures.c)
add_executable(dybuf_verify_fixtures fixtures/verify_fixtures.c)
