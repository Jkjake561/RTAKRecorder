cmake_minimum_required(VERSION 3.22)

project(RTAKrecorder)

add_library(Codec2Binding SHARED Codec2Binding.cpp)

target_include_directories(Codec2Binding PUBLIC ./codec2)

add_library(Codec2Lib SHARED IMPORTED)
set_property(TARGET Codec2Lib PROPERTY IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/codec2/libcodec2.so)

target_link_libraries(Codec2Binding Codec2Lib)