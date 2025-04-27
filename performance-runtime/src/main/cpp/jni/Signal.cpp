//
// Created by xiaoc on 2025/4/27.
//
#include "JNIInvocation.h"
#include <jni.h>
#include <unistd.h>
#include <dirent.h>
#include <fstream>
#include <string>
#include <android/log.h>

static struct SignalJNI {
    jclass Signal;
    jmethodID Signal_anr;
} jni;

static sigset_t old_sigSet;

static int getSignalCatcherThreadId() {
    char path[256];
    pid_t pid = getpid();
    snprintf(path, sizeof(path), "/proc/%d/task", pid);
    DIR *dir = opendir(path);
    if (!dir) {
        return -1;
    }

    struct dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type != DT_DIR
            || strcmp(entry->d_name, ".") == 0
            || strcmp(entry->d_name, "..") == 0) {
            continue;
        }

        char status_path[512];
        snprintf(status_path, sizeof(status_path), "/proc/%d/task/%s/status", pid, entry->d_name);

        FILE *fp = fopen(status_path, "r");
        if (!fp) {
            continue;
        }

        char line[256];
        bool isSignalCatcher = false;
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "Name:")) {
                char thread_name[128];
                sscanf(line, "Name:\t%s", thread_name);
                if (strcmp(thread_name, "Signal") == 0
                    || strcmp(thread_name, "Signal Catcher") == 0) {
                    isSignalCatcher = true;
                }
                break;
            }
        }
        fclose(fp);

        if (isSignalCatcher) {
            closedir(dir);
            return atoi(entry->d_name);
        }
    }

    closedir(dir);
    return -1;
}

static void sendSigToSignalCatcher() {
    int tid = getSignalCatcherThreadId();
    tgkill(getpid(), tid, SIGQUIT);
}

static void *anrCallback(void *arg) {
    JNIEnv *env = JNIInvocation::getEnv();
    env->CallStaticVoidMethod(jni.Signal, jni.Signal_anr);
//    sendSigToSignalCatcher();
    return nullptr;
}

static void *siUserCallback(void *arg) {
//    sendSigToSignalCatcher();
    return nullptr;
}

static void signalHandler(int sig, siginfo_t *info, void *uc) {
    int fromPid1 = info->_si_pad[3];
    int fromPid2 = info->_si_pad[4];
    int pid = getpid();
    bool fromMySelf = fromPid1 == pid || fromPid2 == pid;
    if (sig == SIGQUIT) {
        pthread_t thd;
        if (!fromMySelf) {
            pthread_create(&thd, nullptr, anrCallback, nullptr);
        } else {
            pthread_create(&thd, nullptr, siUserCallback, nullptr);
        }
        pthread_detach(thd);
    }
}

static void com_xiaocydx_performance_runtime_signal_Signal_register(JNIEnv *env, jclass clazz) {
    sigset_t sigSet;
    sigemptyset(&sigSet);
    sigaddset(&sigSet, SIGQUIT);
    pthread_sigmask(SIG_UNBLOCK, &sigSet, &old_sigSet);

    struct sigaction sa{};
    sa.sa_sigaction = signalHandler;
    sa.sa_flags = SA_ONSTACK | SA_SIGINFO | SA_RESTART;
    sigaction(SIGQUIT, &sa, nullptr);
}

static void com_xiaocydx_performance_runtime_signal_Signal_unregister(JNIEnv *env, jclass clazz) {
}

int register_com_xiaocydx_performance_runtime_Signal(JNIEnv *env) {
    auto clazz = env->FindClass("com/xiaocydx/performance/runtime/signal/Signal");
    if (clazz == nullptr) {
        return JNI_ERR;
    }
    const JNINativeMethod methods[] = {
            {"nativeRegister",   "()V",
                    (void *) com_xiaocydx_performance_runtime_signal_Signal_register},
            {"nativeUnregister", "()V",
                    (void *) com_xiaocydx_performance_runtime_signal_Signal_unregister},
    };

    jni.Signal = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    jni.Signal_anr = env->GetStaticMethodID(clazz, "anr", "()V");
    env->RegisterNatives(clazz, methods, NELEM(methods));
    return JNI_TRUE;
}