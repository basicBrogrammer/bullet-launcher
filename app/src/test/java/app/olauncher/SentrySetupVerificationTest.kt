package app.olauncher

import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SentrySetupVerificationTest {

    @Test
    fun captureMessage_reachesConfiguredProject() {
        val dsn = BuildConfig.SENTRY_DSN
        assumeTrue("SENTRY_DSN is not configured for this build", dsn.isNotBlank())

        val context = RuntimeEnvironment.getApplication()
        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.environment = "ci-setup-verification"
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}"
        }

        val eventId = Sentry.captureMessage("Bullet Launcher Sentry setup verification")
        check(eventId != null) { "Sentry did not return an event id" }
        Sentry.flush(5000)
        Sentry.close()
    }
}
