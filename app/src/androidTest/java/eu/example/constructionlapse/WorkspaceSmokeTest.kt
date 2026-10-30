package eu.example.constructionlapse

import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.core.view.WindowInsetsCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class WorkspaceSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA, android.Manifest.permission.POST_NOTIFICATIONS)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val evidence = File(context.filesDir, "workspace-evidence").apply { mkdirs() }
    private val library = ProjectLibrary(File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "projects"))
    @After fun saveFinalUi() {
        runCatching { device.takeScreenshot(File(evidence, "last-ui.png")); device.dumpWindowHierarchy(File(evidence, "last-ui.xml")) }
    }
    private fun pumpCompose() {
        var workspaceResumed = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            workspaceResumed = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).any { it is WorkspaceActivity }
        }
        // The legacy camera has no Compose root. Do not wait for one on that screen.
        if (workspaceResumed) compose.waitForIdle()
    }
    private fun waitFor(label: String, ready: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 45_000
        while (SystemClock.elapsedRealtime() < end) { pumpCompose(); if (ready()) return; SystemClock.sleep(250) }
        device.takeScreenshot(File(evidence, "failure.png"))
        device.dumpWindowHierarchy(File(evidence, "failure.xml"))
        fail("Timed out $label: ${CaptureService.activeService?.status}")
    }
    private fun awaitUi(selector: androidx.test.uiautomator.BySelector, timeout: Long): androidx.test.uiautomator.UiObject2? {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) {
            // Compose's test clock must advance between external UiAutomator actions.
            pumpCompose()
            device.findObject(selector)?.let { return it }
            SystemClock.sleep(100)
        }
        return null
    }
    private fun clickText(text: String) {
        val item = awaitUi(By.text(text), 10_000)
        assertNotNull("Missing $text", item); item!!.click(); pumpCompose(); device.waitForIdle()
    }
    private fun clickDescription(description: String) {
        val item = awaitUi(By.desc(description), 15_000)
        assertNotNull("Missing $description", item); item!!.click(); pumpCompose(); device.waitForIdle()
    }
    private fun set(description: String, value: String) {
        // Drive Compose's real text-edit semantics and wait for recomposition.
        compose.onNodeWithContentDescription(description).performTextReplacement(value)
        pumpCompose()
    }
    private fun renameText(value: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        pumpCompose()
    }
    private fun hideKeyboard() {
        device.waitForIdle()
        var visible = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            activity?.window?.decorView?.rootWindowInsets?.let {
                visible = WindowInsetsCompat.toWindowInsetsCompat(it).isVisible(WindowInsetsCompat.Type.ime())
            }
        }
        if (visible) { device.pressBack(); device.waitForIdle() }
    }
    private fun scrollTo(text: String) {
        repeat(8) {
            pumpCompose()
            if (device.hasObject(By.text(text))) return
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, .7f)
            pumpCompose()
            device.waitForIdle()
        }
        assertTrue("Cannot scroll to $text", device.hasObject(By.text(text)))
    }
    private fun openProject(title: String) {
        val item = awaitUi(By.text(title), 15_000)
        assertNotNull("Missing project $title", item); item!!.click(); pumpCompose(); device.waitForIdle()
    }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun projectPlannerFiniteCaptureTestPhotoGalleryAndTrashPreserveOriginals() {
        EmulatorTestDevice.prepare(device, evidence)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        clickDescription("Open projects library")
        assertTrue(device.wait(Until.hasObject(By.desc("Create project")), 15_000))
        device.takeScreenshot(File(evidence, "projects-home.png"))
        clickDescription("Create project")
        val title = "Solar roof ${System.currentTimeMillis()}"
        set("New project name", title)
        device.takeScreenshot(File(evidence, "new-project-dialog.png"))
        device.dumpWindowHierarchy(File(evidence, "new-project-dialog.xml"))
        assertNotNull("Create stays disabled after name entry", device.wait(Until.findObject(By.text("Create").enabled(true)), 5000))
        clickText("Create")
        waitFor("create project") { library.list().any { it.title == title } }
        val project = library.list().single { it.title == title }
        openProject(title)
        clickText("Plan")
        set("Interval (seconds)", "0.5")
        // A continuous project runs 24h/day; the separate JVM test verifies 57,600 for an 8h shift.
        hideKeyboard()
        assertTrue(device.wait(Until.hasObject(By.text("172800")), 5000))
        device.takeScreenshot(File(evidence, "planner.png"))
        scrollTo("Unlimited recording")
        clickDescription("Unlimited recording")
        scrollTo("Stop after NEW frames")
        set("Stop after NEW frames", "3")
        hideKeyboard()
        scrollTo("Save capture preset")
        clickText("Save capture preset")
        waitFor("preset persistence") { library.read(project.id).preset.maxFrames == 3L }
        assertEquals(500L, library.read(project.id).preset.intervalMs)
        device.pressBack()
        clickText("Open camera & export")
        clickDescription("Start capture")
        waitFor("finite session completes") { library.frames(project.id).size == 3 && CaptureService.activeService?.isCapturing != true }
        assertEquals(3, library.frames(project.id).size)
        ProjectFileJobs.busy = true
        try {
            clickDescription("Take test photo")
            SystemClock.sleep(1000)
            assertEquals("Capture must not overlap import/export", 3, library.frames(project.id).size)
        } finally { ProjectFileJobs.busy = false }
        clickDescription("Take test photo")
        waitFor("one-shot saves one new original") { library.frames(project.id).size == 4 && CaptureService.activeService?.isCapturing != true }
        SystemClock.sleep(2000)
        assertEquals(4, library.frames(project.id).size)
        val before = library.frames(project.id).associate { it.name to hash(it) }
        clickDescription("Open projects library")
        openProject(title)
        device.takeScreenshot(File(evidence, "project-gallery.png"))
        clickText("Rename")
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)
        assertNotNull(field); renameText("$title renamed")
        clickText("Save name")
        waitFor("rename") { library.read(project.id).title.endsWith("renamed") }
        clickText("Copy preset")
        waitFor("duplicate preset") { library.list().any { it.title == "$title renamed · copy" } }
        val copy = library.list().single { it.title == "$title renamed · copy" }
        assertEquals(0, library.inventory(copy.id).count)
        assertEquals(library.read(project.id).preset, copy.preset)
        clickText("Move to trash")
        assertTrue(device.wait(Until.hasObject(By.text("Move to trash?")), 5000))
        clickText("Move to trash")
        waitFor("archive") { library.read(project.id).archived }
        clickText("Trash")
        openProject("$title renamed")
        clickText("Restore project")
        waitFor("restore") { !library.read(project.id).archived }
        assertEquals(before, library.frames(project.id).associate { it.name to hash(it) })
        device.takeScreenshot(File(evidence, "restored-project.png"))
        device.pressBack()
        clickText("All")
        clickDescription("Create project")
        val manualTitle = "Photolapse ${System.currentTimeMillis()}"
        set("New project name", manualTitle)
        clickDescription("Mode PHOTOLAPSE")
        clickText("Create")
        waitFor("manual project create") { library.list().any { it.title == manualTitle } }
        val manual = library.list().single { it.title == manualTitle }
        openProject(manualTitle)
        clickText("Open camera & export")
        clickDescription("Start capture")
        waitFor("first manual frame") { library.frames(manual.id).size == 1 && CaptureService.activeService?.isCapturing != true }
        clickDescription("Start capture")
        waitFor("second manual frame") { library.frames(manual.id).size == 2 && CaptureService.activeService?.isCapturing != true }
        SystemClock.sleep(1000)
        assertEquals(2, library.frames(manual.id).size)
        assertEquals(before, library.frames(project.id).associate { it.name to hash(it) })
        device.takeScreenshot(File(evidence, "photolapse.png"))
        File(evidence, "project-validation.txt").writeText("PASS: create/rename/copy preset/trash/restore; interval=500ms; finite=3 new JPEGs; test photo=1 new JPEG; 4 original hashes unchanged; separate Photolapse project adds exactly 1 frame per action (2 total); capture blocked during file job\n")
    }
}
