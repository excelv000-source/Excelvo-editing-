package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.command.AddAssetClipCommand
import com.example.core.model.AspectRatioPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TimelineProject
import com.example.core.model.TrackType
import com.example.core.ports.ImportFailureReason
import com.example.core.ports.MediaImportException
import com.example.data.db.ExcevoDatabase
import com.example.data.media.MediaLibraryRepository
import com.example.data.project.LocalProjectRepository
import com.example.data.project.ProjectJsonCodec
import com.example.media.playback.AndroidSequencePlaybackController
import com.example.media.probe.AndroidMediaProbeAdapter
import com.example.media.probe.ImageImportEngine
import com.example.media.probe.MediaUriHelper
import com.example.ui.editor.StudioViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaImportPipelineTest {

    private lateinit var context: Context
    private lateinit var db: ExcevoDatabase
    private lateinit var projectRepository: LocalProjectRepository
    private lateinit var mediaLibraryRepository: MediaLibraryRepository
    private lateinit var probeAdapter: AndroidMediaProbeAdapter
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ExcevoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        projectRepository = LocalProjectRepository(context, db.projectDao())
        mediaLibraryRepository = MediaLibraryRepository(context, db.recentMediaDao())
        probeAdapter = AndroidMediaProbeAdapter(context)
        tempDir = File(context.cacheDir, "media_pipeline_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        File(context.filesDir, "excevo_projects").deleteRecursively()
        db.close()
    }

    private fun createBitmapFile(
        fileName: String,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
    ): File {
        val file = File(tempDir, fileName)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF8B5CF6.toInt())
        FileOutputStream(file).use { fos ->
            assertTrue("Compress $fileName failed", bmp.compress(format, 92, fos))
        }
        bmp.recycle()
        return file
    }

    private fun createSineWaveWavFile(
        fileName: String,
        sampleRateHz: Int = 44100,
        durationSeconds: Float = 1.5f,
    ): File {
        val file = File(tempDir, fileName)
        val totalFrames = (sampleRateHz * durationSeconds).toInt()
        val dataSize = totalFrames * 2 // 16-bit mono
        val riffSize = 36 + dataSize
        val bb = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(riffSize)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16) // PCM fmt size
        bb.putShort(1) // PCM format
        bb.putShort(1) // mono
        bb.putInt(sampleRateHz)
        bb.putInt(sampleRateHz * 2) // byte rate
        bb.putShort(2) // block align
        bb.putShort(16) // bits per sample
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataSize)
        for (i in 0 until totalFrames) {
            val sample = (sin(2.0 * PI * 440.0 * i / sampleRateHz) * 24000.0).toInt().toShort()
            bb.putShort(sample)
        }
        file.writeBytes(bb.array())
        return file
    }

    @Test
    fun `real JPEG PNG and WebP images import via file and transient content URI without image open error`() = runBlocking {
        val jpgFile = createBitmapFile("photo.jpg", 640, 480, Bitmap.CompressFormat.JPEG)
        val pngFile = createBitmapFile("graphic.png", 320, 640, Bitmap.CompressFormat.PNG)
        @Suppress("DEPRECATION")
        val webpFile = createBitmapFile("sticker.webp", 512, 512, Bitmap.CompressFormat.WEBP)

        // 1. file:// JPEG
        val jpgResult = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(jpgFile).toString(), "proj-img")
        assertTrue("Expected JPEG import success but got ${jpgResult.exceptionOrNull()}", jpgResult.isSuccess)
        val jpgAsset = jpgResult.getOrThrow()
        assertEquals(MediaType.IMAGE, jpgAsset.mediaType)
        assertEquals(640, jpgAsset.width)
        assertEquals(480, jpgAsset.height)
        assertNotNull(jpgAsset.thumbnailPath)
        assertTrue(File(jpgAsset.thumbnailPath!!).exists())

        // 2. content:// transient URI (copied safely to managed cache)
        val contentUri = Uri.parse("content://com.example.test.provider/images/901")
        shadowOf(context.contentResolver).registerInputStream(contentUri, ByteArrayInputStream(pngFile.readBytes()))
        val pngResult = probeAdapter.probeAndCacheThumbnail(contentUri.toString(), "proj-img")
        assertTrue("Expected content:// PNG import success but got ${pngResult.exceptionOrNull()}", pngResult.isSuccess)
        val pngAsset = pngResult.getOrThrow()
        assertEquals(MediaType.IMAGE, pngAsset.mediaType)
        assertEquals(320, pngAsset.width)
        assertEquals(640, pngAsset.height)
        assertNotNull(pngAsset.cachedLocalPath)
        assertTrue(File(pngAsset.cachedLocalPath!!).exists())

        // 3. WebP still image
        val webpResult = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(webpFile).toString(), "proj-img")
        assertTrue("Expected WebP import success but got ${webpResult.exceptionOrNull()}", webpResult.isSuccess)
        assertEquals(512, webpResult.getOrThrow().width)
        assertEquals(512, webpResult.getOrThrow().height)
    }

    @Test
    fun `exif rotated JPEG swaps visual dimensions and writes upright plate`() = runBlocking {
        val jpgFile = createBitmapFile("portrait_exif.jpg", 800, 400, Bitmap.CompressFormat.JPEG)
        val exif = ExifInterface(jpgFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        exif.saveAttributes()

        val result = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(jpgFile).toString(), "proj-exif")
        assertTrue(result.isSuccess)
        val asset = result.getOrThrow()
        assertEquals(90, asset.rotationDeg)
        assertEquals(400, asset.width)
        assertEquals(800, asset.height)
        assertNotNull(asset.cachedLocalPath)
        assertTrue(File(asset.cachedLocalPath!!).exists())
    }

    @Test
    fun `calculateInSampleSize safely downsamples large 4K image dimensions`() {
        assertEquals(2, ImageImportEngine.calculateInSampleSize(4000, 4000, 1920, 1920))
        assertEquals(1, ImageImportEngine.calculateInSampleSize(1280, 720, 1920, 1920))
        assertEquals(4, ImageImportEngine.calculateInSampleSize(8000, 8000, 1920, 1920))
    }

    @Test
    fun `real WAV audio imports into A1 track with duration sample rate and waveform samples`() = runBlocking {
        val wavFile = createSineWaveWavFile("synth_lead.wav", sampleRateHz = 44100, durationSeconds = 1.5f)
        val result = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(wavFile).toString(), "proj-audio")
        assertTrue("Expected WAV import success but got ${result.exceptionOrNull()}", result.isSuccess)
        val asset = result.getOrThrow()
        assertEquals(MediaType.AUDIO, asset.mediaType)
        assertEquals(44100, asset.sampleRateHz)
        assertEquals(1, asset.channelCount)
        assertEquals("audio/raw", asset.audioCodec)
        assertTrue("Duration should be ~1.5s, was ${asset.durationUs}", asset.durationUs in 1_450_000L..1_550_000L)
        assertEquals(48, asset.waveformSamples.size)
        assertTrue("Waveform should reflect actual sine amplitude", asset.waveformSamples.any { it > 0.35f })

        // Verify Command places AUDIO asset onto TrackType.AUDIO ("a1") and survives JSON + Room round-trip
        val created = projectRepository.createProject(
            name = "Audio Timeline",
            aspectRatio = AspectRatioPreset.RATIO_9_16,
            frameRate = FrameRatePreset.FPS_30,
        )
        val updated = AddAssetClipCommand(asset = asset, clipId = "audio-clip-1").apply(created, 1000L)
        val audioClips = updated.tracks.first { it.type == TrackType.AUDIO }.clips
        assertEquals(1, audioClips.size)
        assertEquals("audio-clip-1", audioClips.first().id)
        projectRepository.saveProject(updated)

        val reloaded = projectRepository.loadProject(created.id)!!
        assertEquals(1, reloaded.tracks.first { it.type == TrackType.AUDIO }.clips.size)
        assertEquals(48, reloaded.assets[asset.id]?.waveformSamples?.size)
        assertEquals(44100, reloaded.assets[asset.id]?.sampleRateHz)
    }

    @Test
    fun `mime sniffing identifies magic bytes accurately across photo video and audio formats`() {
        assertEquals("image/jpeg", MediaUriHelper.sniffMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()), "a"))
        assertEquals("image/png", MediaUriHelper.sniffMimeType(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), "a"))
        assertEquals("image/webp", MediaUriHelper.sniffMimeType("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray(), "a"))
        assertEquals("audio/wav", MediaUriHelper.sniffMimeType("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray(), "a"))
        assertEquals("image/heic", MediaUriHelper.sniffMimeType("\u0000\u0000\u0000\u0018ftypheic".toByteArray(), "a"))
        assertEquals("video/mp4", MediaUriHelper.sniffMimeType("\u0000\u0000\u0000\u0018ftypisom".toByteArray(), "a"))
        assertEquals("audio/mpeg", MediaUriHelper.sniffMimeType("ID3\u0003\u0000\u0000\u0000".toByteArray(), "a"))
        assertEquals("audio/flac", MediaUriHelper.sniffMimeType("fLaC\u0000\u0000\u0000\u0022".toByteArray(), "a"))
    }

    @Test
    fun `corrupted missing and unsupported media return typed MediaImportException without crashing`() = runBlocking {
        // Non-existent file
        val missingRes = probeAdapter.probeAndCacheThumbnail("file:///does/not/exist.jpg", "p1")
        assertTrue(missingRes.isFailure)
        assertEquals(ImportFailureReason.FILE_NOT_FOUND, (missingRes.exceptionOrNull() as MediaImportException).reason)

        // Unsupported file extension + text bytes
        val txtFile = File(tempDir, "notes.txt").apply { writeText("hello world") }
        val unsupportedRes = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(txtFile).toString(), "p1")
        assertTrue(unsupportedRes.isFailure)
        assertEquals(ImportFailureReason.UNSUPPORTED_FORMAT, (unsupportedRes.exceptionOrNull() as MediaImportException).reason)

        // Corrupted JPEG (valid header + garbage payload)
        val badJpg = File(tempDir, "corrupt.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x11, 0x22, 0x33))
        }
        val corruptJpgRes = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(badJpg).toString(), "p1")
        assertTrue(corruptJpgRes.isFailure)
        assertTrue(
            (corruptJpgRes.exceptionOrNull() as MediaImportException).reason in setOf(
                ImportFailureReason.CORRUPTED_MEDIA,
                ImportFailureReason.DECODE_ERROR,
            )
        )

        // Corrupted MP4 / MP3
        val badMp4 = File(tempDir, "broken.mp4").apply { writeText("not a valid mp4 container") }
        val corruptMp4Res = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(badMp4).toString(), "p1")
        assertTrue(corruptMp4Res.isFailure)
        assertEquals(ImportFailureReason.CORRUPTED_MEDIA, (corruptMp4Res.exceptionOrNull() as MediaImportException).reason)

        val badMp3 = File(tempDir, "broken.mp3").apply { writeText("not an mp3 stream") }
        val corruptMp3Res = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(badMp3).toString(), "p1")
        assertTrue(corruptMp3Res.isFailure)
        assertEquals(ImportFailureReason.CORRUPTED_MEDIA, (corruptMp3Res.exceptionOrNull() as MediaImportException).reason)
    }

    @Test
    fun `deleted external file is flagged unavailable on StudioViewModel load and playback controller cleans up`() = runBlocking {
        val externalFile = File(tempDir, "external_clip.mp4").apply { writeBytes(ByteArray(128) { 1 }) }
        val created = projectRepository.createProject(
            name = "Missing Test",
            aspectRatio = AspectRatioPreset.RATIO_16_9,
            frameRate = FrameRatePreset.FPS_30,
        )
        val asset = MediaAsset(
            id = "ext-1",
            sourceUri = Uri.fromFile(externalFile).toString(),
            displayName = "external_clip.mp4",
            mediaType = MediaType.VIDEO,
            durationUs = 3_000_000L,
            width = 1920,
            height = 1080,
            uriPersisted = true,
            isAccessible = true,
        )
        projectRepository.saveProject(AddAssetClipCommand(asset, "c-ext").apply(created, 1000L))

        // Delete the uncached external file
        assertTrue(externalFile.delete())

        val playback = AndroidSequencePlaybackController(context, hardwareAccelerationRequested = false)
        val vm = StudioViewModel(
            projectId = created.id,
            projectStorage = projectRepository,
            mediaProbe = probeAdapter,
            playbackPort = playback,
            mediaLibraryRepository = mediaLibraryRepository,
        )
        try {
            val deadline = System.currentTimeMillis() + 4_000L
            while (vm.uiState.value.isLoading && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            shadowOf(Looper.getMainLooper()).idle()
            val loadedState = vm.uiState.value
            assertFalse(loadedState.isLoading)
            assertEquals(listOf("external_clip.mp4"), loadedState.missingAssetNames)
            assertFalse(loadedState.project?.assets?.get("ext-1")?.isAccessible ?: true)

            // Also verify MediaLibraryRepository records & observes real media
            val wavFile = createSineWaveWavFile("library_beat.wav", durationSeconds = 1.0f)
            val audioAsset = probeAdapter.probeAndCacheThumbnail(Uri.fromFile(wavFile).toString(), created.id).getOrThrow()
            mediaLibraryRepository.recordImportedAsset(audioAsset)
            val recent = mediaLibraryRepository.observeRecentMedia().first()
            assertEquals(1, recent.size)
            assertEquals("library_beat.wav", recent.first().displayName)
            assertEquals(MediaType.AUDIO, recent.first().mediaType)
        } finally {
            vm.viewModelScope.cancel()
            playback.release()
        }
    }

    @Test
    fun `ProjectJsonCodec preserves new MediaAsset metadata with backward compatibility`() {
        val asset = MediaAsset(
            id = "v-json",
            sourceUri = "content://media/video/55",
            displayName = "B-Roll.mp4",
            mediaType = MediaType.VIDEO,
            durationUs = 5_000_000L,
            width = 1920,
            height = 1080,
            fps = 60f,
            hasAudio = true,
            mimeType = "video/mp4",
            sampleRateHz = 48000,
            channelCount = 2,
            audioCodec = "audio/mp4a-latm",
            videoCodec = "video/avc",
            filmstripPaths = listOf("/tmp/f0.jpg", "/tmp/f1.jpg"),
            waveformSamples = listOf(0.2f, 0.8f, 0.5f),
            cachedLocalPath = "/tmp/cached.mp4",
            uriPersisted = false,
            isAccessible = true,
        )
        val project = AddAssetClipCommand(asset, "c1").apply(
            TimelineProject(id = "p-json", name = "Codec Test", createdAtMs = 1000L, updatedAtMs = 1000L),
            1000L,
        )
        val encoded = ProjectJsonCodec.encode(project)
        val decoded = ProjectJsonCodec.decode(encoded)
        val restored = decoded.assets["v-json"]!!
        assertEquals("video/mp4", restored.mimeType)
        assertEquals(48000, restored.sampleRateHz)
        assertEquals(2, restored.channelCount)
        assertEquals("audio/mp4a-latm", restored.audioCodec)
        assertEquals("video/avc", restored.videoCodec)
        assertEquals(listOf("/tmp/f0.jpg", "/tmp/f1.jpg"), restored.filmstripPaths)
        assertEquals(listOf(0.2f, 0.8f, 0.5f), restored.waveformSamples)
        assertEquals("/tmp/cached.mp4", restored.cachedLocalPath)
    }
}
