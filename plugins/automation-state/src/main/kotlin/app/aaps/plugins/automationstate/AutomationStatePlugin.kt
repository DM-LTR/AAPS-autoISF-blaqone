package app.aaps.plugins.automationstate

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automationstate.compose.AutomationStateComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.IntKey as MetroIntKey

// Fork-only plugin (no upstream *PluginsListModule to live in); registered directly here, same
// pattern as SafetyPlugin. Global @IntKey ordering block for fork-only plugins: 252-253.
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@MetroIntKey(252)
@SingleIn(AppScope::class)
class AutomationStatePlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    notificationManager: NotificationManager
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .composeContent { AutomationStateComposeContent() }
        .pluginName(TextRef.AndroidRes(R.string.automation_states))
        .description(TextRef.AndroidRes(R.string.description_automation_states)),
    ownPreferences = emptyList(),
    aapsLogger, rh, preferences, notificationManager
) 