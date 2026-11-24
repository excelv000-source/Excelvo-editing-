package com.example.di

import android.content.Context
import com.example.core.ports.MediaProbePort
import com.example.core.ports.ProjectStoragePort
import com.example.core.ports.SequencePlaybackPort
import com.example.data.db.ExcevoDatabase
import com.example.data.media.MediaLibraryRepository
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.presets.LocalPresetRepository
import com.example.data.project.LocalProjectRepository
import com.example.data.storage.StorageManager
import com.example.media.playback.AndroidSequencePlaybackController
import com.example.media.probe.AndroidMediaProbeAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database: ExcevoDatabase by lazy {
        ExcevoDatabase.getInstance(appContext)
    }

    val projectStorage: ProjectStoragePort by lazy {
        LocalProjectRepository(
            context = appContext,
            projectDao = database.projectDao(),
        )
    }

    val mediaProbe: MediaProbePort by lazy {
        AndroidMediaProbeAdapter(appContext)
    }

    val mediaLibraryRepository: MediaLibraryRepository by lazy {
        MediaLibraryRepository(
            context = appContext,
            recentMediaDao = database.recentMediaDao(),
        )
    }

    val userPreferences: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    val storageManager: StorageManager by lazy {
        StorageManager(
            context = appContext,
            libraryDao = database.libraryDao(),
        )
    }

    val localPresetRepository: LocalPresetRepository by lazy {
        LocalPresetRepository(
            savedPresetDao = database.savedPresetDao(),
            projectStorage = projectStorage,
            storageManager = storageManager,
        )
    }

    fun createSequencePlaybackController(): SequencePlaybackPort {
        val hwAccel = runCatching {
            runBlocking { userPreferences.preferences.first().hardwareDecodeEnabled }
        }.getOrDefault(true)
        return AndroidSequencePlaybackController(
            context = appContext,
            hardwareAccelerationRequested = hwAccel,
        )
    }
}
