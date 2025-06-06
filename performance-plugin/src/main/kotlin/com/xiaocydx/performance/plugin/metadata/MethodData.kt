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

package com.xiaocydx.performance.plugin.metadata

import com.xiaocydx.performance.plugin.metadata.Metadata.Companion.SEPARATOR

/**
 * @author xcc
 * @date 2025/4/15
 */
internal class MethodData(
    id: Int,
    access: Int,
    className: String,
    methodName: String,
    desc: String
) : Metadata {
    var id = id; private set
    var access = access; private set
    var className = className; private set
    var methodName = methodName; private set
    var desc = desc; private set

    override val key: String
        get() = key(className, methodName, desc)

    override fun toOutput(): String {
        return "${id}${SEPARATOR}${access}${SEPARATOR}" +
                "${className}${SEPARATOR}$methodName${SEPARATOR}${desc}"
    }

    companion object {

        fun key(className: String, methodName: String, desc: String): String {
            return "${className}${SEPARATOR}${methodName}${SEPARATOR}$desc"
        }

        fun fromOutput(output: String, destination: MethodData? = null): MethodData {
            val property = output.split(SEPARATOR)
            val id = property[0].toInt()
            val access = property[1].toInt()
            val className = property[2]
            val methodName = property[3]
            val desc = property[4]
            if (destination != null) {
                destination.id = id
                destination.access = access
                destination.className = className
                destination.methodName = methodName
                destination.desc = desc
                return destination
            }
            return MethodData(id, access, className, methodName, desc)
        }
    }
}