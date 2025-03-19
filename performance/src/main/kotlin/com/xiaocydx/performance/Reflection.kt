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

package com.xiaocydx.performance

import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * @author xcc
 * @date 2025/3/19
 */
internal interface Reflection {
    fun Class<*>.toSafe() = SafeClass(this)

    fun List<Field>.find(name: String): Field = first { it.name == name }

    fun List<Method>.find(name: String): Method = first { it.name == name }
}

@JvmInline
internal value class SafeClass(private val clazz: Class<*>) {

    val declaredStaticFields: List<Field>
        get() {
            val fields = runCatching {
                if (Build.VERSION.SDK_INT < 28) {
                    clazz.declaredFields.filter { Modifier.isStatic(it.modifiers) }
                } else {
                    HiddenApiBypass.getStaticFields(clazz)
                }
            }
            return fields.getOrNull() ?: emptyList()
        }

    val declaredInstanceFields: List<Field>
        get() {
            val fields = runCatching {
                if (Build.VERSION.SDK_INT < 28) {
                    clazz.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
                } else {
                    HiddenApiBypass.getInstanceFields(clazz)
                }
            }
            return fields.getOrNull() ?: emptyList()
        }

    val declaredMethods: List<Method>
        get() {
            val methods = runCatching {
                if (Build.VERSION.SDK_INT < 28) {
                    clazz.declaredMethods.toList()
                } else {
                    HiddenApiBypass.getDeclaredMethods(clazz).filterIsInstance<Method>()
                }
            }
            return methods.getOrNull() ?: emptyList()
        }
}