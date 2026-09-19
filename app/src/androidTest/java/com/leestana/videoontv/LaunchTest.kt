package com.leestana.videoontv

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @Test fun activityLaunchesAndSurvivesResume() {
        ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_MAIN)).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            Thread.sleep(2_000)
        }
    }
}

