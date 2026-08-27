package com.fireantzhang.aabinstallhelp.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGuardTest {
    @Test
    fun isOom_detectsOutOfMemoryError() {
        assertTrue(MemoryGuard.isOom(OutOfMemoryError("Failed to allocate")))
    }

    @Test
    fun isOom_walksCauseChain() {
        val wrapped = IllegalStateException(
            "build apks failed",
            OutOfMemoryError("Failed to allocate a 134217744 byte allocation with 25165824 free bytes and 91MB until OOM")
        )
        assertTrue(MemoryGuard.isOom(wrapped))
    }

    @Test
    fun isOom_detectsMessageWithoutErrorType() {
        val wrapped = RuntimeException(
            "Failed to allocate a 134217744 byte allocation with 25165824 free bytes and 91MB until OOM"
        )
        assertTrue(MemoryGuard.isOom(wrapped))
    }

    @Test
    fun isOom_ignoresUnrelatedFailures() {
        assertFalse(MemoryGuard.isOom(IllegalStateException("未生成 apks 文件")))
    }
}
