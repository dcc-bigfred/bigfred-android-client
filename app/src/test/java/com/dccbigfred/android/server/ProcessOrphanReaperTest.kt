package com.dccbigfred.android.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProcessOrphanReaperTest {
    @Test
    fun readPid_missingFile_returnsNull() {
        val f = File.createTempFile("pid", ".txt")
        f.delete()
        assertTrue(ProcessOrphanReaper.readPid(f) == null)
    }

    @Test
    fun writeAndReadPid_roundTrip() {
        val f = File.createTempFile("pid", ".txt")
        f.deleteOnExit()
        ProcessOrphanReaper.writePidIdentity(f, 4242, 99L)
        assertTrue(ProcessOrphanReaper.readPid(f) == 4242)
        val id = ProcessOrphanReaper.readPidIdentity(f)!!
        assertTrue(id.pid == 4242)
        assertTrue(id.starttime == 99L)
    }

    @Test
    fun matchesIdentity_rejectsStarttimeMismatch() {
        val fake = ProcessOrphanReaper.PidIdentity(pid = 1, starttime = 1L)
        // PID 1 is alive on Linux/Android hosts used for unit tests, but starttime won't match 1.
        assertFalse(ProcessOrphanReaper.matchesIdentity(fake, "init"))
    }

    @Test
    fun isLocalUrl_detectsLoopback() {
        assertTrue(LocoServerService.isLocalUrl("http://127.0.0.1:8080"))
        assertTrue(LocoServerService.isLocalUrl("http://localhost:8080"))
        assertFalse(LocoServerService.isLocalUrl("http://192.168.0.120:8080"))
        assertFalse(LocoServerService.isLocalUrl(null))
    }
}
