package com.leestana.videoontv

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @Test fun activityLaunchesAndSurvivesResume() {
        ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_MAIN)).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.player_view).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty_state).visibility)
            }
            Thread.sleep(2_000)
        }
    }
}
