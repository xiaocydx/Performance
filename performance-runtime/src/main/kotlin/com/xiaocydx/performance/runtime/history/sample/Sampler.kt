/*
 * Copyright 2025 xiaocydx
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xiaocydx.performance.runtime.history.sample

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.xiaocydx.performance.runtime.Logger

/**
 * @author xcc
 * @date 2025/4/25
 */
internal class Sampler(
    looper: Looper,
    private val capacity: Int,
    private val intervalMillis: Long
) {
    private val handler = Handler(looper)
    private val sampleTask = SampleTask()
    private val logger = Logger(javaClass)
    private val mainThread = Looper.getMainLooper().thread
    private val sampleDeque = ArrayDeque<Sample>(capacity)

    @Volatile private var sampleUptimeMillis = 0L
    @Volatile private var stopUptimeMillis = 0L

    @MainThread
    fun start(uptimeMillis: Long) {
        resetSampleTime(uptimeMillis, fromTask = false)
        stopUptimeMillis = Long.MAX_VALUE
        sampleTask.startIfNecessary()
    }

    @MainThread
    fun stop(uptimeMillis: Long) {
        stopUptimeMillis = uptimeMillis
    }

    @AnyThread
    private fun resetSampleTime(uptimeMillis: Long, fromTask: Boolean) {
        val nextSampleTime = uptimeMillis + intervalMillis
        sampleUptimeMillis = nextSampleTime.coerceAtLeast(sampleUptimeMillis)
        logger.debug { "reset fromTask = $fromTask" }
    }

    @AnyThread
    fun sampleList(startUptimeMillis: Long, endUptimeMillis: Long): List<Sample> {
        if (startUptimeMillis > endUptimeMillis) return emptyList()
        val outcome = mutableListOf<Sample>()
        synchronized(sampleDeque) {
            sampleDeque.forEach {
                if (it.uptimeMillis in startUptimeMillis..endUptimeMillis) outcome.add(it)
            }
        }
        return outcome
    }

    private fun addSample(sample: Sample) {
        synchronized(sampleDeque) {
            if (sampleDeque.size == capacity) sampleDeque.removeFirst()
            sampleDeque.add(sample)
        }
    }

    private inner class SampleTask : Runnable {
        private var pid = 0
        private var lastSysStat: ProcSysStat? = null
        private var lastPidStat: ProcPidStat? = null
        @Volatile private var isStarted = false

        @MainThread
        fun startIfNecessary() {
            if (isStarted) return
            isStarted = true
            logger.debug { "start task" }
            handler.postDelayed(this, intervalMillis)
        }

        @WorkerThread
        override fun run() {
            var currentTime = SystemClock.uptimeMillis()
            if (delay(currentTime)) return
            if (stop(currentTime)) return

            logger.debug { "begin" }
            sample()
            logger.debug { "end" }

            currentTime = SystemClock.uptimeMillis()
            if (delay(currentTime)) return
            if (stop(currentTime)) return

            resetSampleTime(currentTime, fromTask = true)
            handler.postDelayed(this, intervalMillis)
        }

        private fun delay(currentTime: Long): Boolean {
            val sampleTime = sampleUptimeMillis
            if (currentTime < sampleUptimeMillis) {
                val delayMillis = sampleTime - currentTime
                handler.postDelayed(this, delayMillis)
                logger.debug { "delay ${delayMillis}ms" }
                return true
            }
            return false
        }

        private fun stop(currentTime: Long): Boolean {
            val isStop = currentTime >= stopUptimeMillis
            if (isStop) {
                isStarted = false
                logger.debug { "stop task" }
            }
            return isStop
        }

        private fun sample() {
            val uptimeMillis = SystemClock.uptimeMillis()
            val threadState = mainThread.state
            val stackTrace = mainThread.stackTrace.toList()

            if (pid == 0) pid = Process.myPid()
            val sysStat = ProcSysStat.read()
            val pidStat = ProcPidStat.read(pid)
            val lastSysStat = lastSysStat
            val lastPidStat = lastPidStat

            var cpuStat: CPUStat? = null
            if (sysStat.isAvailable
                    && pidStat.isAvailable
                    && lastSysStat?.isAvailable == true
                    && lastPidStat?.isAvailable == true) {
                val total = sysStat.total - lastSysStat.total
                cpuStat = CPUStat(
                    cpu = "${(total - (sysStat.idle - lastSysStat.idle)) * 100f / total}%",
                    user = "${(sysStat.user - lastSysStat.user) * 100f / total}%",
                    system = "${(sysStat.system - lastSysStat.system) * 100f / total}%",
                    idle = "${(sysStat.idle - lastSysStat.idle) * 100f / total}%",
                    iowait = "${(sysStat.iowait - lastSysStat.iowait) * 100f / total}%",
                    app = "${(pidStat.total - lastPidStat.total) * 100f / total}%"
                )
            }

            val threadStat = ThreadStat(
                priority = pidStat.takeIf { it.isAvailable }?.priority?.toString() ?: "",
                nice = pidStat.takeIf { it.isAvailable }?.nice?.toString() ?: "",
                state = threadState.toString(),
                stack = stackTrace.map { it.toString() },
                trace = stackTrace
            )

            this.lastSysStat = sysStat
            this.lastPidStat = pidStat
            addSample(Sample(uptimeMillis, intervalMillis, cpuStat, threadStat))
        }
    }
}