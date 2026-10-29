package eu.example.constructionlapse

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size

/**
 * Sensor-reported third-party JPEG output sizes per exposed Camera2 ID.
 * This does not promise Samsung's privileged camera-app binning modes.
 */
data class PhotoResolution(val width: Int, val height: Int, val highResolution: Boolean = false) {
    val megapixels: Double get() = width.toDouble() * height.toDouble() / 1_000_000.0
    val caption: String get() = "%d × %d  •  %.1f MP%s".format(
        java.util.Locale.US, width, height, megapixels,
        if (highResolution) "  •  high-res API" else "")
}

object PhotoResolutionCatalog {
    fun query(manager: CameraManager, lensId: String): List<PhotoResolution> = runCatching {
        val characteristics = manager.getCameraCharacteristics(lensId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return emptyList()
        val standard = map.getOutputSizes(ImageFormat.JPEG).orEmpty()
            .map { PhotoResolution(it.width, it.height) }
        val high = map.getHighResolutionOutputSizes(ImageFormat.JPEG).orEmpty()
            .map { PhotoResolution(it.width, it.height, true) }
        (standard + high)
            .groupBy { it.width to it.height }
            .map { (_, entries) -> entries.firstOrNull { it.highResolution } ?: entries.first() }
            .sortedWith(compareByDescending<PhotoResolution> { it.width.toLong() * it.height }
                .thenByDescending { it.width })
    }.getOrDefault(emptyList())

    /** ALL is available in a dialog; this subset is just for the quick capture presets. */
    fun presets(all: List<PhotoResolution>): List<PhotoResolution> {
        if (all.isEmpty()) return emptyList()
        val max = all.first()
        val targets = listOf(3840L*2160, 2560L*1440, 1920L*1080, 1280L*720)
        val selected = linkedMapOf<Pair<Int,Int>, PhotoResolution>()
        selected[max.width to max.height] = max
        for (target in targets) {
            val candidate = all.minByOrNull {
                kotlin.math.abs(it.width.toLong()*it.height-target)
            } ?: continue
            selected[candidate.width to candidate.height] = candidate
        }
        return selected.values.sortedByDescending { it.width.toLong()*it.height }
    }
}
