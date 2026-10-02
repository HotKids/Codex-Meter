package dev.bennett.codexmeter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Answers Samsung ServiceBox requests from System UI with the lock-screen and AOD views of each
 * Codex Meter page. An empty or missing page id asks for every page.
 */
public final class SamsungLockServiceBoxReceiver extends BroadcastReceiver {
    private static final String TAG = "CodexMeterLock";
    private static final String ACTION_REQUEST =
            "com.samsung.android.intent.action.REQUEST_SERVICEBOX_REMOTEVIEWS";
    private static final String ACTION_RESPONSE =
            "com.samsung.android.intent.action.RESPONSE_SERVICEBOX_REMOTEVIEWS";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String EXTRA_PAGE_ID = "pageId";
    private static final String EXTRA_PACKAGE = "package";
    private static final String EXTRA_SHOW = "show";
    private static final String EXTRA_LOCK_VIEWS = "origin";
    private static final String EXTRA_AOD_VIEWS = "aod";

    private static final Page[] PAGES = {
            new Page("codex_meter_numbers_square",
                    SamsungLockWidgetSupport.Shape.SQUARE, SamsungLockWidgetSupport.Style.NUMBERS),
            new Page("codex_meter_numbers_wide",
                    SamsungLockWidgetSupport.Shape.WIDE, SamsungLockWidgetSupport.Style.NUMBERS),
            new Page("codex_meter_rings_square",
                    SamsungLockWidgetSupport.Shape.SQUARE, SamsungLockWidgetSupport.Style.RINGS),
            new Page("codex_meter_rings_wide",
                    SamsungLockWidgetSupport.Shape.WIDE, SamsungLockWidgetSupport.Style.RINGS),
            new Page("codex_meter_dials_square",
                    SamsungLockWidgetSupport.Shape.SQUARE, SamsungLockWidgetSupport.Style.DIALS),
            new Page("codex_meter_dials_wide",
                    SamsungLockWidgetSupport.Shape.WIDE, SamsungLockWidgetSupport.Style.DIALS),
            new Page("codex_meter_bars_square",
                    SamsungLockWidgetSupport.Shape.SQUARE, SamsungLockWidgetSupport.Style.BARS),
            new Page("codex_meter_bars_wide",
                    SamsungLockWidgetSupport.Shape.WIDE, SamsungLockWidgetSupport.Style.BARS)
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION_REQUEST.equals(intent.getAction())) {
            return;
        }
        String requestedPageId = intent.getStringExtra(EXTRA_PAGE_ID);
        boolean allPages = requestedPageId == null || requestedPageId.isEmpty();
        for (Page page : PAGES) {
            if (allPages || page.id.equals(requestedPageId)) {
                send(context, page);
            }
        }
    }

    private static void send(Context context, Page page) {
        try {
            Intent response = new Intent(ACTION_RESPONSE)
                    .setPackage(SYSTEM_UI)
                    .putExtra(EXTRA_PACKAGE, context.getPackageName())
                    .putExtra(EXTRA_PAGE_ID, page.id)
                    .putExtra(EXTRA_SHOW, true)
                    .putExtra(EXTRA_LOCK_VIEWS,
                            SamsungLockWidgetSupport.buildViews(context, page.shape, page.style))
                    .putExtra(EXTRA_AOD_VIEWS,
                            SamsungLockWidgetSupport.buildViews(context, page.shape, page.style));
            context.sendBroadcast(response);
        } catch (RuntimeException e) {
            Log.w(TAG, "ServiceBox response failed", e);
        }
    }

    private static final class Page {
        final String id;
        final SamsungLockWidgetSupport.Shape shape;
        final SamsungLockWidgetSupport.Style style;

        Page(String id, SamsungLockWidgetSupport.Shape shape,
                SamsungLockWidgetSupport.Style style) {
            this.id = id;
            this.shape = shape;
            this.style = style;
        }
    }
}
