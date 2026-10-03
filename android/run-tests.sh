#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
JSON_JAR="${JSON_JAR:-}"

if [[ -z "$JSON_JAR" ]]; then
  for candidate in /mnt/data/toolcache/json-20250517.jar "$ROOT/build/test-libs/json-20250517.jar"; do
    if [[ -f "$candidate" ]]; then JSON_JAR="$candidate"; break; fi
  done
fi

if [[ -z "$JSON_JAR" ]]; then
  CACHE="$ROOT/build/test-libs"
  mkdir -p "$CACHE"
  JSON_JAR="$CACHE/json-20250517.jar"
  if [[ -n "${CAAS_ARTIFACTORY_MAVEN_REGISTRY:-}" && -n "${CAAS_ARTIFACTORY_READER_USERNAME:-}" ]]; then
    curl -fsSL -u "${CAAS_ARTIFACTORY_READER_USERNAME}:${CAAS_ARTIFACTORY_READER_PASSWORD}" \
      "https://${CAAS_ARTIFACTORY_MAVEN_REGISTRY}/org/json/json/20250517/json-20250517.jar" \
      -o "$JSON_JAR"
  else
    curl -fsSL "https://repo1.maven.org/maven2/org/json/json/20250517/json-20250517.jar" -o "$JSON_JAR"
  fi
fi

OUT="$ROOT/build/tests"
rm -rf "$OUT" && mkdir -p "$OUT"

javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageWindow.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageCredits.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageLimit.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/HistorySections.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageSnapshot.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageSample.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageHistory.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsagePace.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/PlanPricing.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageStats.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/AdaptiveRefreshPolicy.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarAutoStart.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarDisplayMode.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarPercentMode.java" \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarCopy.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/PhoneUsageWindows.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageParser.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/CelebrationDetector.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/RateLimitResetCredit.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetCreditsSnapshot.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetCreditExpiryReminder.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarText.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/Pkce.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/JwtClaims.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetOptions.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/OnboardingFlow.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/OAuthBrowserPage.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ReleaseVersion.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/GitHubReleaseSource.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/GitHubRelease.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/GitHubReleaseParser.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateChannel.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ReleaseIntegrity.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ReleaseNotesMarkdown.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ReleaseUpdatePolicy.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateCheckFrequency.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DiagnosticSanitizer.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransfer.java" \
  "$ROOT/tests/ParserSelfTest.java"

java -ea -cp "$OUT:$JSON_JAR" me.pipi.codexmeter.ParserSelfTest

# Source-level release checks.
grep -q 'VERSION_NAME = "0.1.1"' "$ROOT/app/src/main/java/me/pipi/codexmeter/AppConstants.java"
grep -q 'VERSION_CODE = 2' "$ROOT/app/src/main/java/me/pipi/codexmeter/AppConstants.java"
grep -q 'versionName = "0.1.1"' "$ROOT/app/build.gradle.kts"
grep -q 'versionCode = 2' "$ROOT/app/build.gradle.kts"
grep -q 'codex-meter-android/0.1.1' "$ROOT/app/src/main/java/me/pipi/codexmeter/AppConstants.java"
grep -q 'VERSION_NAME="0.1.1"' "$ROOT/build.sh"
WORKFLOW="$ROOT/../.github/workflows/build-apk.yml"
grep -Fq ':app:assembleDebug' "$WORKFLOW"
grep -Fq 'android/app/build/outputs/apk/debug/app-debug.apk' "$WORKFLOW"
! grep -Eq 'ANDROID_SIGNING_PASSWORD|gh release|CodexMeter-Wear-' "$WORKFLOW"
grep -Fq 'CodexMeter-me.pipi.codexmeter-$VERSION_NAME.apk' "$ROOT/build.sh"
grep -Fq '"platforms;android-37.0"' "$WORKFLOW"
grep -q 'HotKids/Codex-Meter/releases?per_page=30' "$ROOT/app/build.gradle.kts" # pragma: allowlist secret
# The fork installs as me.pipi.codexmeter; broadcasts and the internal permission follow the app ID.
grep -q 'applicationId = "me.pipi.codexmeter"' "$ROOT/app/build.gradle.kts"
grep -q 'namespace = "me.pipi.codexmeter"' "$ROOT/app/build.gradle.kts"
grep -q 'BuildConfig.APPLICATION_ID + ".permission.INTERNAL"' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/AppConstants.java"
grep -q '${applicationId}.permission.INTERNAL' "$ROOT/app/src/main/AndroidManifest.xml"
! grep -R -q 'dev\.bennett\.codexmeter\.\(permission\|action\.[A-VX-Z]\)' \
  "$ROOT/app/src/main"
! grep -R -q 'thatjoshguy67/Codex-Meter' \
  "$ROOT/app/src" "$ROOT/app/build.gradle.kts"

# Dashboard reorder + usage-credit / reset-credit auto-hide wiring.
grep -q 'testUsageCreditsAutoHide' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testResetCreditsAutoHide' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testDashboardSectionOrder' "$ROOT/tests/ParserSelfTest.java"
grep -q 'DashboardReorderActivity' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'snapshot.usageCredits.shouldDisplay()' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'shouldShowResetCreditsCard' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'public boolean shouldDisplay()' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetCreditsSnapshot.java"
grep -q 'DashboardSections.resolveOrder' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'ItemTouchHelper' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DashboardReorderActivity.java"
grep -q 'ic_ms_drag_handle' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DashboardReorderActivity.java"
grep -q 'settings_home_display' \
  "$ROOT/app/src/main/res/xml/preferences_settings.xml"
grep -q 'DashboardReorderActivity' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsRootFragment.java"

# Edit-dashboard visibility switches + sortable/hideable usage-history section.
grep -q 'USAGE_HISTORY = "usage_history"' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java"
grep -q 'SwitchCompat' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DashboardReorderActivity.java"
grep -q 'setSectionVisible' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DashboardReorderActivity.java"
grep -q 'DashboardSections.USAGE_HISTORY.equals(key)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'DashboardSections.USAGE_HISTORY' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DashboardReorderActivity.java"
! grep -Eq 'dashboard_additional_limits|dashboard_hidden_sections' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/AppPreferences.java" \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -q 'hidden-section keys round-trip' "$ROOT/tests/ParserSelfTest.java"
# Usage-history analytics: scrubbable charts, per-window insights, and value estimates.
grep -q 'testPlanPricing' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testUsageStats' "$ROOT/tests/ParserSelfTest.java"
grep -q 'setScrubEnabled' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageBurnChartView.java"
grep -q 'requestDisallowInterceptTouchEvent' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageBurnChartView.java"
grep -q 'setOnScrubListener' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageHistoryActivity.java"
grep -q 'PlanPricing.forPlan' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageHistoryActivity.java"
grep -q 'UsageStats.windowBreakdown' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageHistoryActivity.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/PlanPricing.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageStats.java"

# Usage-history declutter: customizable highlights with minimal defaults.
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/HistorySections.java"
grep -q 'testHistorySections' "$ROOT/tests/ParserSelfTest.java"
grep -q 'MENU_CUSTOMIZE' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageHistoryActivity.java"
grep -q 'HistorySections.GUIDE' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageHistoryActivity.java"
grep -q 'isHistorySectionVisible' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/AppPreferences.java"
grep -q 'history_section_overrides' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
# The old always-on explainer card and sample-count summary row must stay gone.
! grep -q 'Burn trends' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageHistoryActivity.java" \
  "$ROOT/app/src/main/res/values/strings_dashboard.xml"
! grep -q 'completed window count' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageHistoryActivity.java" \
  "$ROOT/app/src/main/res/values/strings_dashboard.xml"

# Usage-history charts must be gated on real usage data instead of blank placeholders.
grep -q 'fiveWindow != null && snapshot.fetchedAtMillis > 0L' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'weeklyWindow != null && snapshot.fetchedAtMillis > 0L' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'fiveWindow != null && snapshot.fetchedAtMillis > 0L' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageHistoryActivity.java"
# The usage-history section itself also hides until a window can feed a chart.
grep -q 'snapshot.fiveHour != null || snapshot.weekly != null' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"

# Free-tier monthly Codex window: parsing, dashboard card, history, and the long-window
# fallbacks that keep widgets and the live monitor adapting to subscription changes.
grep -q 'testMonthlyWindow' "$ROOT/tests/ParserSelfTest.java"
grep -q 'MONTHLY = "monthly"' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java"
grep -q 'MONTHLY = "monthly"' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageHistory.java"
grep -q 'public UsageWindow longWindow()' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageSnapshot.java"
grep -q 'monthlyWindow != null && snapshot.fetchedAtMillis > 0L' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'DashboardSections.MONTHLY.equals(key)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'DashboardSections.MONTHLY.equals(key)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DashboardReorderActivity.java"
grep -q 'usage_history_monthly' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/AppPreferences.java"
grep -q 'dashboard_monthly' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -q 'WINDOW_MONTHLY' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsagePace.java"
grep -q 'longWindowIsMonthly' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'meterWindow' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java"
grep -q '<string name="dashboard_section_reset_credits_summary">Hidden automatically when no reset credits are available</string>' \
  "$ROOT/app/src/main/res/values/strings_dashboard.xml"
grep -q 'R.string.dashboard_section_reset_credits_summary' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SharedLabels.java"
grep -q 'SharedLabels.dashboardSectionSummary' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DashboardReorderActivity.java"
# Dashboard auto-hide wiring remains; blank placeholders are widget-only.
grep -q 'snapshot.usageCredits.shouldDisplay()' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'shouldShowResetCreditsCard' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
# Widget meter catalog excludes Spark / additional model limits; config supports drag reorder.
grep -q 'Model-specific additional limits' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java"
grep -q 'available meters exclude model-specific Spark limits' \
  "$ROOT/tests/ParserSelfTest.java"
grep -q 'resolveVisibleForWidget' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java"
grep -q 'resolvedSingleUsageMetric' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/WidgetMeters.java"
grep -q 'ItemTouchHelper' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetConfigActivity.java"
grep -q 'orderedSelection' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetConfigActivity.java"
grep -q 'ic_ms_drag_handle' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetConfigActivity.java"

# Reset/usage-credit dashboard cards use bold in-card titles with left-aligned icon rows,
# matching the other dashboard cards, instead of external One UI separators or centered blocks.
grep -Fq 'Ui.text(this, getString(R.string.dashboard_reset_credits), 18' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -Fq '<string name="dashboard_reset_credits">Reset credits</string>' \
  "$ROOT/app/src/main/res/values/strings_dashboard.xml"
grep -Fq 'Ui.text(this, getString(R.string.dashboard_section_usage_credits_title), 18' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -Fq 'return context.getString(R.string.dashboard_section_usage_credits_title);' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SharedLabels.java"
grep -Fq '<string name="dashboard_section_usage_credits_title">Remaining credits</string>' \
  "$ROOT/app/src/main/res/values/strings_dashboard.xml"
grep -q 'buildIconDetailRow' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'ic_ms_restart_alt' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'ic_ms_account_balance_wallet' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
! grep -q 'Ui.separator' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
python3 - <<PY
import re
from pathlib import Path
root = Path(r"""$ROOT""")
text = (root / "app/src/main/java/me/pipi/codexmeter/ResetCreditActivity.java").read_text()
strings = (root / "app/src/main/res/values/strings_alerts.xml").read_text()
for key, title in (("alerts_reset_credits_section_available", "Available credits"),
                   ("alerts_reset_credits_section_expirations", "Credit expirations")):
    assert re.search(r"Ui\.separator\(this,\s*getString\(R\.string\.%s\)\)" % key, text), key
    assert '<string name="%s">%s</string>' % (key, title) in strings, title
print("Reset credit page keeps its One UI section separators.")
PY
! grep -q 'ic_reset_credit_details' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
# Reset credits is an orderable dashboard section, not a card pinned below the dashboard.
grep -q 'RESET_CREDITS = "reset_credits"' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/DashboardSections.java"
grep -q 'DashboardSections.RESET_CREDITS.equals(key)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'DashboardSections.RESET_CREDITS.equals(key)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/DashboardReorderActivity.java"
! grep -q 'this.content.addView(buildResetCreditsCard())' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"

grep -q 'android.permission.ACCESS_NETWORK_STATE' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android.permission.POST_NOTIFICATIONS' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android.permission.SCHEDULE_EXACT_ALARM' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android:scheme="codexmeter"' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'OnboardingActivity' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'ResetAlertReceiver' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android.permission.POST_PROMOTED_NOTIFICATIONS' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'NowBarActionReceiver' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'MY_PACKAGE_REPLACED' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'android.permission.REQUEST_INSTALL_PACKAGES' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'ReleaseUpdateJobService' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'WidgetRepairJobService' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'UpdateInstallReceiver' "$ROOT/app/src/main/AndroidManifest.xml"
! grep -q 'ReleaseHistoryActivity' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'ReleaseNotesMarkdown.toHtml' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ReleaseNotesUi.java"
grep -q 'ReleaseNotesUi.create' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateActivity.java"
grep -q 'FIRST_IN_APP_UPDATE_VERSION = "0.1"' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ReleaseUpdatePolicy.java"
grep -q 'isIrreversible' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateActivity.java"
grep -q 'R.string.updates_open_on_github' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateActivity.java"
grep -q '<string name="updates_open_on_github">Open on GitHub</string>' \
  "$ROOT/app/src/main/res/values/strings_updates.xml"
# Irreversible-release copy shown on screen keeps the wording ReleaseUpdatePolicy self-tests.
grep -Fq 'Irreversible · Manual GitHub update required' \
  "$ROOT/app/src/main/res/values/strings_updates.xml"
grep -q 'R.string.updates_irreversible_detail' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateActivity.java"
grep -q 'UpdateCheckFrequency' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ReleaseUpdateScheduler.java"
grep -q 'notify_update_available_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_updates.xml"
grep -q 'update_check_interval_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_updates.xml"
grep -q 'settings_update_interval_entries' \
  "$ROOT/app/src/main/res/values/settings_arrays.xml"
grep -q 'EXTRA_START_INSTALL' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateActivity.java"
grep -q 'R.string.updates_notification_action_open), open' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateNotificationManager.java"
grep -q 'R.string.updates_notification_action_update), update' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateNotificationManager.java"
grep -q '<string name="updates_notification_action_open">Open</string>' \
  "$ROOT/app/src/main/res/values/strings_updates.xml"
grep -q '<string name="updates_notification_action_update">Update</string>' \
  "$ROOT/app/src/main/res/values/strings_updates.xml"
grep -q 'UpdateNotificationManager.onReleasesUpdated' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdatePreferences.java"
grep -q 'testUpdateCheckFrequency' "$ROOT/tests/ParserSelfTest.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateCheckFrequency.java"

# Retain release-channel compatibility while the phone enables only stable updates.
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateChannel.java"
grep -q 'testUpdateChannel' "$ROOT/tests/ParserSelfTest.java"
grep -q 'UpdateChannel.selectUpdate' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdatePreferences.java"
grep -q 'UpdateChannel.isReturnToStable' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateActivity.java"
grep -q 'update_channel_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_updates.xml"
grep -q 'update_channel_ui' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsUpdatesFragment.java"
grep -q 'settings_update_channel_entries' \
  "$ROOT/app/src/main/res/values/settings_arrays.xml"
grep -q '"update_channel"' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -Fq 'branches: [main, alpha]' "$WORKFLOW"
! grep -Eq '^[[:space:]]*tags:' "$WORKFLOW"
grep -Fq 'return position == 0;' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsUpdatesFragment.java"
grep -Fq 'return UpdateChannel.STABLE;' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdatePreferences.java"
grep -Fq 'if (!release.prerelease)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdatePreferences.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/UpdateNotificationManager.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransfer.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -q 'testSettingsTransfer' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testDiagnosticSanitizer' "$ROOT/tests/ParserSelfTest.java"
grep -q 'android:name="me.pipi.codexmeter.CodexMeterApplication"' \
  "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'preferences_settings_diagnostics' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsDiagnosticsFragment.java"
grep -q 'application/x-ndjson' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsDiagnosticsFragment.java"
grep -q 'DiagnosticSanitizer.safeUrl' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageApi.java"
grep -q 'export_settings_transfer' \
  "$ROOT/app/src/main/res/xml/preferences_settings_transfer.xml"
grep -q 'import_settings_transfer' \
  "$ROOT/app/src/main/res/xml/preferences_settings_transfer.xml"
grep -q 'transfer_security_notice' \
  "$ROOT/app/src/main/res/xml/preferences_settings_transfer.xml"
grep -q 'SECURITY_WARNING' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransfer.java"
grep -q 'bindTransfer' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferFragment.java"
grep -q 'SettingsTransferStore.collect' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferFragment.java"
grep -q 'SettingsTransferStore.apply' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferFragment.java"
grep -q '@string/settings_transfer_security_title' \
  "$ROOT/app/src/main/res/xml/preferences_settings_transfer.xml"
grep -q '"settings_transfer_security_title">Export file security<' \
  "$ROOT/app/src/main/res/values/strings_settings.xml"
# Dialogs show localized copies of the pure-Java transfer text; the English resources must stay
# identical to what SettingsTransfer writes into files and returns to the self-tests.
python3 - <<PY
import re
import xml.etree.ElementTree as ET
from pathlib import Path
root = Path(r"""$ROOT""")
java = (root / "app/src/main/java/me/pipi/codexmeter/SettingsTransfer.java").read_text()
strings = {node.get("name"): node.text or "" for node in ET.parse(
    root / "app/src/main/res/values/strings_settings.xml").getroot() if node.tag == "string"}
block = java[java.index("SECURITY_WARNING ="):java.index(";", java.index("SECURITY_WARNING ="))]
warning = "".join(re.findall(r'"((?:[^"\\\\]|\\\\.)*)"', block))
assert strings["settings_transfer_security_warning"] == warning, "security warning drifted"
for section in ("app_settings", "notifications", "now_bar", "authentication"):
    for suffix, method in (("", "sectionTitle"), ("_summary", "sectionSummary")):
        body = java[java.index("public static String " + method):]
        constant = "SECTION_" + section.upper()
        english = re.search(constant + r'\.equals\(section\)\) \{\s*return "([^"]*)"', body)
        assert english, (method, section)
        key = "settings_transfer_section_" + section + suffix
        assert strings[key] == english.group(1), key + " drifted from SettingsTransfer"
print("transfer section and security-warning resources match SettingsTransfer.")
PY
grep -q 'material_you' \
  "$ROOT/app/src/main/res/xml/preferences_settings_appearance.xml"
grep -q 'HorizontalRadioPreference' \
  "$ROOT/app/src/main/res/xml/preferences_settings_appearance.xml"
grep -q 'theme_system_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_appearance.xml"
grep -q 'preferences_darkmode_entries_image' \
  "$ROOT/app/src/main/res/values/settings_arrays.xml"
grep -q 'HorizontalRadioPreference theme' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsAppearanceFragment.java"
test -f "$ROOT/app/src/main/res/drawable-xxhdpi/display_help_light_mode.webp"
test -f "$ROOT/app/src/main/res/drawable-xxhdpi/display_help_dark_mode.webp"
grep -q 'isMaterialYouEnabled' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/AppPreferences.java"
grep -q 'AppTheme_MaterialYou' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/Ui.java"
grep -q 'oneUiAccent' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/Ui.java"
grep -q 'oneui_primary' \
  "$ROOT/app/src/main/res/values/colors.xml"
grep -q '#0381FE' \
  "$ROOT/app/src/main/res/values/colors.xml"
grep -q 'material you preference restored' "$ROOT/tests/ParserSelfTest.java"
grep -q 'widgetOptionsFromJson(widget,' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -q 'requireLeadTimes' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -q 'applyNowBar && NowBarManager.isActive' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -q 'AppPreferences.setLastError(app, message)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -q 'partial widget keeps current theme' "$ROOT/tests/ParserSelfTest.java"
grep -q 'malformed lead times rejected' "$ROOT/tests/ParserSelfTest.java"
grep -q 'non-numeric lead time element rejected' "$ROOT/tests/ParserSelfTest.java"
grep -q 'parseLeadTimeEntry' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransfer.java"
python3 - <<PY
from pathlib import Path
text = (Path(r"""$ROOT""") / "app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java").read_text()
start = text.index("private static void applyNotifications")
end = text.index("private static void applyNowBar", start)
block = text[start:end]
assert "SettingsTransfer.requireLeadTimes" in block
assert block.index("SettingsTransfer.requireLeadTimes") < block.index("ResetAlertPreferences.save")
print("notification import validates lead times before saving.")
PY
grep -q 'com.samsung.android.support.ongoing_activity' "$ROOT/app/src/main/AndroidManifest.xml"

grep -q 'app:expanded="true"' "$ROOT/app/src/main/res/layout/activity_settings.xml"
grep -q 'app:expandable="true"' "$ROOT/app/src/main/res/layout/activity_settings.xml"
grep -q 'Ui.configureReachToolbar(toolbar, pageTitle(page), true);' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsActivity.java"
grep -q 'toolbar.setExpandable(false);' "$ROOT/app/src/main/java/me/pipi/codexmeter/Ui.java"
grep -q 'toolbar.setExpandable(true);' "$ROOT/app/src/main/java/me/pipi/codexmeter/Ui.java"
grep -q 'toolbar.setExpanded(true, false);' "$ROOT/app/src/main/java/me/pipi/codexmeter/Ui.java"

test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetAlertPreferences.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetAlertScheduler.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetAlertReceiver.java"
grep -q 'scheduleFromSnapshot' "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetAlertScheduler.java"
grep -q 'POST_NOTIFICATIONS' "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsPageFragment.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetCreditExpiryScheduler.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetCreditExpiryReceiver.java"
grep -q 'ResetCreditExpiryReceiver' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'reset_credit_expiry_times_ui' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsNotificationsFragment.java"
grep -q 'getString(R.string.alerts_use_reset_action), useReset' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetNotificationManager.java"
grep -Fq '<string name="alerts_use_reset_action">Use reset credit</string>' \
  "$ROOT/app/src/main/res/values/strings_alerts.xml"
grep -q 'EXTRA_PROMPT_USE_RESET' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/ResetCreditActivity.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarActionReceiver.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarPreferences.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarAutoStart.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarDisplayMode.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarPercentMode.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/NowBarCopy.java"
# The capsule keeps upstream copy; expanded chips and bodies use localized phone resources.
grep -q 'NowBarCopy.focusCriticalText' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'NowBarText.limitText' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'NowBarText.chipExpandedText' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'testNowBarTextMatchesNowBarCopy' "$ROOT/tests/ParserSelfTest.java"
grep -q 'testNowBarCopy' "$ROOT/tests/ParserSelfTest.java"
grep -q 'exhausted five-hour focus shows hours until natural reset' \
  "$ROOT/tests/ParserSelfTest.java"
grep -q 'Build.VERSION.SDK_INT >= 36' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'codex_live_monitor_v2' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'NotificationManager.IMPORTANCE_DEFAULT' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'setSmallIcon(R.drawable.ic_notification)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'ic_now_bar_progress_dot' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'R.mipmap.ic_launcher' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'themeAdaptiveCodexLogo' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'ic_codex_logo_dark' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
test -f "$ROOT/app/src/main/res/drawable/ic_now_bar_progress_dot.xml"
test -f "$ROOT/app/src/main/res/drawable/ic_codex_logo.xml"
test -f "$ROOT/app/src/main/res/drawable/ic_codex_logo_dark.xml"
test -f "$ROOT/app/src/main/res/drawable/ic_codex_logo_on_accent.xml"
# Do not rely on night-qualified logos for Samsung extras (SystemUI may mismatch).
! test -f "$ROOT/app/src/main/res/drawable-night/ic_codex_logo.xml"
# Progress tracker must be a plain circle, not the brand glyph.
grep -q 'M12,2c5.523,0 10,4.477 10,10' \
  "$ROOT/app/src/main/res/drawable/ic_now_bar_progress_dot.xml"
# Codex logo vectors must stay transparent (no baked white square background).
! grep -q 'android:pathData="M19.503 0H4.496' \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo.xml" \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo_dark.xml" \
  "$ROOT/app/src/main/res/drawable/ic_notification.xml" \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo_on_accent.xml"
# Keep SVG arc flags explicitly separated so SystemUI's VectorDrawable parser can load them.
grep -q 'android:pathData="M 8.086,0.457 a 6.105,6.105 0 0,1' \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo.xml" \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo_dark.xml" \
  "$ROOT/app/src/main/res/drawable/ic_notification.xml" \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo_on_accent.xml"
! grep -q 'android:pathData="M8.086.457' \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo.xml" \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo_dark.xml" \
  "$ROOT/app/src/main/res/drawable/ic_notification.xml" \
  "$ROOT/app/src/main/res/drawable/ic_codex_logo_on_accent.xml"
grep -q 'fillType="evenOdd"' \
  "$ROOT/app/src/main/res/drawable/ic_notification.xml"
grep -q '#435F87' "$ROOT/app/src/main/res/drawable/ic_codex_logo.xml"
grep -q '#B5CDFA' "$ROOT/app/src/main/res/drawable/ic_codex_logo_dark.xml"
grep -q 'android.ongoingActivityNoti.' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'applySamsungCompatibility' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'NowBarDisplayMode.ANDROID_LIVE_UPDATE' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'setDeleteIntent(stopIntent)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'FLAG_PROMOTED_ONGOING' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'legacyColorizedFallback' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'builder.setColorized(true)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'NowBarManager.isPromoted' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsNowBarFragment.java"
grep -q 'hasStoredActiveState' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'NowBarManager.onUsageUpdated' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageApi.java"
grep -q 'maybeAutoStart' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'now_bar_auto_start_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_now_bar.xml"
grep -q 'now_bar_threshold_ui' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsNowBarFragment.java"
grep -q 'NowBarPreferences.isAutoStartEnabled' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsNowBarFragment.java"
grep -q 'markSuppressedUntil' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'NowBarAutoStart.shouldStart' \
  "$ROOT/tests/ParserSelfTest.java"
grep -q 'NowBarDisplayMode.resolve' \
  "$ROOT/tests/ParserSelfTest.java"
grep -q 'NowBarPercentMode.resolveFocus' \
  "$ROOT/tests/ParserSelfTest.java"
grep -q 'NowBarPercentMode.triggeredFocus' \
  "$ROOT/tests/ParserSelfTest.java"
grep -q 'NowBarPercentMode.focusForSettingsChange' \
  "$ROOT/tests/ParserSelfTest.java"
grep -q 'KEY_AUTO_TRIGGER_FOCUS' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'sessionAutoTriggerFocus' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'focusForSettingsChange' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
# applyPercentModeChange must stop the monitor when post() fails (no zombie active state).
python3 - <<PY
from pathlib import Path
root = Path(r"""$ROOT""")
text = (root / "app/src/main/java/me/pipi/codexmeter/NowBarManager.java").read_text()
start = text.index("public static synchronized boolean applyPercentModeChange")
end = text.index("public static synchronized void stop(Context context)", start)
block = text[start:end]
assert "if (post(context, snapshot, until, preview)) return true;" in block
assert "stop(context, false);" in block
assert "return false;" in block
print("applyPercentModeChange stops on post failure.")
PY
grep -q 'now_bar_display_mode_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_now_bar.xml"
grep -q 'now_bar_percent_mode_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_now_bar.xml"
grep -q 'NowBarPreferences.getPercentMode' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'applyPercentModeChange' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsNowBarFragment.java"
grep -q 'KEY_FOCUS_METRIC' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'R.string.alerts_now_bar_samsung_help)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsNowBarFragment.java"
grep -q 'Live notifications for all apps' \
  "$ROOT/app/src/main/res/values/strings_alerts.xml"

grep -R -q '<Chronometer' "$ROOT/app/src/main/res/layout/widget_lock_"*.xml
grep -q 'setChronometerCountDown' "$ROOT/app/src/main/java/me/pipi/codexmeter/SamsungLockWidgetSupport.java"
grep -q 'show_countdown' "$ROOT/app/src/main/java/me/pipi/codexmeter/AppPreferences.java"
grep -q 'R.string.dashboard_lock_show_countdown' "$ROOT/app/src/main/java/me/pipi/codexmeter/LockWidgetConfigActivity.java"
grep -q '<string name="dashboard_lock_show_countdown">Show live time until reset</string>' \
  "$ROOT/app/src/main/res/values/strings_dashboard.xml"

for style in rings dials bars; do
  for shape in square wide; do
    test -f "$ROOT/app/src/main/res/layout/widget_lock_${style}_${shape}.xml"
    test -f "$ROOT/app/src/main/res/xml/samsung_lock_${style}_${shape}_info.xml"
    test -f "$ROOT/app/src/main/res/xml/samsung_lock_${style}_${shape}_oneui_info.xml"
  done
done

for provider in \
  SamsungLockSquareWidget SamsungLockWideWidget \
  SamsungLockRingsSquareWidget SamsungLockRingsWideWidget \
  SamsungLockDialsSquareWidget SamsungLockDialsWideWidget \
  SamsungLockBarsSquareWidget SamsungLockBarsWideWidget \
  SamsungLockFiveHourWidget SamsungLockWeeklyWidget; do
  grep -q "android:name=\"me.pipi.codexmeter.$provider\"" \
    "$ROOT/app/src/main/AndroidManifest.xml"
  grep -q "$provider.class" \
    "$ROOT/app/src/main/java/me/pipi/codexmeter/SamsungLockWidgetSupport.java"
done

grep -R -q 'android:widgetCategory="0x2000"' "$ROOT/app/src/main/res/xml/samsung_lock_"*_info.xml
grep -R -q 'app:widgetStyle="monotone"' "$ROOT/app/src/main/res/xml/samsung_lock_"*_info.xml
! grep -R -q 'com.samsung.systemui.permission.FACE_WIDGET' "$ROOT/app/src/main"

grep -q 'RESET_CREDITS_CONSUME_URL' "$ROOT/app/src/main/java/me/pipi/codexmeter/AppConstants.java"
grep -q 'ResetCreditActivity' "$ROOT/app/src/main/AndroidManifest.xml"
grep -q 'showResetAction' "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetOptions.java"
grep -q 'OPACITY_LEVELS = {56, 88, 100}' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetOptions.java"
grep -q 'widget_background' "$ROOT/app/src/main/res/values/strings.xml"
grep -q 'backgroundSwitch' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetConfigActivity.java"
grep -q 'RadioItemViewGroup' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/OneUiChoiceDialog.java"
# Phone widgets combine reset credits with next reset, keep one-row dials and Clear cards.
grep -q 'MaterialCardRenderer.build' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetRenderer.java"
grep -q 'DialWidgetRenderer.build' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetRenderer.java"
grep -q 'WidgetOptions.availableMeterKeys' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetConfigActivity.java"
grep -q 'this.cardStyle = CARD_CLEAR' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetOptions.java"
grep -q 'android:src="@drawable/ic_notification"' \
  "$ROOT/app/src/main/res/layout/widget_material.xml"
! grep -R -q '@layout/widget_card' "$ROOT/app/src/main/res/xml"*
grep -q 'card_style' "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransfer.java"
# Home and lock config drive the shared meter catalog for per-meter visibility.
grep -q 'WidgetMeters' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetConfigActivity.java"
grep -q 'WidgetMeters' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/LockWidgetConfigActivity.java"
grep -q 'selectedMeters' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/LockWidgetConfigActivity.java"
# Renderer binds each widget's ordered window selection.
grep -q 'selectedKeys' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetRenderer.java"
! grep -q 'ListPopupWindow' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/WidgetConfigActivity.java"
grep -q 'android:max="2"' "$ROOT/app/src/main/res/layout/view_widget_opacity.xml"
! grep -q 'opacity_tick_3' "$ROOT/app/src/main/res/layout/view_widget_opacity.xml"
grep -q 'One UI widget opacity uses three levels' "$ROOT/tests/ParserSelfTest.java"
grep -q 'dev.oneuiproject.oneui.widget.CardItemView' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/OnboardingActivity.java"
grep -q 'Ui.nativePrimaryButton' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/OnboardingActivity.java"
grep -q 'Ui.addSpacer(this.content, 20)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/OnboardingActivity.java"
python3 - <<PY
from pathlib import Path
text = (Path(r"""$ROOT""") / "app/src/main/res/xml/preferences_settings.xml").read_text()
about = text.index('android:key="about_codex_meter"')
assert 'android:summary="@string/settings_about_summary"' in text[about:], "missing About summary"
assert text.find('InsetPreferenceCategory', about) != -1, "missing bottom inset after About"
assert 'app:height="28dp"' in text[about:], "bottom settings inset should be 28dp"
print("settings list keeps About summary and bottom padding.")
PY
grep -q 'OAuthBrowserPage.render' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/OAuthService.java"
# The browser page text comes from the app's resources in its current language.
grep -q 'R.string.auth_browser_success_title' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/OAuthService.java"
grep -Fq '>You’re connected<' "$ROOT/app/src/main/res/values/strings_auth.xml"
grep -q 'titlePaint.setColor(foreground);' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageWaveView.java"
grep -q 'resetPaint.setColor(foreground);' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageWaveView.java"
grep -q 'showsResetCountdown' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageWindow.java"
grep -q 'testResetCountdownFollowsApiTimeline' "$ROOT/tests/ParserSelfTest.java"
! grep -q 'remainingPercent() <= 99' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsageWindow.java"
grep -q 'testUsagePace' "$ROOT/tests/ParserSelfTest.java"
grep -q 'UsagePace.mostAcceleratedWindow' "$ROOT/tests/ParserSelfTest.java"
test -f "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsagePace.java"
test -f "$ROOT/app/src/main/java/me/pipi/codexmeter/UsagePacePreferences.java"
test -f "$ROOT/app/src/debug/java/me/pipi/codexmeter/UsagePaceDemoActivity.java"
grep -q 'UsagePaceDemoActivity' "$ROOT/app/src/debug/AndroidManifest.xml"
grep -q 'usage_pace_enabled_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_refresh_usage.xml"
grep -q 'usage_pace_sensitivity_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_refresh_usage.xml"
grep -q '<item>off</item>' \
  "$ROOT/app/src/main/res/values/settings_arrays.xml"
grep -q 'public static final String OFF = "off"' \
  "$ROOT/shared/src/main/java/dev/bennett/codexmeter/UsagePace.java"
grep -q 'areWarningsEnabled' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsagePacePreferences.java"
grep -q 'now_bar_accelerated_ui' \
  "$ROOT/app/src/main/res/xml/preferences_settings_now_bar.xml"
grep -q 'accelerated_enabled' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsTransferStore.java"
grep -q 'START_ACCELERATED' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/NowBarManager.java"
grep -q 'Color.rgb(230, 91, 23)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/Ui.java"
grep -q 'WARNING_WAVE_DURATION_MS = 950L' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageWaveView.java"
grep -q '"· " + pace' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageWaveView.java"
grep -q 'card.setMinimumHeight(Ui.dp(this, 103.0f))' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/MainActivity.java"
grep -q 'onPaceSettingsChanged' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/SettingsRefreshUsageFragment.java"
grep -q '!usageWindow.showsResetCountdown()' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageFormat.java"
! grep -q 'resetPaint.setColor(Ui.secondaryText(dark));' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageWaveView.java"
! grep -q 'titlePaint.setColor(0xFF000000)' \
  "$ROOT/app/src/main/java/me/pipi/codexmeter/UsageWaveView.java"

# Vendored SESL transitive deps keep phone Android CI working without GitHub Packages auth.
test -f "$ROOT/vendor/m2/sesl/androidx/appcompat/appcompat/1.7.1+1.0.21-sesl8+rev8/appcompat-1.7.1+1.0.21-sesl8+rev8.aar"
test -f "$ROOT/vendor/m2/sesl/com/google/android/material/material/1.12.0+1.0.32-sesl8+rev3/material-1.12.0+1.0.32-sesl8+rev3.aar"
# Phone-only builds must not restore the removed watch module or Data Layer dependency.
test ! -d "$ROOT/wear"
! grep -Fq 'include(":wear")' "$ROOT/settings.gradle.kts"
grep -Fq ':app:lintRelease' "$ROOT/lint.sh"
! grep -Fq ':wear:' "$ROOT/lint.sh"
! grep -Fq ':wear:' "$ROOT/build.sh"
! grep -Fq 'play-services-wearable' "$ROOT/app/build.gradle.kts"
! grep -Rq 'PhoneWearSync\|PhoneWearListenerService\|com.google.android.gms.wearable' \
  "$ROOT/app/src/main"

echo "Parser, updater, OAuth, onboarding, reset-credit, alert, and widget source checks passed."
