package eu.example.constructionlapse

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Recovery for a positively identified emulator launcher fault, never app ANRs. */
object EmulatorTestDevice {
    fun prepare(device: UiDevice, evidence: File) {
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.waitForIdle()
        repeat(2) {
            val launcherAnr = device.findObject(By.pkg("android").text("Pixel Launcher isn't responding"))
                ?: return
            device.takeScreenshot(File(evidence, "pixel-launcher-anr.png"))
            File(evidence, "emulator-events.txt").appendText("Pixel Launcher ANR detected; stopping only com.google.android.apps.nexuslauncher\n")
            device.executeShellCommand("am force-stop com.google.android.apps.nexuslauncher")
            SystemClock.sleep(500)
            device.waitForIdle()
        }
        check(!device.hasObject(By.pkg("android").text("Pixel Launcher isn't responding"))) {
            "Emulator launcher ANR could not be cleared"
        }
    }
}
