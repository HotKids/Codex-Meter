package me.pipi.codexmeter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAppWidgetManager;
import org.robolectric.shadows.ShadowBuild;
import org.xmlpull.v1.XmlPullParser;

/** Picker eligibility must not remove an installed provider or a placed widget's settings. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31, application = Application.class,
        shadows = ColorOsWidgetAvailabilityTest.ProviderManager.class)
public class ColorOsWidgetAvailabilityTest {
    private static final String HIDDEN_METADATA =
            BuildConfig.APPLICATION_ID + ".COLOROS_HIDDEN_DIAL_PROVIDER_INFO";
    private static final String DEFAULT_METADATA = "android.appwidget.provider";
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private Application app;
    private AppWidgetManager manager;
    private ProviderManager service;
    private AppWidgetProviderInfo dial;
    private AppWidgetProviderInfo card;

    @Before
    public void prepareSyntheticProviders() {
        app = RuntimeEnvironment.getApplication();
        manager = AppWidgetManager.getInstance(app);
        service = Shadow.extract(manager);
        dial = install(CodexDialWidget.class);
        card = install(CodexUsageWidget.class);
        setHost("oppo", "com.android.launcher");
    }

    @Test
    public void stockLauncherHidesOnlyDialAndRepeatedPublicationIsIdempotent() {
        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertTrue(hidden(dial));
        assertFalse(hidden(card));
        assertEquals(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, dial.widgetCategory);
        assertEquals(1, service.updates.size());
        assertEquals(dial.provider, service.updates.get(0).provider);
        assertEquals(HIDDEN_METADATA, service.updates.get(0).metadata);

        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertEquals(1, service.updates.size());
        assertEquals(2, manager.getInstalledProvidersForPackage(app.getPackageName(),
                Process.myUserHandle()).size());
    }

    @Test
    public void otherManufacturerDoesNotHideEvenWithSameLauncherPackage() {
        setHost("Google", "com.android.launcher");

        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertFalse(hidden(dial));
        assertTrue(service.updates.isEmpty());
    }

    @Test
    public void thirdPartyLauncherRestoresDefaultMetadataOnce() {
        ColorOsWidgetAppearance.publishPreviews(app, manager);
        setHost("oppo", "org.example.launcher");

        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertFalse(hidden(dial));
        assertEquals(2, service.updates.size());
        assertNull(service.updates.get(1).metadata);
        ColorOsWidgetAppearance.publishPreviews(app, manager);
        assertEquals(2, service.updates.size());
    }

    @Test
    public void otherManufacturerRestoresPreviouslySelectedHiddenMetadata() {
        ColorOsWidgetAppearance.publishPreviews(app, manager);
        setHost("Samsung", "com.android.launcher");

        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertFalse(hidden(dial));
        assertEquals(2, service.updates.size());
        assertNull(service.updates.get(1).metadata);
    }

    @Test
    public void missingDefaultLauncherRestoresPreviouslySelectedHiddenMetadata() {
        ColorOsWidgetAppearance.publishPreviews(app, manager);
        setHost("oppo", null);

        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertFalse(hidden(dial));
        assertEquals(2, service.updates.size());
        assertNull(service.updates.get(1).metadata);
    }

    @Test
    public void hidingAndRestoringKeepPlacedIdsHostOptionsAndAllSavedSettings() {
        service.addBoundWidget(99, dial);
        service.addBoundWidget(100, dial);
        service.addBoundWidget(42, card);
        Bundle bounds = new Bundle();
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        bounds.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 60);
        manager.updateAppWidgetOptions(99, bounds);
        AppPreferences.saveWidgetOptions(app, 99,
                WidgetOptions.defaults().withVisibleMeters("weekly,next_reset"));
        AppPreferences.saveWidgetTapAction(app, 99, WidgetOptions.TAP_REFRESH);
        Map<String, ?> saved = new HashMap<>(app.getSharedPreferences(
                "codex_meter_settings_v1", Context.MODE_PRIVATE).getAll());
        Map<String, Object> hostOptions = bundleValues(manager.getAppWidgetOptions(99));

        ColorOsWidgetAppearance.publishPreviews(app, manager);
        assertArrayEquals(new int[] {99, 100}, placedIds(dial.provider));
        assertNotNull(manager.getAppWidgetInfo(99));
        setHost("oppo", "org.example.launcher");
        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertArrayEquals(new int[] {99, 100}, placedIds(dial.provider));
        assertArrayEquals(new int[] {42}, placedIds(card.provider));
        assertEquals(hostOptions, bundleValues(manager.getAppWidgetOptions(99)));
        assertEquals(saved, app.getSharedPreferences("codex_meter_settings_v1",
                Context.MODE_PRIVATE).getAll());
        assertEquals("weekly,next_reset",
                AppPreferences.loadWidgetOptions(app, 99).effectiveVisibleMeters());
        assertEquals(WidgetOptions.TAP_REFRESH, AppPreferences.getWidgetTapAction(app, 99));
    }

    @Test
    @Config(sdk = 35)
    public void providerUpdateFailureIsLocalAndNextPublicationRetries() {
        service.refuseUpdates = true;

        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertFalse(hidden(dial));
        assertEquals(1, service.updates.size());
        assertCardPreviewPublished();
        service.refuseUpdates = false;
        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertTrue(hidden(dial));
        assertEquals(2, service.updates.size());
        assertCardPreviewPublished();
    }

    @Test
    @Config(sdk = 35)
    public void providerEnumerationFailureIsLocalAndNextPublicationRetries() {
        service.refuseEnumeration = true;

        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertFalse(hidden(dial));
        assertTrue(service.updates.isEmpty());
        assertCardPreviewPublished();
        service.refuseEnumeration = false;
        ColorOsWidgetAppearance.publishPreviews(app, manager);

        assertTrue(hidden(dial));
        assertEquals(1, service.updates.size());
    }

    @Test
    public void hiddenMetadataPreservesEveryGenericAttributeExceptAddedPickerFlag()
            throws Exception {
        Definition generic = definition(dial.provider, DEFAULT_METADATA);
        Definition hidden = definition(dial.provider, HIDDEN_METADATA);

        assertEquals(generic.attributes, hidden.attributes);
        assertEquals(generic.features | AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER,
                hidden.features);
        assertEquals(AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE, generic.features);
        assertEquals(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, hidden.category);
        assertEquals(WidgetConfigActivity.class.getName(), hidden.attributes.get(ANDROID
                + ":configure"));
    }

    private AppWidgetProviderInfo install(Class<?> provider) {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(app, provider);
        info.widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
        info.widgetFeatures = AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE;
        service.addInstalledProvider(info);
        service.addInstalledProvidersForProfile(Process.myUserHandle(), info);
        return info;
    }

    private void setHost(String manufacturer, String launcher) {
        ShadowBuild.setManufacturer(manufacturer);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        if (launcher == null) {
            shadowOf(app.getPackageManager()).setResolveInfosForIntent(home,
                    Collections.emptyList());
            return;
        }
        ResolveInfo resolver = new ResolveInfo();
        resolver.isDefault = true;
        resolver.activityInfo = new ActivityInfo();
        resolver.activityInfo.packageName = launcher;
        resolver.activityInfo.name = launcher + ".Launcher";
        shadowOf(app.getPackageManager()).setResolveInfosForIntent(home,
                Collections.singletonList(resolver));
    }

    private void assertCardPreviewPublished() {
        RemoteViews preview = manager.getWidgetPreview(card.provider, Process.myUserHandle(),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        assertNotNull(preview);
        assertEquals(R.layout.widget_coloros_card_preview, preview.getLayoutId());
    }

    private int[] placedIds(ComponentName provider) {
        int[] ids = manager.getAppWidgetIds(provider);
        Arrays.sort(ids);
        return ids;
    }

    private static boolean hidden(AppWidgetProviderInfo info) {
        return (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER) != 0;
    }

    private static Map<String, Object> bundleValues(Bundle bundle) {
        Map<String, Object> values = new TreeMap<>();
        for (String key : bundle.keySet()) {
            values.put(key, bundle.get(key));
        }
        return values;
    }

    private static Definition definition(ComponentName provider, String metadata) throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        int resource = app.getPackageManager().getReceiverInfo(provider,
                PackageManager.GET_META_DATA).metaData.getInt(metadata);
        assertTrue("The receiver must declare " + metadata, resource != 0);
        XmlResourceParser parser = app.getResources().getXml(resource);
        try {
            while (parser.next() != XmlPullParser.START_TAG) {
                assertTrue(parser.getEventType() != XmlPullParser.END_DOCUMENT);
            }
            assertEquals("appwidget-provider", parser.getName());
            Map<String, String> attributes = new TreeMap<>();
            for (int index = 0; index < parser.getAttributeCount(); index++) {
                if (!"widgetFeatures".equals(parser.getAttributeName(index))) {
                    attributes.put(parser.getAttributeNamespace(index) + ":"
                            + parser.getAttributeName(index), parser.getAttributeValue(index));
                }
            }
            return new Definition(attributes,
                    parser.getAttributeIntValue(ANDROID, "widgetFeatures", -1),
                    parser.getAttributeIntValue(ANDROID, "widgetCategory", -1));
        } finally {
            parser.close();
        }
    }

    private static final class Definition {
        final Map<String, String> attributes;
        final int features;
        final int category;

        Definition(Map<String, String> attributes, int features, int category) {
            this.attributes = attributes;
            this.features = features;
            this.category = category;
        }
    }

    private static final class Update {
        final ComponentName provider;
        final String metadata;

        Update(ComponentName provider, String metadata) {
            this.provider = provider;
            this.metadata = metadata;
        }
    }

    /** Simulates only the system's declared XML override and keeps normal bound-widget state. */
    @Implements(AppWidgetManager.class)
    public static class ProviderManager extends ShadowAppWidgetManager {
        final List<Update> updates = new ArrayList<>();
        final Map<ComponentName, Map<Integer, RemoteViews>> previews = new HashMap<>();
        boolean refuseUpdates;
        boolean refuseEnumeration;
        int enumerations;

        @Implementation(minSdk = 26)
        protected List<AppWidgetProviderInfo> getInstalledProvidersForPackage(String packageName,
                UserHandle profile) {
            enumerations++;
            if (refuseEnumeration) {
                throw new IllegalStateException("Synthetic unavailable provider enumeration");
            }
            return super.getInstalledProvidersForPackage(packageName, profile);
        }

        @Implementation(minSdk = 28)
        protected void updateAppWidgetProviderInfo(ComponentName provider, String metadata) {
            updates.add(new Update(provider, metadata));
            if (refuseUpdates) {
                throw new IllegalStateException("Synthetic unavailable widget service");
            }
            try {
                Definition definition = definition(provider,
                        metadata == null ? DEFAULT_METADATA : metadata);
                for (AppWidgetProviderInfo info : super.getInstalledProvidersForPackage(
                        provider.getPackageName(), Process.myUserHandle())) {
                    if (provider.equals(info.provider)) {
                        info.widgetFeatures = definition.features;
                        info.widgetCategory = definition.category;
                        return;
                    }
                }
                throw new IllegalArgumentException("Synthetic unknown widget provider");
            } catch (Exception exception) {
                throw new IllegalArgumentException("Synthetic invalid provider metadata", exception);
            }
        }

        @Implementation(minSdk = 35)
        protected boolean setWidgetPreview(ComponentName provider, int category, RemoteViews views) {
            previews.computeIfAbsent(provider, ignored -> new HashMap<>()).put(category, views);
            return true;
        }

        @Implementation(minSdk = 35)
        protected RemoteViews getWidgetPreview(ComponentName provider, UserHandle user, int category) {
            Map<Integer, RemoteViews> categories = previews.get(provider);
            return categories == null ? null : categories.get(category);
        }

        @Implementation(minSdk = 35)
        protected void removeWidgetPreview(ComponentName provider, int category) {
            Map<Integer, RemoteViews> categories = previews.get(provider);
            if (categories != null) {
                categories.remove(category);
            }
        }
    }
}
