#include <jni.h>
#include <string>
#include <mutex>
#include <android/log.h>

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

static JavaVM* javaVM = nullptr;
static jobject globalCallback = nullptr;
static std::mutex callbackMutex;

extern "C" {
// Define the callback function that matches the expected type
#define LOG_TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void myCallback(char* message) {
    JNIEnv* env = nullptr;
    bool attached = false;

    // Check if the current thread is already attached to the JVM
    if (javaVM->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        // If not, attach it
        if (javaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            LOGE("Failed to attach current thread to Java VM");
            return;
        }
    }

    // Assuming that the message should be passed to the Java layer
    if (env != nullptr && globalCallback != nullptr) {
        jclass callbackClass = env->GetObjectClass(globalCallback);
        if (callbackClass != nullptr) {
            jmethodID onCallbackMethod = env->GetMethodID(callbackClass, "onCallback", "(Ljava/lang/String;)V");
            if (onCallbackMethod != nullptr) {
                jstring jMessage = env->NewStringUTF(message);
                env->CallVoidMethod(globalCallback, onCallbackMethod, jMessage);
                env->DeleteLocalRef(jMessage);
            } else {
                LOGE("Failed to find method 'onCallback' in globalCallback");
            }
            env->DeleteLocalRef(callbackClass);
        } else {
            LOGE("Failed to find class of globalCallback");
        }
    }

    // Detach the thread if it was attached in this function
    if (attached) {
        javaVM->DetachCurrentThread();
    }
}

JNIEXPORT void JNICALL
Java_com_pawns_ndk_NativeLib_Initialize(JNIEnv *env, jobject obj, jstring rawDeviceID, jstring rawDeviceName) {
    const char *cRawDeviceID = env->GetStringUTFChars(rawDeviceID, 0);
    const char *cRawDeviceName = env->GetStringUTFChars(rawDeviceName, 0);

    Initialize((char *) cRawDeviceID, (char *) cRawDeviceName);

    env->ReleaseStringUTFChars(rawDeviceID, cRawDeviceID);
    env->ReleaseStringUTFChars(rawDeviceName, cRawDeviceName);
}

JNIEXPORT void JNICALL
Java_com_pawns_ndk_NativeLib_StartMainRoutine(JNIEnv* env, jobject obj, jstring rawAccessToken, jobject callback) {
    const char* nativeAccessToken = env->GetStringUTFChars(rawAccessToken, 0);

    {
        std::lock_guard<std::mutex> lock(callbackMutex);
        if (globalCallback != nullptr) {
            env->DeleteGlobalRef(globalCallback);
        }
        globalCallback = env->NewGlobalRef(callback);

        if (javaVM == nullptr) {
            env->GetJavaVM(&javaVM);
        }

        StartMainRoutine((char*)nativeAccessToken, (void*)myCallback);
    }

    env->ReleaseStringUTFChars(rawAccessToken, nativeAccessToken);
}

JNIEXPORT void JNICALL
Java_com_pawns_ndk_NativeLib_StopMainRoutine(JNIEnv *env, jobject obj) {
    StopMainRoutine();

    // Clean up the global callback reference
    {
        std::lock_guard<std::mutex> lock(callbackMutex);
        if (globalCallback != nullptr) {
            env->DeleteGlobalRef(globalCallback);
            globalCallback = nullptr;
        }
    }
}

}
