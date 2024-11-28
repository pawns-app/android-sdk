#include <jni.h>
#include <string>
#include <mutex>
#include <atomic>
#include <condition_variable>
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

static JavaVM *javaVM = nullptr;
static jobject globalCallback = nullptr;
static std::atomic<bool> isCallbackValid{false};
static int activeCallbackCount = 0;
static std::mutex callbackMutex;
static std::condition_variable callbackCondition;

extern "C" {
// Define the callback function that matches the expected type
#define LOG_TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void myCallback(char *message) {
    JNIEnv *env = nullptr;
    bool attached = false;
    jobject localCallback = nullptr;  // Local reference to globalCallback

    // Check if the callback is valid and increment activeCallbackCount
    {
        std::lock_guard<std::mutex> lock(callbackMutex);
        if (!isCallbackValid) {
            LOGI("Callback is no longer valid. Skipping callback invocation.");
            return;
        }
        activeCallbackCount++;
        localCallback = globalCallback;  // Assign the globalCallback to a local variable
    }

    // Attach the current thread to the JVM if needed
    if (javaVM->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (javaVM->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            LOGE("Failed to attach current thread to Java VM");
            // Decrement activeCallbackCount
            {
                std::lock_guard<std::mutex> lock(callbackMutex);
                activeCallbackCount--;
                callbackCondition.notify_all();
            }
            return;
        }
    }

    // Use a scoped block to manage control flow and cleanup
    do {
        jclass callbackClass = env->GetObjectClass(localCallback);
        if (callbackClass == nullptr) {
            LOGE("Failed to find class of globalCallback");
            break;
        }

        jmethodID onCallbackMethod = env->GetMethodID(callbackClass, "onCallback", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(callbackClass);  // Delete local reference immediately
        if (onCallbackMethod == nullptr) {
            LOGE("Failed to find method 'onCallback' in globalCallback");
            break;
        }

        jstring jMessage = env->NewStringUTF(message);
        if (jMessage == nullptr) {
            LOGE("Failed to create jstring from message");
            break;
        }

        env->CallVoidMethod(localCallback, onCallbackMethod, jMessage);
        env->DeleteLocalRef(jMessage);  // Delete local reference

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            LOGE("Exception occurred while calling Java callback.");
        }

    } while (false);

    // Detach the thread if it was attached
    if (attached) {
        javaVM->DetachCurrentThread();
    }

    // Decrement activeCallbackCount and notify waiting threads
    {
        std::lock_guard<std::mutex> lock(callbackMutex);
        activeCallbackCount--;
        callbackCondition.notify_all();
    }
}

JNIEXPORT void JNICALL
Java_com_pawns_ndk_PawnsCore_Initialize(JNIEnv *env, jobject obj, jstring rawDeviceID, jstring rawDeviceName) {
    const char *cRawDeviceID = env->GetStringUTFChars(rawDeviceID, 0);
    const char *cRawDeviceName = env->GetStringUTFChars(rawDeviceName, 0);

    Initialize((char *) cRawDeviceID, (char *) cRawDeviceName);

    env->ReleaseStringUTFChars(rawDeviceID, cRawDeviceID);
    env->ReleaseStringUTFChars(rawDeviceName, cRawDeviceName);
}

JNIEXPORT void JNICALL
Java_com_pawns_ndk_PawnsCore_StartMainRoutine(JNIEnv *env, jobject obj, jstring rawAccessToken, jobject callback) {
    const char *nativeAccessToken = env->GetStringUTFChars(rawAccessToken, 0);

    {
        std::lock_guard<std::mutex> lock(callbackMutex);
        if (globalCallback != nullptr) {
            env->DeleteGlobalRef(globalCallback);
        }
        globalCallback = env->NewGlobalRef(callback);

        if (javaVM == nullptr) {
            env->GetJavaVM(&javaVM);
        }

        isCallbackValid.store(true);  // Mark callback as valid
    }

    StartMainRoutine((char *) nativeAccessToken, (void *) myCallback);

    env->ReleaseStringUTFChars(rawAccessToken, nativeAccessToken);
}

JNIEXPORT void JNICALL
Java_com_pawns_ndk_PawnsCore_StopMainRoutine(JNIEnv *env, jobject obj) {
    StopMainRoutine();

    // Mark callback as invalid
    {
        std::lock_guard<std::mutex> lock(callbackMutex);
        isCallbackValid.store(false);
    }

    // Wait for all active callbacks to finish
    {
        std::unique_lock<std::mutex> lock(callbackMutex);
        callbackCondition.wait(lock, [] { return activeCallbackCount == 0; });

        // Now safe to delete globalCallback
        if (globalCallback != nullptr) {
            env->DeleteGlobalRef(globalCallback);
            globalCallback = nullptr;
        }
    }
}

}
