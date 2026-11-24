package com.example.core

import com.example.core.command.AddAssetClipCommand
import com.example.core.command.CommandHistoryManager
import com.example.core.command.DeleteClipCommand
import com.example.core.command.MoveClipOrderCommand
import com.example.core.command.SplitClipCommand
import com.example.core.command.TrimClipCommand
import com.example.core.command.UpdateProjectSettingsCommand
import com.example.core.model.AspectRatioPreset
import com.example.core.model.FrameRatePreset
import com.example.core.model.MediaAsset
import com.example.core.model.MediaType
import com.example.core.model.TimelineProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryManagerTest {

    private fun createEmptyProject() = TimelineProject(
        id = "proj-1",
        name = "Test Sequence",
        createdAtMs = 1000L,
        updatedAtMs = 1000L,
    )

    private fun videoAsset(id: String, durationUs: Long) = MediaAsset(
        id = id,
        sourceUri = "content://media/$id",
        displayName = "clip_$id.mp4",
        mediaType = MediaType.VIDEO,
        durationUs = durationUs,
        width = 1920,
        height = 1080,
    )

    @Test
    fun `add, split, trim, reorder, delete and undo-redo maintain exact timeline invariants`() {
        val manager = CommandHistoryManager(createEmptyProject())
        assertFalse(manager.snapshot().canUndo)

        // 1. Add clip (4 seconds)
        manager.execute(AddAssetClipCommand(videoAsset("a1", 4_000_000L), clipId = "c1"), 2000L)
        assertEquals(4_000_000L, manager.snapshot().project.totalDurationUs())
        assertTrue(manager.snapshot().canUndo)

        // 2. Split at 1.5s
        manager.execute(SplitClipCommand("c1", splitAtTimelineUs = 1_500_000L, secondClipId = "c2"), 3000L)
        val splitClips = manager.snapshot().project.primaryVideoTrack().clips
        assertEquals(2, splitClips.size)
        assertEquals(1_500_000L, splitClips[0].timelineDurationUs)
        assertEquals(2_500_000L, splitClips[1].timelineDurationUs)
        assertEquals(1_500_000L, splitClips[1].timelineStartUs)

        // 3. Reorder c2 before c1
        manager.execute(MoveClipOrderCommand("c2", deltaIndex = -1), 4000L)
        val reordered = manager.snapshot().project.primaryVideoTrack().clips
        assertEquals("c2", reordered[0].id)
        assertEquals(0L, reordered[0].timelineStartUs)
        assertEquals("c1", reordered[1].id)
        assertEquals(2_500_000L, reordered[1].timelineStartUs)

        // 4. Trim c1 down to 1.0s
        manager.execute(TrimClipCommand("c1", newSourceStartUs = 0L, newSourceEndUs = 1_000_000L), 5000L)
        assertEquals(3_500_000L, manager.snapshot().project.totalDurationUs())

        // 5. Delete c2 -> c1 slides back to 0L
        manager.execute(DeleteClipCommand("c2"), 6000L)
        val remaining = manager.snapshot().project.primaryVideoTrack().clips
        assertEquals(1, remaining.size)
        assertEquals(0L, remaining[0].timelineStartUs)
        assertEquals(1_000_000L, manager.snapshot().project.totalDurationUs())

        // 6. Undo Delete & Undo Trim
        manager.undo()
        assertEquals(2, manager.snapshot().project.primaryVideoTrack().clips.size)
        assertEquals(3_500_000L, manager.snapshot().project.totalDurationUs())
        assertTrue(manager.snapshot().canRedo)

        manager.undo()
        assertEquals(4_000_000L, manager.snapshot().project.totalDurationUs())

        // 7. Redo Trim
        manager.redo()
        assertEquals(3_500_000L, manager.snapshot().project.totalDurationUs())

        // 8. Executing new command clears redo stack
        manager.execute(
            UpdateProjectSettingsCommand("Cinema Cut", AspectRatioPreset.RATIO_16_9, FrameRatePreset.FPS_60),
            7000L,
        )
        assertFalse(manager.snapshot().canRedo)
        assertEquals("Cinema Cut", manager.snapshot().project.name)
        assertEquals(AspectRatioPreset.RATIO_16_9, manager.snapshot().project.aspectRatio)
        assertEquals(FrameRatePreset.FPS_60, manager.snapshot().project.frameRate)
    }
}
