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

package com.xiaocydx.performance.convert

import java.io.File

internal fun main() {
    val dirs = Dirs()
    val mapping = readMapping(dirs)
    val records = readRecords(dirs)
    records.forEach { writeJson(dirs, mapping, it) }
}

private fun readMapping(dirs: Dirs): Map<Int, MethodData> {
    var listFiles = dirs.mappingDir.listFiles() ?: emptyArray()
    listFiles = listFiles.filter { it.isFile }.toTypedArray()
    require(listFiles.size == 1)
    val mappingFile = listFiles.first()
    return mappingFile.bufferedReader().useLines { lines ->
        lines.map { MethodData.fromOutput(it) }.associateBy { it.id }
    }
}

private fun readRecords(dirs: Dirs): Map<String, List<Record>> {
    var listFiles = dirs.snapshotDir.listFiles() ?: emptyArray()
    listFiles = listFiles.filter { it.isFile }.toTypedArray()
    return listFiles.associate { file ->
        val record = file.bufferedReader().useLines { lines ->
            lines.map { Record(it.toLong()) }.toList()
        }
        file.name to record
    }
}

private fun writeJson(
    dirs: Dirs,
    mapping: Map<Int, MethodData>,
    entry: Map.Entry<String, List<Record>>,
) {
    val jsonFile = File(dirs.snapshotJsonDir, "${entry.key}.json")
    val json = TracingJson.toJson(mapping, entry.value)
    jsonFile.bufferedWriter().use { writer -> writer.write(json) }
}