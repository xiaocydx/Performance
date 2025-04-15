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

@file:Suppress("PackageDirectoryMismatch")

package com.xiaocydx.performance.convert.test

import com.xiaocydx.performance.convert.Dirs
import com.xiaocydx.performance.convert.MethodData
import com.xiaocydx.performance.convert.Record
import com.xiaocydx.performance.convert.TracingJson
import java.io.File

internal fun main() {
    val dirs = Dirs()
    val mapping = readMapping()
    val records = readRecords()
    writeJson(dirs, mapping, records)
}

private fun readMapping(): Map<Int, MethodData> {
    val mapping = listOf(
        "26013,17,com/xiaocydx/sample/PerformanceTest,run,()V",
        "26014,18,com/xiaocydx/sample/PerformanceTest,A,()V",
        "26015,18,com/xiaocydx/sample/PerformanceTest,B,()V",
        "26016,18,com/xiaocydx/sample/PerformanceTest,C,()V",
    )
    return mapping.map { MethodData.fromOutput(it) }.associateBy { it.id }
}

private fun readRecords(): List<Record> {
    val snapshot = longArrayOf(
        -17592165867454,
        -8994559269047902142,
        -8994550472954879934,
        -8994541676861857726,
        228830359992918383,
        -8994532880768835217,
        228839156085940691,
        228821563899896275,
        228812767806874067,
        9223354444688908755,
    )
    return snapshot.map { Record(it) }
}

private fun writeJson(
    dirs: Dirs,
    mapping: Map<Int, MethodData>,
    records: List<Record>,
) {
    val jsonFile = File(dirs.snapshotJsonDir, "snapshot-test.json")
    val json = TracingJson.toJson(mapping, records)
    jsonFile.bufferedWriter().use { writer -> writer.write(json) }
}