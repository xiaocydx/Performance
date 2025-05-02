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

package com.xiaocydx.performance.runtime.history.segment

import com.google.common.truth.Truth.assertThat
import com.xiaocydx.performance.runtime.looper.Scene
import org.junit.Test

/**
 * [Merger]的单元测试
 *
 * @author xcc
 * @date 2025/5/2
 */
internal class MergerTest {

    @Test
    fun segmentCopyFrom() {
        val source = Segment.copySource()
        val segment = Segment()
        segment.copyFrom(source)
        assertThat(source).isEqualTo(segment)
        assertThat(source.hashCode()).isEqualTo(segment.hashCode())
    }

    @Test
    fun emptyNotMerge() {
        val merger = merger()
        val segment = Segment()
        assertThat(merger.merge(segment)).isFalse()
    }

    @Test
    fun singleNotMerge() {
        val merger = merger()
        val segment = Segment()
        val single = Segment().apply { isSingle = true }
        // single.isSingle = true
        assertThat(merger.merge(single)).isFalse()
        // last.isSingle = true
        merger.append(single)
        assertThat(merger.merge(segment)).isFalse()
    }

    @Test
    fun differentSceneNotMerge() {
        val merger = merger()
        val messageSegment = Segment().apply { scene = Scene.Message }
        val idleSegment = Segment().apply { scene = Scene.IdleHandler }
        merger.append(messageSegment)
        assertThat(merger.merge(idleSegment)).isFalse()
    }

    @Test
    fun greaterThanIdleThresholdNotMerge() {
        val merger = merger()
        val segment1 = Segment()
        val segment2 = Segment()

        segment1.startUptimeMillis = 1
        segment1.endUptimeMillis = segment1.startUptimeMillis + 1

        segment2.startUptimeMillis = segment1.endUptimeMillis + IDLE_THRESHOLD_MILLIS + 1
        segment2.endUptimeMillis = segment2.startUptimeMillis + 1

        merger.append(segment1)
        assertThat(merger.merge(segment2)).isFalse()
    }

    @Test
    fun greaterThanMergeThresholdNotMerge() {
        val merger = merger()
        val segment1 = Segment()
        val segment2 = Segment()

        segment1.startUptimeMillis = 1
        segment1.endUptimeMillis = segment1.startUptimeMillis + 1

        segment2.startUptimeMillis = segment1.endUptimeMillis
        segment2.endUptimeMillis = segment2.startUptimeMillis + MERGE_THRESHOLD_MILLIS

        merger.append(segment1)
        assertThat(merger.merge(segment2)).isFalse()
    }

    @Test
    fun merge() {
        val merger = merger()
        val segment1 = Segment()
        val segment2 = Segment()
        val idleDuration = 1

        segment1.startUptimeMillis = 1
        segment1.endUptimeMillis = segment1.startUptimeMillis + 1
        segment1.startThreadTimeMillis = 1
        segment1.endThreadTimeMillis = segment1.startThreadTimeMillis + 1

        segment2.startUptimeMillis = segment1.endUptimeMillis + idleDuration
        segment2.endUptimeMillis = segment2.startUptimeMillis + 1
        segment2.startThreadTimeMillis = segment1.endThreadTimeMillis + idleDuration
        segment2.endThreadTimeMillis = segment2.startThreadTimeMillis + 1

        merger.append(segment1)
        merger.merge(segment2)

        val last = requireNotNull(merger.lastOrNull())
        assertThat(last.count).isEqualTo(2)
        assertThat(last.startUptimeMillis).isEqualTo(segment1.startUptimeMillis)
        assertThat(last.startThreadTimeMillis).isEqualTo(segment1.startThreadTimeMillis)
        assertThat(last.idleDurationMillis).isEqualTo(idleDuration)
        assertThat(last.endUptimeMillis).isEqualTo(segment2.endUptimeMillis)
        assertThat(last.endThreadTimeMillis).isEqualTo(segment2.endThreadTimeMillis)
    }

    @Test
    fun copy() {
        val merger = merger()
        val segment1 = Segment()
        val segment2 = Segment()
        val idleDuration = 1

        segment1.startUptimeMillis = 1
        segment1.endUptimeMillis = segment1.startUptimeMillis + 1

        segment2.startUptimeMillis = segment1.endUptimeMillis + idleDuration
        segment2.endUptimeMillis = segment2.startUptimeMillis + 1

        merger.append(segment1)
        val element1 = requireNotNull(merger.lastOrNull())
        merger.append(segment2)
        val element2 = requireNotNull(merger.lastOrNull())

        val copy = merger.copy()
        assertThat(copy.first()).isNotSameInstanceAs(element1)
        assertThat(copy.first().last).isNotSameInstanceAs(element1.last)
        assertThat(copy.first()).isEqualTo(element1)
        assertThat(copy.last()).isNotSameInstanceAs(element2)
        assertThat(copy.last().last).isNotSameInstanceAs(element2.last)
        assertThat(copy.last()).isEqualTo(element2)
    }

    @Test
    fun copyForRange() {
        val merger = merger()
        val segment1 = Segment()
        val segment2 = Segment()
        val segment3 = Segment()
        val idleDuration = 1

        segment1.startUptimeMillis = 1
        segment1.endUptimeMillis = segment1.startUptimeMillis + 2

        segment2.startUptimeMillis = segment1.endUptimeMillis + idleDuration
        segment2.endUptimeMillis = segment2.startUptimeMillis + 2

        segment3.startUptimeMillis = segment2.endUptimeMillis + idleDuration
        segment3.endUptimeMillis = segment3.startUptimeMillis + 2

        merger.append(segment1)
        val element1 = requireNotNull(merger.lastOrNull())
        merger.append(segment2)
        val element2 = requireNotNull(merger.lastOrNull())
        merger.append(segment3)
        val element3 = requireNotNull(merger.lastOrNull())

        val copy = merger.copy(segment1.startUptimeMillis + 1, segment2.endUptimeMillis - 1)
        assertThat(copy.contains(element1)).isTrue()
        assertThat(copy.contains(element2)).isTrue()
        assertThat(copy.contains(element3)).isFalse()
    }

    private fun merger() = Merger(
        capacity = 100,
        idleThresholdMillis = IDLE_THRESHOLD_MILLIS,
        mergeThresholdMillis = MERGE_THRESHOLD_MILLIS
    )

    private companion object {
        const val IDLE_THRESHOLD_MILLIS = 16L
        const val MERGE_THRESHOLD_MILLIS = 300L
    }
}