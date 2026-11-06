package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.ui.screens.upload.ImportOperationState
import com.cit.kaido.voxsight.ui.screens.upload.UploadManager
import com.cit.kaido.voxsight.ui.screens.upload.UploadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class UploadManagerStateTest {

    @Before
    fun setUp() {
        UploadManager.clearReadyState()
    }

    @Test
    fun `test 01 - single press on Import File transitions IDLE to PICKER_OPENING and PICKER_OPEN`() {
        assertEquals(ImportOperationState.IDLE, UploadManager.operationState.value)
        assertFalse(UploadManager.isBusy())

        val accepted = UploadManager.tryBeginPickerOpening()
        assertTrue("First press on Import File must succeed", accepted)
        assertEquals(ImportOperationState.PICKER_OPENING, UploadManager.operationState.value)
        assertTrue(UploadManager.isBusy())

        val markedOpen = UploadManager.markPickerOpen()
        assertTrue("Transition to PICKER_OPEN must succeed", markedOpen)
        assertEquals(ImportOperationState.PICKER_OPEN, UploadManager.operationState.value)
        assertTrue(UploadManager.isBusy())
    }

    @Test
    fun `test 02 - rapid multiple presses on Import File allow only a single picker launch`() {
        val threadCount = 16
        val pool = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            pool.submit {
                try {
                    startLatch.await()
                    if (UploadManager.tryBeginPickerOpening()) {
                        successCount.incrementAndGet()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await()
        pool.shutdown()

        assertEquals("Exactly one concurrent press may acquire PICKER_OPENING", 1, successCount.get())
        assertEquals(ImportOperationState.PICKER_OPENING, UploadManager.operationState.value)

        // Additional presses while in PICKER_OPENING or PICKER_OPEN must also be rejected
        assertFalse(UploadManager.tryBeginPickerOpening())
        assertTrue(UploadManager.markPickerOpen())
        assertFalse(UploadManager.tryBeginPickerOpening())
    }

    @Test
    fun `test 03 - pressing Take Photo while Import File is active is rejected`() {
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertFalse("Take Photo must be rejected while PICKER_OPENING", UploadManager.tryBeginCapturing())

        assertTrue(UploadManager.markPickerOpen())
        assertFalse("Take Photo must be rejected while PICKER_OPEN", UploadManager.tryBeginCapturing())

        assertTrue(UploadManager.tryBeginImporting("Happy_Birthday.pdf"))
        assertFalse("Take Photo must be rejected while IMPORTING", UploadManager.tryBeginCapturing())

        UploadManager.markProcessing("Digitizing sheet music...")
        assertFalse("Take Photo must be rejected while PROCESSING", UploadManager.tryBeginCapturing())
    }

    @Test
    fun `test 04 - pressing Import File while Take Photo is active is rejected`() {
        assertTrue(UploadManager.tryBeginCapturing())
        assertEquals(ImportOperationState.CAPTURING, UploadManager.operationState.value)
        assertTrue(UploadManager.isBusy())

        assertFalse("Import File must be rejected while CAPTURING", UploadManager.tryBeginPickerOpening())
        assertFalse("Second Take Photo press must be rejected while CAPTURING", UploadManager.tryBeginCapturing())
    }

    @Test
    fun `test 05 - full happy path from file selection to IMPORTING, PROCESSING, and READY`() {
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertTrue(UploadManager.markPickerOpen())

        // File selected from picker
        assertTrue(UploadManager.tryBeginImporting("Amare_Score.pdf"))
        assertEquals(ImportOperationState.IMPORTING, UploadManager.operationState.value)
        assertEquals(UploadStatus.PROCESSING, UploadManager.uploadStatus.value)
        assertEquals("Amare_Score.pdf", UploadManager.pendingFileName)
        assertTrue(UploadManager.isBusy())

        // Duplicate import call while already importing must be blocked
        assertFalse(UploadManager.tryBeginImporting("Duplicate.pdf"))

        // Local stream copied -> OMR processing starts
        UploadManager.markProcessing("Digitizing staves & notes...")
        assertEquals(ImportOperationState.PROCESSING, UploadManager.operationState.value)
        assertEquals(UploadStatus.PROCESSING, UploadManager.uploadStatus.value)
        assertTrue(UploadManager.isBusy())

        // OMR completes -> READY
        UploadManager.markReady("<score-partwise/>", "Amare Score")
        assertEquals(ImportOperationState.READY, UploadManager.operationState.value)
        assertEquals(UploadStatus.READY, UploadManager.uploadStatus.value)
        assertEquals("<score-partwise/>", UploadManager.pendingMusicXml)
        assertEquals("Amare Score", UploadManager.pendingScoreTitle)
        assertFalse("isBusy should be false once READY", UploadManager.isBusy())
    }

    @Test
    fun `test 06 - cancelling file picker resets state to IDLE and re-enables actions`() {
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertTrue(UploadManager.markPickerOpen())

        // User cancels file picker (uri == null)
        UploadManager.onPickerCancelledOrFailed()
        assertEquals(ImportOperationState.IDLE, UploadManager.operationState.value)
        assertEquals(UploadStatus.IDLE, UploadManager.uploadStatus.value)
        assertFalse(UploadManager.isBusy())

        // User can immediately press Import File or Take Photo again
        assertTrue(UploadManager.tryBeginPickerOpening())
        UploadManager.onPickerCancelledOrFailed()
        assertTrue(UploadManager.tryBeginCapturing())
    }

    @Test
    fun `test 07 - file picker launch failure transitions to ERROR and allows retry`() {
        assertTrue(UploadManager.tryBeginPickerOpening())

        // Simulate ActivityNotFoundException / launch failure
        UploadManager.onPickerCancelledOrFailed("Unable to open the device file manager. Please try again.")
        assertEquals(ImportOperationState.ERROR, UploadManager.operationState.value)
        assertEquals(UploadStatus.IDLE, UploadManager.uploadStatus.value)
        assertEquals("Unable to open the device file manager. Please try again.", UploadManager.activeError)
        assertFalse(UploadManager.isBusy())

        // User can retry directly from ERROR state or after dismissing error dialog
        assertTrue("Retry must be allowed from ERROR state", UploadManager.tryBeginPickerOpening())
        assertNull(UploadManager.activeError)
    }

    @Test
    fun `test 08 - invalid or unreadable file processing failure transitions to ERROR and allows retry`() {
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertTrue(UploadManager.markPickerOpen())
        assertTrue(UploadManager.tryBeginImporting("corrupted.pdf"))
        UploadManager.markProcessing("Uploading score...")

        // Processing fails
        UploadManager.markFailed("The selected file could not be read or is empty.")
        assertEquals(ImportOperationState.ERROR, UploadManager.operationState.value)
        assertEquals(UploadStatus.IDLE, UploadManager.uploadStatus.value)
        assertFalse(UploadManager.isBusy())

        // Dismiss error dialog
        UploadManager.acknowledgeError()
        assertEquals(ImportOperationState.IDLE, UploadManager.operationState.value)
        assertNull(UploadManager.activeError)

        // Retry with another file or photo
        assertTrue(UploadManager.tryBeginPickerOpening())
    }

    @Test
    fun `test 09 - rapid switching between Import File and Take Photo is serialized safely`() {
        // Start Import File -> block Take Photo -> cancel Import File -> start Take Photo -> cancel -> start Import File
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertFalse(UploadManager.tryBeginCapturing())
        UploadManager.onPickerCancelledOrFailed()

        assertTrue(UploadManager.tryBeginCapturing())
        assertFalse(UploadManager.tryBeginPickerOpening())
        UploadManager.onCaptureCancelledOrFailed()

        assertTrue(UploadManager.tryBeginPickerOpening())
        UploadManager.onPickerCancelledOrFailed()
        assertEquals(ImportOperationState.IDLE, UploadManager.operationState.value)
    }

    @Test
    fun `test 10 - stale PICKER_OPEN state is safely released on activity resume`() {
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertTrue(UploadManager.markPickerOpen())

        // Before minElapsedMs, it should not release prematurely
        assertFalse(UploadManager.releaseStalePickerIfStillOpen(minElapsedMs = 500L, nowMs = System.currentTimeMillis() + 100L))
        assertEquals(ImportOperationState.PICKER_OPEN, UploadManager.operationState.value)

        // After minElapsedMs, it releases back to IDLE
        assertTrue(UploadManager.releaseStalePickerIfStillOpen(minElapsedMs = 500L, nowMs = System.currentTimeMillis() + 1000L))
        assertEquals(ImportOperationState.IDLE, UploadManager.operationState.value)
        assertFalse(UploadManager.isBusy())
    }

    @Test
    fun `test 11 - late picker cancel callback does not clobber active IMPORTING or PROCESSING state`() {
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertTrue(UploadManager.markPickerOpen())
        assertTrue(UploadManager.tryBeginImporting("Happy_Birthday.xml"))

        // If a late cancel/cleanup callback fires after import has started, it must be ignored
        UploadManager.onPickerCancelledOrFailed()
        assertEquals(ImportOperationState.IMPORTING, UploadManager.operationState.value)

        UploadManager.markProcessing("Analyzing vocal parts (SATB)...")
        UploadManager.onPickerCancelledOrFailed()
        assertEquals(ImportOperationState.PROCESSING, UploadManager.operationState.value)
    }

    @Test
    fun `test 12 - sequential imports of different files reset cleanly between runs`() {
        // First file import
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertTrue(UploadManager.markPickerOpen())
        assertTrue(UploadManager.tryBeginImporting("First_Score.pdf"))
        UploadManager.markProcessing("Uploading score...")
        UploadManager.markReady("<score-partwise id='1'/>", "First Score")
        assertEquals(ImportOperationState.READY, UploadManager.operationState.value)

        // Navigation to review clears ready state
        UploadManager.clearReadyState()
        assertEquals(ImportOperationState.IDLE, UploadManager.operationState.value)
        assertNull(UploadManager.pendingMusicXml)

        // Second file import immediately after returning to UploadScoreScreen
        assertTrue(UploadManager.tryBeginPickerOpening())
        assertTrue(UploadManager.markPickerOpen())
        assertTrue(UploadManager.tryBeginImporting("Second_Score.pdf"))
        UploadManager.markProcessing("Uploading score...")
        UploadManager.markReady("<score-partwise id='2'/>", "Second Score")
        assertEquals("Second Score", UploadManager.pendingScoreTitle)
        assertEquals("<score-partwise id='2'/>", UploadManager.pendingMusicXml)
    }
}

