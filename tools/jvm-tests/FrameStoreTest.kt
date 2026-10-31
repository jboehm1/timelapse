package eu.example.constructionlapse

import java.io.File
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("timelapse-store-test").toFile()
    try {
        val store = FrameStore(root)
        check(store.nextId() == 1L)
        val jpg = byteArrayOf(-1,-40,0,0,0,0,0,0,-1,-39)
        val temp1 = File(root,"frame_0000000001_1700000.pending").apply { writeBytes(jpg) }
        val done = store.commit(temp1,"frame_0000000001_1700000.jpg",1700000)
        check(done.exists() && !temp1.exists() && store.nextId() == 2L)
        check(store.originals().size == 1)
        check(runCatching {store.commit(File(root,"none"),done.name,1700000)}.isFailure)
        check(done.readBytes().contentEquals(jpg))
        val validPending = File(root,"frame_0000000002_1700001.pending").apply {writeBytes(jpg)}
        val invalidPending = File(root,"frame_0000000003_1700002.pending").apply {writeText("oops")}
        val validPng = File(root,"frame_0000000004_import.png.partial").apply {
            writeBytes(byteArrayOf(-119,80,78,71,13,10,26,10,0,0,0,0,0,0,0,0,73,69,78,68,-82,66,96,-126))
        }
        val rec = store.recover()
        check(rec.promoted == 2) { rec }
        check(rec.quarantined == 1) { rec }
        check(rec.originals == 3) { rec }
        check(rec.nextId == 5L) { rec }
        check(!validPending.exists() && !validPng.exists() && !invalidPending.exists())
        check(File(root,"frame_0000000003_1700002.pending.incomplete").exists())
        check(store.originals().size == 3)
        check(File(root,"frames.journal").readLines().size == 3)
        check(store.recover().promoted == 0)
        check(store.recover().quarantined == 0)
        check(store.auditOriginals().isEmpty())
        done.writeBytes(jpg + byteArrayOf(1))
        check(store.auditOriginals().contains(done.name))
        check(runCatching { store.commit(File(root,"evil"),"../bad.jpg",0) }.isFailure)
        // Simulate a failure after atomic rename but before the journal append.
        // Recovery must discover the durable original and reconstruct its journal row.
        val interruptedBytes = byteArrayOf(-1,-40,9,8,7,6,5,4,-1,-39)
        val interrupted = File(root,"frame_0000000005_1700004.pending").apply { writeBytes(interruptedBytes) }
        val journalPath = File(root,"frames.journal")
        journalPath.delete()
        check(journalPath.mkdir())
        val finalName = "frame_0000000005_1700004.jpg"
        check(runCatching { store.commit(interrupted,finalName,1700004) }.isFailure)
        val committed = File(root,finalName)
        check(committed.isFile && committed.readBytes().contentEquals(interruptedBytes))
        check(!interrupted.exists())
        journalPath.deleteRecursively()
        val reconciled = store.recover()
        check(reconciled.originals == 4) { reconciled }
        check(store.auditOriginals().isEmpty()) { store.auditOriginals() }
        check(done.delete())
        check(store.auditOriginals() == listOf(done.name)) { store.auditOriginals() }
        println("PASS: FrameStore original preservation, atomic publish, resume, quarantine, journal")
    } finally { root.deleteRecursively() }
}
