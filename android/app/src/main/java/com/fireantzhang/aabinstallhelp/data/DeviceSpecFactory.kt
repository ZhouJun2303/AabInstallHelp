package com.fireantzhang.aabinstallhelp.data

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES10
import android.os.Build
import android.util.DisplayMetrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

object DeviceSpecFactory {
    fun write(context: Context, dest: File): File {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val locales = linkedSetOf<String>()
        locales.add(Locale.getDefault().toLanguageTag())
        if (Build.VERSION.SDK_INT >= 24) {
            val list = context.resources.configuration.locales
            for (i in 0 until list.size()) {
                locales.add(list[i].toLanguageTag())
            }
        }

        val features = JSONArray()
        context.packageManager.systemAvailableFeatures?.forEach { info ->
            if (!info.name.isNullOrBlank()) {
                features.put(info.name)
            }
        }

        val abis = JSONArray()
        Build.SUPPORTED_ABIS?.forEach { abis.put(it) }

        val json = JSONObject()
            .put("supportedAbis", abis)
            .put("supportedLocales", JSONArray(locales.toList()))
            .put("screenDensity", metrics.densityDpi)
            .put("sdkVersion", Build.VERSION.SDK_INT)
            .put("deviceFeatures", features)
            .put("glExtensions", JSONArray(queryGlExtensions()))

        dest.parentFile?.mkdirs()
        dest.writeText(json.toString(2))
        return dest
    }

    private fun queryGlExtensions(): List<String> {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return emptyList()
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return emptyList()
            val attribList = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, num, 0)
            val config = configs[0] ?: return emptyList()
            val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
            val surfAttrib = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfAttrib, 0)
            EGL14.eglMakeCurrent(display, surface, surface, context)
            val raw = GLES10.glGetString(GLES10.GL_EXTENSIONS) ?: ""
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            raw.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
