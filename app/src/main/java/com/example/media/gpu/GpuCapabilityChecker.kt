package com.example.media.gpu

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.core.ports.GpuCapabilities

object GpuCapabilityChecker {

    fun inspect(
        context: Context,
        hardwareAccelerationRequested: Boolean = true,
    ): GpuCapabilities {
        val am = runCatching {
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        }.getOrNull()

        val reqGlEsVersion = runCatching {
            am?.deviceConfigurationInfo?.reqGlEsVersion ?: 0x00020000
        }.getOrDefault(0x00020000)

        val majorVersion = ((reqGlEsVersion and 0xFFFF0000.toInt()) ushr 16).coerceAtLeast(2)
        val supportsGlEs3 = majorVersion >= 3
        val isLowMemory = runCatching {
            am?.isLowRamDevice ?: false
        }.getOrDefault(false)

        val isRobolectric = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        val useHardwarePipeline = hardwareAccelerationRequested && !isRobolectric

        val label = when {
            !hardwareAccelerationRequested -> "Software Fallback (User Pref)"
            isRobolectric -> "Software Fallback (Test Env)"
            supportsGlEs3 && isLowMemory -> "GLES 3.0 (Low-RAM Mode)"
            supportsGlEs3 -> "GLES 3.0 GPU"
            else -> "GLES 2.0 Compat"
        }

        return GpuCapabilities(
            glEsMajorVersion = majorVersion,
            supportsGlEs3 = supportsGlEs3,
            isLowMemoryDevice = isLowMemory,
            useHardwareGpuPipeline = useHardwarePipeline,
            statusLabel = label,
        )
    }
}
