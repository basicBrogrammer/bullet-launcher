package app.olauncher

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import app.olauncher.ai.JournalAppFunctions
import io.sentry.android.core.SentryAndroid

class BulletLauncherApplication : Application(), AppFunctionConfiguration.Provider {

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(JournalAppFunctions::class.java) { JournalAppFunctions() }
            .build()

    override fun onCreate() {
        super.onCreate()

        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) {
            return
        }

        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}"
            options.isSendDefaultPii = false
            options.isAttachScreenshot = true
            options.isAttachViewHierarchy = true
            options.isEnableUserInteractionTracing = true
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
        }
    }
}
