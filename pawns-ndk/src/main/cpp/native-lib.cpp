#include <jni.h>
#include <string>

#ifdef ARCH_ARM64_V8A
#include "arm64-v8a/libpawns_mobile_sdk.h"
#elif defined(ARCH_ARMEABI_V7A)
#include "armeabi-v7a/libpawns_mobile_sdk.h"
#elif defined(ARCH_X86)
#include "x86/libpawns_mobile_sdk.h"
#elif defined(ARCH_X86_64)
#include "x86_64/libpawns_mobile_sdk.h"
#else
#error "Unsupported architecture"
#endif

// Include the generated header file from your Go code
extern "C" {


static JavaVM* javaVM = nullptr;
static jobject globalCallback = nullptr;

// Define the callback function that matches the expected type
void myCallback(char* message) {
    JNIEnv* env;
    javaVM->AttachCurrentThread(reinterpret_cast<JNIEnv**>(&env), nullptr);

    jclass callbackClass = env->GetObjectClass(globalCallback);
    jmethodID methodID = env->GetMethodID(callbackClass, "onCallback", "(Ljava/lang/String;)V");
    jstring javaMessage = env->NewStringUTF(message);

    env->CallVoidMethod(globalCallback, methodID, javaMessage);

    env->DeleteLocalRef(javaMessage);
    javaVM->DetachCurrentThread();
}

JNIEXPORT void JNICALL
Java_com_pawns_ndk_NativeLib_Initialize(JNIEnv *env, jobject obj, jstring rawDeviceID, jstring rawDeviceName) {
    const char *cRawDeviceID = env->GetStringUTFChars(rawDeviceID, 0);
    const char *cRawDeviceName = env->GetStringUTFChars(rawDeviceName, 0);

    Initialize((char *) cRawDeviceID, (char *) cRawDeviceName);

    env->ReleaseStringUTFChars(rawDeviceID, cRawDeviceID);
    env->ReleaseStringUTFChars(rawDeviceName, cRawDeviceName);
}

// JNI function to start the main routine
JNIEXPORT void JNICALL
Java_com_pawns_ndk_NativeLib_StartMainRoutine(JNIEnv* env, jobject obj, jstring rawAccessToken, jobject callback) {
    const char* nativeAccessToken = env->GetStringUTFChars(rawAccessToken, 0);

    // Store the Java callback globally
    if (globalCallback != nullptr) {
        env->DeleteGlobalRef(globalCallback);
    }
    globalCallback = env->NewGlobalRef(callback);

    // Store the JavaVM instance
    if (javaVM == nullptr) {
        env->GetJavaVM(&javaVM);
    }

    // Start the main routine with the native callback function
    StartMainRoutine((char*)nativeAccessToken, (void*)myCallback);

    env->ReleaseStringUTFChars(rawAccessToken, nativeAccessToken);
}

JNIEXPORT void JNICALL
Java_com_pawns_ndk_NativeLib_StopMainRoutine(JNIEnv *env, jobject obj) {
    StopMainRoutine();
}
}