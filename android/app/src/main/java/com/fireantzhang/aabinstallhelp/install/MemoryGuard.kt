package com.fireantzhang.aabinstallhelp.install

object MemoryGuard {
    const val OOM_MESSAGE =
        "该 aab 体积过大，当前设备内存不足以在本机生成 apks。请改用电脑版安装，或换内存更大的设备/真机。"

    fun heapSummary(aabBytes: Long? = null): String {
        val rt = Runtime.getRuntime()
        val extra = if (aabBytes != null) "，aab=${mb(aabBytes)}" else ""
        return "堆内存：max=${mb(rt.maxMemory())} total=${mb(rt.totalMemory())} free=${mb(rt.freeMemory())}$extra"
    }

    fun isOom(t: Throwable): Boolean {
        var current: Throwable? = t
        val seen = HashSet<Throwable>()
        while (current != null && seen.add(current)) {
            if (current is OutOfMemoryError) return true
            val msg = current.message.orEmpty()
            if (msg.contains("OutOfMemoryError", ignoreCase = true)) return true
            if (msg.contains("until OOM", ignoreCase = true)) return true
            current = current.cause
        }
        return false
    }

    private fun mb(bytes: Long): String = "${bytes / (1024L * 1024L)}MB"
}
