# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project(pawns_ndk)

# Specifies a path to native header files.
set(SHARED_INCLUDES
        ${CMAKE_CURRENT_SOURCE_DIR}/cpp/include/
        ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt_libs/${ANDROID_ABI}/
)

# This function will have an effect on all the downstream CMakeLists files.
include_directories(${SHARED_INCLUDES})

# Creates and names a library, sets it as either STATIC or SHARED,
# and provides the relative paths to its source code.
add_library(
        ${CMAKE_PROJECT_NAME}
        SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/cpp/native-lib.cpp  # Use the correct source file here
)

# Include architecture-specific headers
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/cpp/include/arm64-v8a)
    target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE -DARCH_ARM64_V8A)
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/cpp/include/armeabi-v7a)
    target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE -DARCH_ARMEABI_V7A)
elseif (${ANDROID_ABI} STREQUAL "x86")
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/cpp/include/x86)
    target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE -DARCH_X86)
elseif (${ANDROID_ABI} STREQUAL "x86_64")
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/cpp/include/x86_64)
    target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE -DARCH_X86_64)
endif ()

# Add an imported library
add_library(
        imported-lib
        SHARED
        IMPORTED
)
set_property(TARGET imported-lib PROPERTY IMPORTED_NO_SONAME 1)
# Set properties for the imported library
set_target_properties(
        imported-lib
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt_libs/${CMAKE_ANDROID_ARCH_ABI}/libpawns_mobile_sdk.so
)

# Find the NDK log library
find_library(
        log-lib
        log
)

# Link libraries to the target library
target_link_libraries(
        ${CMAKE_PROJECT_NAME}
        imported-lib
        ${log-lib}
)
