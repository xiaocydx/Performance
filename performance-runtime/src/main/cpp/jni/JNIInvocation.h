//
// Created by xiaoc on 2025/4/27.
//
#ifndef PERFORMANCE_JNIINVOCATION_H
#define PERFORMANCE_JNIINVOCATION_H

#include <jni.h>

namespace JNIInvocation {
    void init(JavaVM *vm);

    JavaVM *getJavaVM();

    JNIEnv *getEnv();
}

#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))

#endif //PERFORMANCE_JNIINVOCATION_H