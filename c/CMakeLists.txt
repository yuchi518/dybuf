cmake_minimum_required(VERSION 3.3)
project(dybuf_c)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c99")

set(SOURCE_FILES main.c)
add_executable(dybuf_c ${SOURCE_FILES})