//
// Created by xiaoc on 2025/4/27.
//
#include "JNIInvocation.h"
#include <jni.h>

extern int register_com_xiaocydx_performance_runtime_Signal(JNIEnv *env);

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIInvocation::init(vm);
    JNIEnv *env;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        return JNI_ERR;
    }
    int (*const methods[])(JNIEnv *) = {
            register_com_xiaocydx_performance_runtime_Signal
    };
    for (const auto &method: methods) {
        if (!method(env)) return JNI_ERR;
    }
    return JNI_VERSION_1_4;
}