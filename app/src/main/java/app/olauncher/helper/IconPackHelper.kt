package app.olauncher.helper

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(
    val packageName: String,
    val label: String,
)

/**
 * Resolves launcher icons from the system or an installed icon pack (ADW/Nova/GO style).
 */
object IconPackHelper {
    private const val TAG = "IconPackHelper"

    private val THEME_INTENTS = listOf(
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.novalauncher.THEME",
        "org.adw.launcher.icons.ACTION_PICK_ICON",
    )

    @Volatile
    private var cachedPackPackage: String? = null

    @Volatile
    private var componentDrawableMap: Map<String, String> = emptyMap()

    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val packs = linkedMapOf<String, IconPackInfo>()
        for (action in THEME_INTENTS) {
            val intent = Intent(action)
            val resolves = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            for (info in resolves) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) continue
                if (packs.containsKey(pkg)) continue
                val label = info.loadLabel(pm)?.toString() ?: pkg
                packs[pkg] = IconPackInfo(pkg, label)
            }
        }
        return packs.values.sortedBy { it.label.lowercase() }
    }

    fun clearCache() {
        cachedPackPackage = null
        componentDrawableMap = emptyMap()
    }

    fun loadAppIcon(
        context: Context,
        packageName: String,
        activityClassName: String?,
        userHandle: UserHandle,
        iconPackPackage: String?,
    ): Drawable? {
        if (packageName.isBlank()) return null
        val systemIcon = loadSystemIcon(context, packageName, activityClassName, userHandle)

        if (iconPackPackage.isNullOrBlank()) return systemIcon

        val packIcon = loadFromIconPack(
            context,
            iconPackPackage,
            packageName,
            activityClassName,
        )
        return packIcon ?: systemIcon
    }

    private fun loadSystemIcon(
        context: Context,
        packageName: String,
        activityClassName: String?,
        userHandle: UserHandle,
    ): Drawable? {
        return try {
            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activities = launcher.getActivityList(packageName, userHandle)
            val info = if (!activityClassName.isNullOrBlank()) {
                activities.find { it.componentName.className == activityClassName }
                    ?: activities.firstOrNull()
            } else {
                activities.firstOrNull()
            }
            info?.getBadgedIcon(0) ?: context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun loadFromIconPack(
        context: Context,
        iconPackPackage: String,
        appPackage: String,
        activityClassName: String?,
    ): Drawable? {
        return try {
            ensureAppFilterLoaded(context, iconPackPackage)
            val packRes = context.packageManager.getResourcesForApplication(iconPackPackage)
            val keys = buildLookupKeys(appPackage, activityClassName)
            for (key in keys) {
                val drawableName = componentDrawableMap[key] ?: continue
                val drawable = loadPackDrawable(packRes, iconPackPackage, drawableName)
                if (drawable != null) return drawable
            }
            // Calendar-style fallback: try package name as drawable
            loadPackDrawable(packRes, iconPackPackage, appPackage.replace('.', '_'))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon from pack $iconPackPackage", e)
            null
        }
    }

    private fun buildLookupKeys(appPackage: String, activityClassName: String?): List<String> {
        val keys = mutableListOf<String>()
        if (!activityClassName.isNullOrBlank()) {
            keys += "ComponentInfo{$appPackage/$activityClassName}"
            keys += "$appPackage/$activityClassName"
        }
        keys += appPackage
        return keys
    }

    private fun loadPackDrawable(
        packRes: Resources,
        iconPackPackage: String,
        drawableName: String,
    ): Drawable? {
        val id = packRes.getIdentifier(drawableName, "drawable", iconPackPackage)
        if (id == 0) return null
        return try {
            packRes.getDrawable(id, null)
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureAppFilterLoaded(context: Context, iconPackPackage: String) {
        if (cachedPackPackage == iconPackPackage && componentDrawableMap.isNotEmpty()) return
        synchronized(this) {
            if (cachedPackPackage == iconPackPackage && componentDrawableMap.isNotEmpty()) return
            componentDrawableMap = parseAppFilter(context, iconPackPackage)
            cachedPackPackage = iconPackPackage
        }
    }

    private fun parseAppFilter(context: Context, iconPackPackage: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val packRes = context.packageManager.getResourcesForApplication(iconPackPackage)
            val xmlId = packRes.getIdentifier("appfilter", "xml", iconPackPackage)
            if (xmlId == 0) {
                // Some packs store appfilter as raw/asset — try opening as XML resource by name via assets
                try {
                    context.packageManager.getResourcesForApplication(iconPackPackage)
                        .assets.open("appfilter.xml").use { stream ->
                            parseAppFilterStream(stream, map)
                        }
                } catch (_: Exception) {
                    Log.w(TAG, "No appfilter.xml in $iconPackPackage")
                }
                return map
            }
            val parser = packRes.getXml(xmlId)
            parseAppFilterParser(parser, map)
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing appfilter for $iconPackPackage", e)
        }
        return map
    }

    private fun parseAppFilterStream(stream: java.io.InputStream, map: MutableMap<String, String>) {
        val factory = android.util.Xml.newPullParser()
        factory.setInput(stream, null)
        parseAppFilterParser(factory, map)
    }

    private fun parseAppFilterParser(parser: XmlPullParser, map: MutableMap<String, String>) {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                    map[component] = drawable
                    // Also index by package alone for coarse matches
                    val pkg = component.substringAfter("{", "")
                        .substringBefore("/", "")
                        .substringBefore("}", "")
                    if (pkg.isNotBlank() && !map.containsKey(pkg)) {
                        map[pkg] = drawable
                    }
                }
            }
            event = parser.next()
        }
    }
}
