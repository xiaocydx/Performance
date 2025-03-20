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

package com.xiaocydx.performance.looper

import com.xiaocydx.performance.Performance
import com.xiaocydx.performance.log
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * @author xcc
 * @date 2025/3/19
 */
internal class MainLooperMonitor(private val host: Performance.Host) {
    private val scope = host.createMainScope()

    fun init(resetAfterGC: Boolean = true) {
        scope.launch {
            log { "设置${MainLooperIdleAnalyzer::class.java.simpleName}" }
            while (true) {
                val analyzer = MainLooperIdleAnalyzer.setup()
                if (resetAfterGC) analyzer.awaitGC() else break
                log { "重新设置${MainLooperIdleAnalyzer::class.java.simpleName}" }
            }
        }

        scope.launch {
            log { "设置${MainLooperMessageAnalyzer::class.java.simpleName}" }
            while (true) {
                val analyzer = MainLooperMessageAnalyzer.setup()
                if (resetAfterGC) analyzer.awaitGC() else break
                log { "重新设置${MainLooperMessageAnalyzer::class.java.simpleName}" }
            }
        }

        scope.launch {
            log { "设置${MainLooperIdleCheck::class.java.simpleName}" }
            MainLooperIdleCheck(host).repeatCheckOnActivityResumed()
        }
    }

    private suspend fun MainLooperIdleAnalyzer.awaitGC() {
        suspendCancellableCoroutine { cont -> trackGC { cont.resume(Unit) } }
    }

    private suspend fun MainLooperMessageAnalyzer.awaitGC() {
        suspendCancellableCoroutine { cont -> trackGC { cont.resume(Unit) } }
    }
}