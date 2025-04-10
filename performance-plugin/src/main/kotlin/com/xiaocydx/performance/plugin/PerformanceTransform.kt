// /*
//  * Copyright 2025 xiaocydx
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *    http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */
//
// package com.xiaocydx.performance.plugin
//
// import com.android.build.api.transform.Format
// import com.android.build.api.transform.QualifiedContent
// import com.android.build.api.transform.Transform
// import com.android.build.api.transform.TransformInvocation
// import com.android.build.gradle.internal.pipeline.TransformManager
// import com.android.utils.FileUtils
// import org.objectweb.asm.ClassReader
// import org.objectweb.asm.ClassWriter
// import org.objectweb.asm.Opcodes
// import java.io.FileOutputStream
//
// /**
//  * @author xcc
//  * @date 2025/4/10
//  */
// class PerformanceTransform : Transform() {
//
//     override fun getName(): String {
//         return PerformanceTransform::class.java.simpleName
//     }
//
//     override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
//         return TransformManager.CONTENT_CLASS
//     }
//
//     override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
//         return TransformManager.SCOPE_FULL_PROJECT
//     }
//
//     override fun isIncremental(): Boolean {
//         return false
//     }
//
//     override fun transform(transformInvocation: TransformInvocation) {
//         val inputs = transformInvocation.inputs
//         val outputProvider = transformInvocation.outputProvider
//
//         inputs.forEach { input ->
//             input.directoryInputs.forEach { directoryInput ->
//                 FileUtils.getAllFiles(directoryInput.file).forEach { file ->
//                     val name = file.name
//                     if (name.endsWith("Activity.class")) {
//                         val classPath = file.absolutePath
//                         val classReader = ClassReader(file.readBytes())
//                         val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
//                         val classVisitor = PerformanceClassVisitor(Opcodes.ASM7, classWriter)
//                         classReader.accept(classVisitor, ClassReader.SKIP_FRAMES)
//
//                         val bytes = classWriter.toByteArray()
//                         val fos = FileOutputStream(classPath)
//                         fos.write(bytes)
//                         fos.close()
//                     }
//                 }
//
//                 val dest = outputProvider.getContentLocation(
//                     directoryInput.name,
//                     directoryInput.contentTypes,
//                     directoryInput.scopes,
//                     Format.DIRECTORY
//                 )
//                 FileUtils.copyDirectoryToDirectory(directoryInput.file, dest)
//             }
//
//             input.jarInputs.forEach {
//                 val dest = outputProvider.getContentLocation(
//                     it.name,
//                     it.contentTypes,
//                     it.scopes,
//                     Format.JAR
//                 )
//                 FileUtils.copyFile(it.file, dest)
//             }
//         }
//     }
// }