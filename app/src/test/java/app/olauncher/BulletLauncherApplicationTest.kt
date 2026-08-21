package app.olauncher

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = BulletLauncherApplication::class)
class BulletLauncherApplicationTest {

    @Test
    fun onCreate_doesNotCrash() {
        // Robolectric boots BulletLauncherApplication with the configured Sentry DSN.
    }
}
