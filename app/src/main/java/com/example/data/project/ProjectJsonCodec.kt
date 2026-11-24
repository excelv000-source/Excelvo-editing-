package com.example.data.project

import com.example.core.model.AnimatableProperty
import com.example.core.model.AspectRatioPreset
import com.example.core.model.BlendModeType
import com.example.core.model.CanvasBackgroundSpec
import com.example.core.model.CanvasScaleMode
import com.example.core.model.ChromaKeySpec
import com.example.core.model.ClipEffectInstance
import com.example.core.model.ColorGradeSpec
import com.example.core.model.EasingType
import com.example.core.model.ExportResolutionPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.Keyframe
import com.example.core.model.MaskShape
import com.example.core.model.MaskSpec
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TimelineClip
import com.example.core.model.TimelineProject
import com.example.core.model.TimelineTrack
import com.example.core.model.TrackType
import com.example.core.model.TransformSpec
import com.example.core.model.VisualEffectType
import org.json.JSONArray
import org.json.JSONObject

object ProjectJsonCodec {

    fun encode(project: TimelineProject): String {
        val root = JSONObject().apply {
            put("schemaVersion", project.schemaVersion)
            put("id", project.id)
            put("name", project.name)
            put("aspectRatio", project.aspectRatio.id)
            put("frameRate", project.frameRate.fps)
            put("targetResolution", project.targetResolution.id)
            put("scaleMode", project.scaleMode.id)
            put("backgroundId", project.backgroundId)
            put("customWidthPx", project.customWidthPx ?: JSONObject.NULL)
            put("customHeightPx", project.customHeightPx ?: JSONObject.NULL)
            put("createdAtMs", project.createdAtMs)
            put("updatedAtMs", project.updatedAtMs)
            put("assets", JSONArray().apply {
                project.assets.values.forEach { put(encodeAsset(it)) }
            })
            put("tracks", JSONArray().apply {
                project.tracks.forEach { put(encodeTrack(it)) }
            })
        }
        return root.toString(2)
    }

    fun decode(json: String): TimelineProject {
        val root = JSONObject(json)
        val assetsArray = root.optJSONArray("assets") ?: JSONArray()
        val assetsMap = LinkedHashMap<String, MediaAsset>()
        for (i in 0 until assetsArray.length()) {
            val asset = decodeAsset(assetsArray.getJSONObject(i))
            assetsMap[asset.id] = asset
        }

        val tracksArray = root.optJSONArray("tracks") ?: JSONArray()
        val tracks = mutableListOf<TimelineTrack>()
        for (i in 0 until tracksArray.length()) {
            tracks.add(decodeTrack(tracksArray.getJSONObject(i)))
        }

        val customW = if (root.has("customWidthPx") && !root.isNull("customWidthPx")) root.optInt("customWidthPx") else null
        val customH = if (root.has("customHeightPx") && !root.isNull("customHeightPx")) root.optInt("customHeightPx") else null

        return TimelineProject(
            id = root.getString("id"),
            name = root.getString("name"),
            aspectRatio = AspectRatioPreset.fromId(root.optString("aspectRatio", "9:16")),
            frameRate = FrameRatePreset.fromFps(root.optInt("frameRate", 30)),
            targetResolution = ExportResolutionPreset.fromId(root.optString("targetResolution", "1080p")),
            scaleMode = CanvasScaleMode.fromId(root.optString("scaleMode", CanvasScaleMode.FIT.id)),
            backgroundId = root.optString("backgroundId", CanvasBackgroundSpec.PURE_BLACK.id),
            customWidthPx = customW?.takeIf { it >= 120 },
            customHeightPx = customH?.takeIf { it >= 120 },
            tracks = if (tracks.isEmpty()) TimelineProject.defaultTracks() else tracks,
            assets = assetsMap,
            createdAtMs = root.getLong("createdAtMs"),
            updatedAtMs = root.getLong("updatedAtMs"),
            schemaVersion = root.optInt("schemaVersion", TimelineProject.CURRENT_SCHEMA_VERSION),
        )
    }

    private fun encodeAsset(asset: MediaAsset): JSONObject = JSONObject().apply {
        put("id", asset.id)
        put("sourceUri", asset.sourceUri)
        put("displayName", asset.displayName)
        put("mediaType", asset.mediaType.name)
        put("durationUs", asset.durationUs)
        put("width", asset.width)
        put("height", asset.height)
        put("rotationDeg", asset.rotationDeg)
        put("fps", asset.fps.toDouble())
        put("hasAudio", asset.hasAudio)
        put("fileSizeBytes", asset.fileSizeBytes)
        put("thumbnailPath", asset.thumbnailPath ?: JSONObject.NULL)
        put("mimeType", asset.mimeType)
        put("sampleRateHz", asset.sampleRateHz)
        put("channelCount", asset.channelCount)
        put("audioCodec", asset.audioCodec)
        put("videoCodec", asset.videoCodec)
        put("filmstripPaths", JSONArray().apply {
            asset.filmstripPaths.forEach { put(it) }
        })
        put("waveformSamples", JSONArray().apply {
            asset.waveformSamples.forEach { put(it.toDouble()) }
        })
        put("cachedLocalPath", asset.cachedLocalPath ?: JSONObject.NULL)
        put("uriPersisted", asset.uriPersisted)
        put("isAccessible", asset.isAccessible)
    }

    private fun decodeAsset(obj: JSONObject): MediaAsset {
        val filmstripArr = obj.optJSONArray("filmstripPaths")
        val filmstrips = buildList {
            if (filmstripArr != null) {
                for (i in 0 until filmstripArr.length()) {
                    val path = filmstripArr.optString(i)
                    if (path.isNotBlank() && path != "null") add(path)
                }
            }
        }
        val waveArr = obj.optJSONArray("waveformSamples")
        val waveforms = buildList {
            if (waveArr != null) {
                for (i in 0 until waveArr.length()) {
                    add(waveArr.optDouble(i, 0.0).toFloat())
                }
            }
        }
        return MediaAsset(
            id = obj.getString("id"),
            sourceUri = obj.getString("sourceUri"),
            displayName = obj.getString("displayName"),
            mediaType = MediaType.valueOf(obj.getString("mediaType")),
            durationUs = obj.getLong("durationUs"),
            width = obj.getInt("width"),
            height = obj.getInt("height"),
            rotationDeg = obj.optInt("rotationDeg", 0),
            fps = obj.optDouble("fps", 30.0).toFloat(),
            hasAudio = obj.optBoolean("hasAudio", false),
            fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
            thumbnailPath = obj.optString("thumbnailPath").takeIf { it.isNotBlank() && it != "null" },
            mimeType = obj.optString("mimeType", ""),
            sampleRateHz = obj.optInt("sampleRateHz", 0),
            channelCount = obj.optInt("channelCount", 0),
            audioCodec = obj.optString("audioCodec", ""),
            videoCodec = obj.optString("videoCodec", ""),
            filmstripPaths = filmstrips,
            waveformSamples = waveforms,
            cachedLocalPath = obj.optString("cachedLocalPath").takeIf { it.isNotBlank() && it != "null" },
            uriPersisted = obj.optBoolean("uriPersisted", false),
            isAccessible = obj.optBoolean("isAccessible", true),
        )
    }

    private fun encodeTrack(track: TimelineTrack): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("type", track.type.name)
        put("name", track.name)
        put("isLocked", track.isLocked)
        put("isMuted", track.isMuted)
        put("isHidden", track.isHidden)
        put("clips", JSONArray().apply {
            track.clips.forEach { put(encodeClip(it)) }
        })
    }

    private fun decodeTrack(obj: JSONObject): TimelineTrack {
        val clipsArray = obj.optJSONArray("clips") ?: JSONArray()
        val clips = mutableListOf<TimelineClip>()
        for (i in 0 until clipsArray.length()) {
            clips.add(decodeClip(clipsArray.getJSONObject(i)))
        }
        return TimelineTrack(
            id = obj.getString("id"),
            type = TrackType.valueOf(obj.getString("type")),
            name = obj.getString("name"),
            isLocked = obj.optBoolean("isLocked", false),
            isMuted = obj.optBoolean("isMuted", false),
            isHidden = obj.optBoolean("isHidden", false),
            clips = clips,
        )
    }

    private fun encodeClip(clip: TimelineClip): JSONObject = JSONObject().apply {
        put("id", clip.id)
        put("assetId", clip.assetId)
        put("trackId", clip.trackId)
        put("mediaType", clip.mediaType.name)
        put("label", clip.label)
        put("timelineStartUs", clip.timelineStartUs)
        put("timelineDurationUs", clip.timelineDurationUs)
        put("sourceStartUs", clip.sourceStartUs)
        put("sourceEndUs", clip.sourceEndUs)
        put("sourceTotalDurationUs", clip.sourceTotalDurationUs)
        put("speedMultiplier", clip.speedMultiplier.toDouble())
        put("volume", clip.volume.toDouble())
        put("isMuted", clip.isMuted)
        put("fadeInUs", clip.fadeInUs)
        put("fadeOutUs", clip.fadeOutUs)
        put("blendMode", clip.blendMode.id)
        put("thumbnailPath", clip.thumbnailPath ?: JSONObject.NULL)
        put("groupId", clip.groupId ?: JSONObject.NULL)
        put("transform", JSONObject().apply {
            put("positionX", clip.transform.positionX.toDouble())
            put("positionY", clip.transform.positionY.toDouble())
            put("positionZ", clip.transform.positionZ.toDouble())
            put("scaleX", clip.transform.scaleX.toDouble())
            put("scaleY", clip.transform.scaleY.toDouble())
            put("rotationDeg", clip.transform.rotationDeg.toDouble())
            put("skewXDeg", clip.transform.skewXDeg.toDouble())
            put("skewYDeg", clip.transform.skewYDeg.toDouble())
            put("opacity", clip.transform.opacity.toDouble())
            put("anchorX", clip.transform.anchorX.toDouble())
            put("anchorY", clip.transform.anchorY.toDouble())
            put("flipH", clip.transform.flipH)
            put("flipV", clip.transform.flipV)
            put("cropLeft", clip.transform.cropLeft.toDouble())
            put("cropTop", clip.transform.cropTop.toDouble())
            put("cropRight", clip.transform.cropRight.toDouble())
            put("cropBottom", clip.transform.cropBottom.toDouble())
        })
        put("colorGrade", JSONObject().apply {
            put("exposure", clip.colorGrade.exposure.toDouble())
            put("brightness", clip.colorGrade.brightness.toDouble())
            put("contrast", clip.colorGrade.contrast.toDouble())
            put("saturation", clip.colorGrade.saturation.toDouble())
            put("temperature", clip.colorGrade.temperature.toDouble())
            put("tint", clip.colorGrade.tint.toDouble())
            put("highlights", clip.colorGrade.highlights.toDouble())
            put("shadows", clip.colorGrade.shadows.toDouble())
            put("sharpness", clip.colorGrade.sharpness.toDouble())
            put("blurRadius", clip.colorGrade.blurRadius.toDouble())
            put("glow", clip.colorGrade.glow.toDouble())
            put("vignette", clip.colorGrade.vignette.toDouble())
            put("grayscale", clip.colorGrade.grayscale.toDouble())
            put("sepia", clip.colorGrade.sepia.toDouble())
            put("filterPresetId", clip.colorGrade.filterPresetId ?: JSONObject.NULL)
            put("filterIntensity", clip.colorGrade.filterIntensity.toDouble())
            put("lutId", clip.colorGrade.lutId ?: JSONObject.NULL)
        })
        put("effectStack", JSONArray().apply {
            clip.effectStack.forEach { fx ->
                put(JSONObject().apply {
                    put("id", fx.id)
                    put("type", fx.type.name)
                    put("enabled", fx.enabled)
                    put("intensity", fx.intensity.toDouble())
                })
            }
        })
        put("chromaKey", JSONObject().apply {
            put("enabled", clip.chromaKey.enabled)
            put("keyColorRgb", clip.chromaKey.keyColorRgb)
            put("similarity", clip.chromaKey.similarity.toDouble())
            put("smoothness", clip.chromaKey.smoothness.toDouble())
            put("spillSuppression", clip.chromaKey.spillSuppression.toDouble())
        })
        put("mask", JSONObject().apply {
            put("shape", clip.mask.shape.name)
            put("feather", clip.mask.feather.toDouble())
            put("invert", clip.mask.invert)
        })
        put("keyframes", JSONObject().apply {
            clip.keyframes.forEach { (prop, list) ->
                put(prop.name, JSONArray().apply {
                    list.forEach { kf ->
                        put(JSONObject().apply {
                            put("id", kf.id)
                            put("timeOffsetUs", kf.timeOffsetUs)
                            put("value", kf.value.toDouble())
                            put("easing", kf.easing.name)
                        })
                    }
                })
            }
        })
    }

    private fun decodeClip(obj: JSONObject): TimelineClip {
        val tObj = obj.optJSONObject("transform")
        val cObj = obj.optJSONObject("colorGrade")
        val kObj = obj.optJSONObject("chromaKey")
        val mObj = obj.optJSONObject("mask")
        val kfObj = obj.optJSONObject("keyframes")
        val fxArr = obj.optJSONArray("effectStack")

        val effectStack = buildList {
            if (fxArr != null) {
                for (i in 0 until fxArr.length()) {
                    val fxObj = fxArr.optJSONObject(i) ?: continue
                    val type = runCatching { VisualEffectType.valueOf(fxObj.getString("type")) }.getOrNull() ?: continue
                    add(
                        ClipEffectInstance(
                            id = fxObj.optString("id", "fx_$i"),
                            type = type,
                            enabled = fxObj.optBoolean("enabled", true),
                            intensity = fxObj.optDouble("intensity", type.defaultIntensity.toDouble()).toFloat(),
                        )
                    )
                }
            }
        }

        val keyframes = mutableMapOf<AnimatableProperty, List<Keyframe>>()
        if (kfObj != null) {
            val keys = kfObj.keys()
            while (keys.hasNext()) {
                val propKey = keys.next()
                val prop = runCatching { AnimatableProperty.valueOf(propKey) }.getOrNull() ?: continue
                val arr = kfObj.getJSONArray(propKey)
                val list = mutableListOf<Keyframe>()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(
                        Keyframe(
                            id = item.getString("id"),
                            timeOffsetUs = item.getLong("timeOffsetUs"),
                            value = item.getDouble("value").toFloat(),
                            easing = runCatching { EasingType.valueOf(item.optString("easing", "LINEAR")) }.getOrDefault(EasingType.LINEAR),
                        )
                    )
                }
                keyframes[prop] = list
            }
        }

        return TimelineClip(
            id = obj.getString("id"),
            assetId = obj.getString("assetId"),
            trackId = obj.getString("trackId"),
            mediaType = MediaType.valueOf(obj.getString("mediaType")),
            label = obj.getString("label"),
            timelineStartUs = obj.getLong("timelineStartUs"),
            timelineDurationUs = obj.getLong("timelineDurationUs"),
            sourceStartUs = obj.getLong("sourceStartUs"),
            sourceEndUs = obj.getLong("sourceEndUs"),
            sourceTotalDurationUs = obj.getLong("sourceTotalDurationUs"),
            speedMultiplier = obj.optDouble("speedMultiplier", 1.0).toFloat(),
            volume = obj.optDouble("volume", 1.0).toFloat(),
            isMuted = obj.optBoolean("isMuted", false),
            fadeInUs = obj.optLong("fadeInUs", 0L),
            fadeOutUs = obj.optLong("fadeOutUs", 0L),
            blendMode = BlendModeType.fromId(obj.optString("blendMode", BlendModeType.NORMAL.id)),
            transform = if (tObj != null) {
                TransformSpec(
                    positionX = tObj.optDouble("positionX", 0.0).toFloat(),
                    positionY = tObj.optDouble("positionY", 0.0).toFloat(),
                    positionZ = tObj.optDouble("positionZ", 0.0).toFloat(),
                    scaleX = tObj.optDouble("scaleX", 1.0).toFloat(),
                    scaleY = tObj.optDouble("scaleY", 1.0).toFloat(),
                    rotationDeg = tObj.optDouble("rotationDeg", 0.0).toFloat(),
                    skewXDeg = tObj.optDouble("skewXDeg", 0.0).toFloat(),
                    skewYDeg = tObj.optDouble("skewYDeg", 0.0).toFloat(),
                    opacity = tObj.optDouble("opacity", 1.0).toFloat(),
                    anchorX = tObj.optDouble("anchorX", 0.5).toFloat(),
                    anchorY = tObj.optDouble("anchorY", 0.5).toFloat(),
                    flipH = tObj.optBoolean("flipH", false),
                    flipV = tObj.optBoolean("flipV", false),
                    cropLeft = tObj.optDouble("cropLeft", 0.0).toFloat(),
                    cropTop = tObj.optDouble("cropTop", 0.0).toFloat(),
                    cropRight = tObj.optDouble("cropRight", 0.0).toFloat(),
                    cropBottom = tObj.optDouble("cropBottom", 0.0).toFloat(),
                )
            } else TransformSpec(),
            colorGrade = if (cObj != null) {
                ColorGradeSpec(
                    exposure = cObj.optDouble("exposure", 0.0).toFloat(),
                    brightness = cObj.optDouble("brightness", 0.0).toFloat(),
                    contrast = cObj.optDouble("contrast", 0.0).toFloat(),
                    saturation = cObj.optDouble("saturation", 0.0).toFloat(),
                    temperature = cObj.optDouble("temperature", 0.0).toFloat(),
                    tint = cObj.optDouble("tint", 0.0).toFloat(),
                    highlights = cObj.optDouble("highlights", 0.0).toFloat(),
                    shadows = cObj.optDouble("shadows", 0.0).toFloat(),
                    sharpness = cObj.optDouble("sharpness", 0.0).toFloat(),
                    blurRadius = cObj.optDouble("blurRadius", 0.0).toFloat(),
                    glow = cObj.optDouble("glow", 0.0).toFloat(),
                    vignette = cObj.optDouble("vignette", 0.0).toFloat(),
                    grayscale = cObj.optDouble("grayscale", 0.0).toFloat(),
                    sepia = cObj.optDouble("sepia", 0.0).toFloat(),
                    filterPresetId = cObj.optString("filterPresetId").takeIf { it.isNotBlank() && it != "null" },
                    filterIntensity = cObj.optDouble("filterIntensity", 1.0).toFloat(),
                    lutId = cObj.optString("lutId").takeIf { it.isNotBlank() && it != "null" },
                )
            } else ColorGradeSpec(),
            effectStack = effectStack,
            chromaKey = if (kObj != null) {
                ChromaKeySpec(
                    enabled = kObj.optBoolean("enabled", false),
                    keyColorRgb = kObj.optInt("keyColorRgb", 0x00FF00),
                    similarity = kObj.optDouble("similarity", 0.25).toFloat(),
                    smoothness = kObj.optDouble("smoothness", 0.1).toFloat(),
                    spillSuppression = kObj.optDouble("spillSuppression", 0.2).toFloat(),
                )
            } else ChromaKeySpec(),
            mask = if (mObj != null) {
                MaskSpec(
                    shape = MaskShape.valueOf(mObj.optString("shape", "NONE")),
                    feather = mObj.optDouble("feather", 0.05).toFloat(),
                    invert = mObj.optBoolean("invert", false),
                )
            } else MaskSpec(),
            keyframes = keyframes,
            thumbnailPath = obj.optString("thumbnailPath").takeIf { it.isNotBlank() && it != "null" },
            groupId = obj.optString("groupId").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}
