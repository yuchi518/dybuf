cmake_minimum_required(VERSION 3.3)
project(dybuf_c)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c99")
include_directories(json)

set(SOURCE_FILES main.c)
add_library(json json/json.lex.c)

add_executable(dybuf_c ${SOURCE_FILES})

target_link_libraries(dybuf_c json)