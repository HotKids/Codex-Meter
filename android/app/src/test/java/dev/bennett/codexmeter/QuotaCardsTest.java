package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class, qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class QuotaCardsTest {
    public static class TestApp extends Application {
        @Override public void onCreate() { super.onCreate(); AppText.initialize(this); }
    }
    private Context context;
    static final long NOW = 1_790_942_400_000L, RESET = NOW + 3 * 86_400_000L;
    private UsageWindow weekly, five;
    private UsageSnapshot pro, plus;
    private WidgetOptions options;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        weekly = new UsageWindow(64, 604800, 0, RESET / 1000);
        five = new UsageWindow(20, 18000, 0, (NOW + 7200000) / 1000);
        pro = new UsageSnapshot("pro", true, false, null, weekly, NOW);
        plus = new UsageSnapshot("plus", true, false, five, weekly, NOW);
        options = WidgetOptions.defaults();
    }

    static UsageSnapshot multiWindowSnapshot() {
        UsageWindow five = new UsageWindow(20,18000,0,(NOW+7200000)/1000);
        UsageWindow week = new UsageWindow(64,604800,0,RESET/1000);
        return new UsageSnapshot("pro",true,false,five,week,Arrays.asList(
                new UsageLimit("spark","Spark","spark",true,false,
                        new UsageWindow(5,18000,0,(NOW+3600000)/1000),
                        new UsageWindow(50,604800,0,RESET/1000)),
                new UsageLimit("extra","Extra","extra",true,false,five,null)),null,2,NOW);
    }

    @Test public void choicesRetainFourBuiltinsExtrasAndAllSavedSelections() {
        List<String> all = QuotaCardOptions.available(multiWindowSnapshot());
        assertEquals(7,all.size());
        assertEquals(Arrays.asList("five_hour", "weekly", "next_reset", "reset_credits"), all.subList(0,4));
        assertTrue(QuotaCardOptions.canEnable(all.subList(0,2),all.get(2)));
        assertTrue(QuotaCardOptions.canEnable(all.subList(0,3),all.get(3)));
        assertTrue(QuotaCardOptions.canEnable(all.subList(0,4),all.get(4)));
        assertEquals(all,QuotaCardOptions.resolve(WidgetMeters.serialize(all)));
        assertEquals(all.subList(0,4),QuotaCardOptions.available(pro));
        assertEquals(all.subList(0,4),QuotaCardOptions.available(plus));
    }

    @Test public void selectedNextResetOccupiesItsOwnOrderedSlot() throws Exception {
        View view = render(options.withVisibleMeters("weekly,next_reset"),pro,null,true,"",136,196);
        assertEquals("36%",text(view,R.id.quota_value_0));
        assertEquals("下次重置",text(view,R.id.quota_title_1));
        assertEquals("3天0小时",text(view,R.id.quota_value_1));
        assertEquals(View.VISIBLE,view.findViewById(R.id.quota_card_1).getVisibility());
        assertEquals(View.GONE,view.findViewById(R.id.quota_hero_position).getVisibility());
        assertEquals(View.GONE,view.findViewById(R.id.quota_optional_position).getVisibility());
        save(view,"reference-pro-136x196");
    }

    @Test public void proAndPlusUseTheirActualWindowCount() throws Exception {
        for (int[] size : new int[][]{{158,158},{136,196},{338,158}}) {
            View single = render(options.withVisibleMeters("weekly"),pro,null,true,"",size[0],size[1]);
            assertEquals(View.VISIBLE,single.findViewById(R.id.quota_hero_position).getVisibility());
            assertEquals("36%",text(single,R.id.quota_hero_value));
            assertContentFits(single);
            save(single,"reference-single-"+size[0]+"x"+size[1]);
            View dual = render(options,plus,null,true,"",size[0],size[1]);
            assertEquals(View.GONE,dual.findViewById(R.id.quota_hero_position).getVisibility());
            assertEquals("80%",text(dual,R.id.quota_value_0));
            assertEquals("36%",text(dual,R.id.quota_value_1));
            assertNoRowOverlap(dual);
            assertContentFits(dual);
            save(dual,"reference-dual-"+size[0]+"x"+size[1]);
        }
    }

    @Test public void plusCanChooseOnlyWeeklyOrOnlyFiveHour() {
        for (String key : Arrays.asList("weekly","five_hour")) {
            View view=render(options.withVisibleMeters(key),plus,null,true,"",158,158);
            assertEquals(key.equals("weekly") ? "36%" : "80%",text(view,R.id.quota_hero_value));
            assertEquals(View.GONE,view.findViewById(R.id.quota_card_1).getVisibility());
        }
    }

    @Test public void threeAndFourWindowsUseReferenceStackedRows() throws Exception {
        UsageSnapshot snapshot=multiWindowSnapshot();
        UsageLimit spark = snapshot.additionalLimits.get(0);
        List<String> all=Arrays.asList("five_hour", "weekly", WidgetMeters.limitPrimaryKey(spark),
                WidgetMeters.limitSecondaryKey(spark));
        for(int count:new int[]{3,4}) {
            String csv=WidgetMeters.serialize(all.subList(0,count));
            View view=render(options.withVisibleMeters(csv),snapshot,null,true,"",338,158);
            assertEquals("80%",text(view,R.id.quota_inline_value_0));
            assertEquals("95%",text(view,R.id.quota_inline_value_2));
            assertEquals(View.VISIBLE,view.findViewById(R.id.quota_inline_position_2).getVisibility());
            assertEquals(View.GONE,view.findViewById(R.id.quota_optional_position).getVisibility());
            if(count==4) assertEquals("50%",text(view,R.id.quota_inline_value_3));
            assertContentFits(view);
            save(view,"reference-"+count+"-windows");
            View small=render(options.withVisibleMeters(csv),snapshot,null,true,"",158,158);
            assertEquals(View.VISIBLE,small.findViewById(R.id.quota_card_1).getVisibility());
            assertEquals(View.GONE,small.findViewById(R.id.quota_card_2).getVisibility());
            assertContentFits(small);
        }
    }

    @Test public void resetCreditsOnlyAppearAsSelectedSlotsIncludingZero() throws Exception {
        for(UsageSnapshot snapshot:new UsageSnapshot[]{pro,plus}) for(int[] size:new int[][]{{136,196},{158,158},{338,158}}) {
            View view=render(options.withVisibleMeters("reset_credits,weekly"),snapshot,
                    ResetCreditsSnapshot.summary(2,NOW),true,"",size[0],size[1]);
            assertEquals("重置券",text(view,R.id.quota_title_0));
            assertEquals("2",text(view,R.id.quota_value_0));
            assertEquals("36%",text(view,R.id.quota_value_1));
            assertEquals(View.GONE,view.findViewById(R.id.quota_optional_position).getVisibility());
            assertContentFits(view);
            save(view,"reference-credits-"+snapshot.planType+"-"+size[0]+"x"+size[1]);
        }
        View empty=render(options.withVisibleMeters("reset_credits"),pro,ResetCreditsSnapshot.summary(0,NOW),true,"",136,196);
        assertEquals("0",text(empty,R.id.quota_hero_value));
        assertEquals(View.GONE,empty.findViewById(R.id.quota_hero_caption).getVisibility());
        assertEquals(View.GONE,empty.findViewById(R.id.quota_optional_position).getVisibility());
        View off=render(options.withVisibleMeters("weekly"),pro,ResetCreditsSnapshot.summary(2,NOW),true,"",136,196);
        assertEquals("36%",text(off,R.id.quota_hero_value));
        assertEquals(View.GONE,off.findViewById(R.id.quota_optional_position).getVisibility());
    }

    @Test public void sourceTypographyAndOffsetsArePreserved() {
        View small=render(options.withVisibleMeters("weekly"),pro,null,true,"",158,158);
        assertEquals(36,bounds(small,R.id.quota_title_0).top);
        assertEquals(55,bounds(small,R.id.quota_hero_value).top);
        assertEquals(96,bounds(small,R.id.quota_graphic_0).top);
        assertEquals(32,((TextView)small.findViewById(R.id.quota_hero_value)).getTextSize(),0.01);
        View medium=render(options.withVisibleMeters("weekly"),pro,null,true,"",338,158);
        assertEquals(39,bounds(medium,R.id.quota_title_0).top);
        assertEquals(62,bounds(medium,R.id.quota_hero_value).top);
        assertEquals(111,bounds(medium,R.id.quota_graphic_0).top);
        assertEquals(40,((TextView)medium.findViewById(R.id.quota_hero_value)).getTextSize(),0.01);
    }

    @Test public void realCreditExpiryFitsTheNarrowReferenceMetadataRow() throws Exception {
        ResetCreditsSnapshot credits = new ResetCreditsSnapshot(2, Arrays.asList(
                new RateLimitResetCredit("fixture", "weekly", "available", NOW, RESET, "", "")), NOW);
        for (UsageSnapshot snapshot : new UsageSnapshot[]{pro, plus}) {
            View view = render(options.withVisibleMeters("reset_credits"), snapshot, credits, true, "", 136, 196);
            assertContentFits(view);
            assertEquals("2",text(view,R.id.quota_hero_value));
            assertTrue(text(view, R.id.quota_meta_value).contains("月"));
            assertEquals(View.GONE,view.findViewById(R.id.quota_optional_position).getVisibility());
            save(view, "reference-expiry-" + snapshot.planType);
        }
    }

    @Test @Config(qualifiers = "zh-rCN-xhdpi") public void densePhoneRendersAtActualPixelDensity() throws Exception {
        for (UsageSnapshot snapshot : new UsageSnapshot[]{pro, plus}) {
            View view = QuotaCardRenderer.build(context, 42, options, 136, 196,
                    snapshot, null, null, true, "", NOW).apply(context, new FrameLayout(context));
            measure(view, 272, 392);
            assertContentFits(view);
            save(view, "phone-" + snapshot.planType + "-136x196");
        }
    }

    @Test public void resetFormattingMatchesReferenceBoundaryCases() {
        assertEquals("3天0小时",QuotaCardRenderer.resetText(context,RESET,NOW,false));
        assertEquals("2小时0分后重置",QuotaCardRenderer.resetText(context,NOW+7200000,NOW,true));
        assertEquals("即将重置",QuotaCardRenderer.resetText(context,NOW+59000,NOW,true));
        assertEquals("已重置",QuotaCardRenderer.resetText(context,NOW-1,NOW,false));
        assertEquals("—",QuotaCardRenderer.resetText(context,0,NOW,true));
        UsageSnapshot expired=new UsageSnapshot("pro",true,false,null,new UsageWindow(90,604800,0,(NOW-1000)/1000),NOW-60000);
        View view=render(options.withVisibleMeters("weekly"),expired,null,true,"",136,196);
        assertEquals("10%",text(view,R.id.quota_hero_value));
        assertEquals("已重置",text(view,R.id.quota_meta_value));
    }

    @Test public void signedOutDoesNotLeakCachedPercentagesOrCredits() {
        View view=render(options.withVisibleMeters("weekly"),pro,ResetCreditsSnapshot.summary(2,NOW),false,"",136,196);
        assertEquals("—",text(view,R.id.quota_hero_value));
        assertEquals(View.GONE,view.findViewById(R.id.quota_optional_position).getVisibility());
        assertEquals("打开应用登录",text(view,R.id.quota_status));
        assertEquals("Codex",view.findViewById(R.id.quota_badge).getContentDescription());
    }

    @Test public void refreshFailureKeepsCacheAndShowsErrorInSourcePosition() {
        View small=render(options.withVisibleMeters("weekly"),pro,null,true,"offline",136,196);
        assertEquals("36%",text(small,R.id.quota_hero_value));
        assertEquals("刷新失败",text(small,R.id.quota_status));
        View medium=render(options.withVisibleMeters("weekly"),pro,null,true,"offline",338,158);
        assertEquals("刷新失败 · 显示上次数据",text(medium,R.id.quota_error));
        assertTrue(text(medium,R.id.quota_status).endsWith("刷新"));
    }

    @Test public void rootOpensOriginalAppAndReferenceTimestampHasNoRefreshAction() {
        View view=render(options.withVisibleMeters("weekly"),pro,null,true,"",136,196);
        view.performClick();
        Intent open=shadowOf((Application)context).getNextStartedActivity();
        assertEquals(MainActivity.class.getName(),open.getComponent().getClassName());
        assertFalse(open.hasExtra("account_detail"));
        assertFalse(view.findViewById(R.id.quota_status).performClick());
        assertTrue(shadowOf((Application)context).getBroadcastIntents().stream()
                .noneMatch(i -> AppConstants.ACTION_REFRESH_WIDGET.equals(i.getAction())));
        assertNull(shadowOf((Application)context).getNextStartedActivity());
    }

    @Test public void twoByOneKeepsUpstreamNativeDialsAndEnglish() throws Exception {
        View view=render(options,plus,null,true,"",180,100);
        assertEquals("80%",text(view,R.id.primary_samsung_value));
        assertEquals("36%",text(view,R.id.secondary_samsung_value));
        assertEquals(15,((TextView)view.findViewById(R.id.primary_samsung_value)).getTextSize(),0.01); // XML 14.5sp rounds to 15px at mdpi.
        assertEquals(56,view.findViewById(R.id.primary_samsung_progress).getWidth());
        assertEquals("Refresh",view.findViewById(R.id.refresh_button).getContentDescription());
        assertEnglish(view);
        save(view,"upstream-2x1-english");
    }

    @Test public void builtinMissingUsageKeepsItsSelectedPosition() {
        View view = render(options.withVisibleMeters("five_hour,weekly"),pro,null,true,"",158,158);
        assertEquals("—",text(view,R.id.quota_value_0));
        assertEquals("36%",text(view,R.id.quota_value_1));
        assertEquals(View.VISIBLE,view.findViewById(R.id.quota_card_0).getVisibility());
        assertEquals(View.VISIBLE,view.findViewById(R.id.quota_card_1).getVisibility());
    }

    @Test public void orderedHelpersUseOriginalEnglishDialValuesIconsAndProgress() throws Exception {
        for (String csv : Arrays.asList("next_reset,reset_credits", "reset_credits,next_reset")) {
            View view = render(options.withVisibleMeters(csv),plus,ResetCreditsSnapshot.summary(2,NOW),true,"",180,100);
            boolean resetFirst = csv.startsWith("next_reset");
            assertEquals(resetFirst ? "2h 0m" : "2",text(view,R.id.primary_samsung_value));
            assertEquals(resetFirst ? "2" : "2h 0m",text(view,R.id.secondary_samsung_value));
            assertEquals(resetFirst ? 40 : 50,progress(view,R.id.primary_samsung_progress));
            assertEquals(resetFirst ? 50 : 40,progress(view,R.id.secondary_samsung_progress));
            assertEquals(resetFirst ? "Reset" : "Credits",view.findViewById(R.id.primary_samsung_icon).getContentDescription());
            assertEquals(resetFirst ? R.drawable.ic_oui_alarm : R.drawable.ic_oui_refresh,
                    shadowOf(((ImageView)view.findViewById(R.id.primary_samsung_icon)).getDrawable()).getCreatedFromResId());
            assertEquals(resetFirst ? R.drawable.ic_oui_refresh : R.drawable.ic_oui_alarm,
                    shadowOf(((ImageView)view.findViewById(R.id.secondary_samsung_icon)).getDrawable()).getCreatedFromResId());
            assertEquals(15,((TextView)view.findViewById(R.id.primary_samsung_value)).getTextSize(),.01);
            assertEquals(56,view.findViewById(R.id.primary_samsung_progress).getWidth());
            assertEnglish(view);
            save(view,"upstream-2x1-"+(resetFirst ? "reset-credits" : "credits-reset"));
        }
    }

    @Test public void creditHelperKeepsRawCountsAndOriginalFourCreditScale() {
        for (int count : new int[]{0,1,2,3,4,7}) {
            View view = render(options.withVisibleMeters("reset_credits"),plus,
                    ResetCreditsSnapshot.summary(count,NOW),true,"",180,100);
            assertEquals(String.valueOf(count),text(view,R.id.primary_samsung_value));
            assertEquals(Math.min(4,count)*25,progress(view,R.id.primary_samsung_progress));
            assertEnglish(view);
        }
        UsageSnapshot summary = multiWindowSnapshot();
        View fallback = render(options.withVisibleMeters("reset_credits"),summary,null,true,"",180,100);
        assertEquals("2",text(fallback,R.id.primary_samsung_value));
        View detail = render(options.withVisibleMeters("reset_credits"),summary,
                ResetCreditsSnapshot.summary(7,NOW),true,"",180,100);
        assertEquals("7",text(detail,R.id.primary_samsung_value));
        View unknown = render(options.withVisibleMeters("reset_credits"),plus,null,true,"",180,100);
        assertEquals("—",text(unknown,R.id.primary_samsung_value));
        assertEquals(0,progress(unknown,R.id.primary_samsung_progress));
    }

    @Test public void resetHelperUsesEarliestFutureOrdinaryWindowAndFiveHourTieBreak() {
        long twoHours = NOW+7_200_000L;
        UsageSnapshot tied = new UsageSnapshot("plus",true,false,
                new UsageWindow(0,18000,0,twoHours/1000),
                new UsageWindow(10,604800,0,twoHours/1000),NOW);
        View tie = render(options.withVisibleMeters("next_reset"),tied,null,true,"",180,100);
        assertEquals("2h 0m",text(tie,R.id.primary_samsung_value));
        assertEquals(40,progress(tie,R.id.primary_samsung_progress));
        UsageSnapshot earlierWeek = new UsageSnapshot("plus",true,false,tied.fiveHour,
                new UsageWindow(10,604800,0,(NOW+3_600_000L)/1000),NOW);
        View week = render(options.withVisibleMeters("next_reset"),earlierWeek,null,true,"",180,100);
        assertEquals("1h 0m",text(week,R.id.primary_samsung_value));
        assertEquals(1,progress(week,R.id.primary_samsung_progress));
        // A named extra has an earlier reset, but the upstream helper only considers Codex.
        View extra = render(options.withVisibleMeters("next_reset"),multiWindowSnapshot(),null,true,"",180,100);
        assertEquals("2h 0m",text(extra,R.id.primary_samsung_value));
    }

    @Test public void resetHelperAnchorsRelativeTimelineAndPrefersExplicitEpoch() {
        UsageWindow relative = new UsageWindow(0,18000,10800,0);
        UsageSnapshot cached = new UsageSnapshot("plus",true,false,relative,null,NOW-3_600_000L);
        View view = render(options.withVisibleMeters("next_reset"),cached,null,true,"",180,100);
        assertEquals("2h 0m",text(view,R.id.primary_samsung_value));
        assertEquals(40,progress(view,R.id.primary_samsung_progress));
        UsageSnapshot explicit = new UsageSnapshot("plus",true,false,
                new UsageWindow(0,18000,10800,(NOW+3_600_000L)/1000),null,NOW-3_600_000L);
        View epoch = render(options.withVisibleMeters("next_reset"),explicit,null,true,"",180,100);
        assertEquals("1h 0m",text(epoch,R.id.primary_samsung_value));
        assertEquals(20,progress(epoch,R.id.primary_samsung_progress));
    }

    @Test public void resetHelperHandlesSubMinuteExpiredAndMissingData() {
        UsageSnapshot soon = new UsageSnapshot("plus",true,false,
                new UsageWindow(0,18000,0,(NOW+59_000L)/1000),null,NOW);
        View future = render(options.withVisibleMeters("next_reset"),soon,null,true,"",180,100);
        assertEquals("now",text(future,R.id.primary_samsung_value));
        assertEquals(0,progress(future,R.id.primary_samsung_progress));
        for (long reset : new long[]{NOW,NOW-1000}) {
            UsageSnapshot expired = new UsageSnapshot("plus",true,false,
                    new UsageWindow(0,18000,0,reset/1000),null,NOW);
            View view = render(options.withVisibleMeters("next_reset"),expired,null,true,"",180,100);
            assertEquals("—",text(view,R.id.primary_samsung_value));
            assertEquals(0,progress(view,R.id.primary_samsung_progress));
        }
        View signedOut = render(options.withVisibleMeters("next_reset,reset_credits"),plus,
                ResetCreditsSnapshot.summary(7,NOW),false,"",180,100);
        assertEquals("—",text(signedOut,R.id.primary_samsung_value));
        assertEquals("—",text(signedOut,R.id.secondary_samsung_value));
        assertEnglish(signedOut);
    }

    @Test public void mixedSlotsFollowUserOrderAndCapOnlyRenderingCapacity() throws Exception {
        UsageSnapshot snapshot = multiWindowSnapshot();
        String extra = WidgetMeters.limitPrimaryKey(snapshot.additionalLimits.get(0));
        WidgetOptions selected = options.withVisibleMeters("reset_credits,weekly,next_reset,five_hour,"+extra);
        View wide = render(selected,snapshot,null,true,"",338,158);
        assertEquals("2",text(wide,R.id.quota_inline_value_0));
        assertEquals("36%",text(wide,R.id.quota_inline_value_1));
        assertEquals("2小时0分",text(wide,R.id.quota_inline_value_2));
        assertEquals("80%",text(wide,R.id.quota_inline_value_3));
        assertEquals(View.GONE,wide.findViewById(R.id.quota_optional_position).getVisibility());
        assertContentFits(wide);
        save(wide,"reference-sorted-four-items");
        View small = render(selected,snapshot,null,true,"",158,158);
        assertEquals("2",text(small,R.id.quota_value_0));
        assertEquals("36%",text(small,R.id.quota_value_1));
        assertEquals(View.GONE,small.findViewById(R.id.quota_card_2).getVisibility());
    }

    @Test public void fullMonthlyCacheSuppliesBuiltinLabelAndResetWithoutLegacyData() {
        WidgetUsageSnapshot monthly = new WidgetUsageSnapshot("free",NOW,3,List.of(
                new WidgetUsageWindow("codex:monthly","每月","monthly",25d,2592000,NOW+86_400_000L)));
        assertEquals("每月",QuotaCardRenderer.windowLabel(context,"weekly",monthly,null));
        assertEquals("每月",QuotaCardRenderer.windowLabel(context,"weekly",monthly,plus));
        View view = renderComplete(options.withVisibleMeters("weekly,next_reset,reset_credits"),monthly,null,338,158);
        assertEquals("每月",text(view,R.id.quota_inline_title_0));
        assertEquals("75%",text(view,R.id.quota_inline_value_0));
        assertEquals("1天0小时",text(view,R.id.quota_inline_value_1));
        assertEquals("3",text(view,R.id.quota_inline_value_2));
        assertEquals(View.GONE,view.findViewById(R.id.quota_optional_position).getVisibility());
        View dials = renderComplete(options.withVisibleMeters("weekly,next_reset"),monthly,null,180,100);
        assertEquals("Mo",dials.findViewById(R.id.primary_samsung_icon).getContentDescription());
        assertEquals("1d 0h",text(dials,R.id.secondary_samsung_value));
        assertEquals(3,progress(dials,R.id.secondary_samsung_progress));
        assertEnglish(dials);
    }

    @Test public void adaptiveRoutingUsesRealLauncherSizesAndSamsungSpans() {
        Bundle host=new Bundle();
        host.putParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES,new ArrayList<>(Arrays.asList(new SizeF(136,196),new SizeF(244,116))));
        assertEquals(2,QuotaCardRenderer.responsiveSizes(host).size());
        RemoteViews responsive=WidgetRenderer.buildResponsiveWidget(context,42,options,host);
        for(SizeF size:QuotaCardRenderer.responsiveSizes(host)) {
            RemoteViews selected=ReflectionHelpers.callInstanceMethod(responsive,"getRemoteViewsToApply",ClassParameter.from(Context.class,context),ClassParameter.from(SizeF.class,size));
            assertNotNull(selected.apply(context,new FrameLayout(context)).findViewById(R.id.quota_hero_value));
        }
        host.putInt("semAppWidgetRowSpan",1);host.putInt("semAppWidgetColumnSpan",2);
        assertTrue(QuotaCardRenderer.usesDials(host,260,100));
        host.putInt("semAppWidgetColumnSpan",3);
        assertFalse(QuotaCardRenderer.usesDials(host,180,100));
        assertFalse(QuotaCardRenderer.usesDials(136,196));
    }

    @Test public void smallHostsAndLargeSystemFontsKeepAllVisibleDataInBounds() throws Exception {
        Configuration config=context.getResources().getConfiguration();config.fontScale=1.3f;
        context.getResources().updateConfiguration(config,context.getResources().getDisplayMetrics());
        for(int[] size:new int[][]{{90,160},{110,180},{136,196},{158,300},{250,100},{330,360}}) {
            View view=render(options,plus,null,true,"",size[0],size[1]);
            assertContentFits(view);assertNoRowOverlap(view);
            save(view,"reference-fit-"+size[0]+"x"+size[1]);
        }
    }

    @Test @Config(sdk = 28) public void legacyAndroidAppliesReferenceLayout() {
        View view=render(options.withVisibleMeters("weekly"),pro,null,true,"",136,196);
        assertEquals("36%",text(view,R.id.quota_hero_value));
        assertContentFits(view);
    }

    @Test public void reapplyCanSwitchSingleDualAndStackedWithoutStaleViews() {
        View view=render(options.withVisibleMeters("weekly"),pro,null,true,"",338,158);
        UsageSnapshot snapshot=multiWindowSnapshot();
        WidgetOptions selected=options.withVisibleMeters(WidgetMeters.serialize(QuotaCardOptions.available(snapshot)));
        QuotaCardRenderer.build(context,42,selected,338,158,snapshot,null,null,true,"",NOW).reapply(context,view);
        measure(view,338,158);
        assertEquals(View.GONE,view.findViewById(R.id.quota_hero_position).getVisibility());
        assertEquals(View.VISIBLE,view.findViewById(R.id.quota_inline_position_3).getVisibility());
        QuotaCardRenderer.build(context,42,options.withVisibleMeters("weekly"),338,158,pro,null,null,true,"",NOW).reapply(context,view);
        measure(view,338,158);
        assertEquals(View.VISIBLE,view.findViewById(R.id.quota_hero_position).getVisibility());
        assertEquals(View.GONE,view.findViewById(R.id.quota_card_3).getVisibility());
    }

    @Test public void darkThemeWatermarkAndUsageColorsMatchReference() throws Exception {
        assertEquals(0xffff9500,ReferenceWidgetGraphics.usageColor(36,false));
        assertEquals(0xff34c759,ReferenceWidgetGraphics.usageColor(41,false));
        assertEquals(0xffff453a,ReferenceWidgetGraphics.usageColor(15,true));
        Configuration config=context.getResources().getConfiguration();
        config.uiMode=(config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)|Configuration.UI_MODE_NIGHT_YES;
        context.getResources().updateConfiguration(config,context.getResources().getDisplayMetrics());
        View dark=render(options.withVisibleMeters("weekly"),pro,null,true,"",136,196);
        assertEquals(View.VISIBLE,dark.findViewById(R.id.quota_watermark).getVisibility());
        assertEquals(96,dark.findViewById(R.id.quota_watermark).getWidth());
        save(dark,"reference-pro-dark");
    }

    @Test @Config(qualifiers = "en-rUS-mdpi") public void englishReferenceCopyIsAvailable() throws Exception {
        View view=render(options.withVisibleMeters("weekly"),pro,null,true,"",338,158);
        assertEquals("Weekly",text(view,R.id.quota_title_0));
        assertEquals("3d 0h",text(view,R.id.quota_meta_value));
        assertContentFits(view);save(view,"reference-english");
    }

    @Test public void proNamesRemainTenXIncludingLegacyCaches() {
        for(String plan:new String[]{"pro","pro20x","pro_20x","Pro 20x","pro10x","pro_10x"})
            assertEquals("Pro 10x",UsageFormat.planLabel(plan));
    }

    private View render(WidgetOptions options,UsageSnapshot snapshot,ResetCreditsSnapshot credits,boolean signedIn,String error,int w,int h) {
        View view=QuotaCardRenderer.build(context,42,options,w,h,snapshot,credits,null,signedIn,error,NOW).apply(context,new FrameLayout(context));
        measure(view,w,h);return view;
    }
    private View renderComplete(WidgetOptions options,WidgetUsageSnapshot snapshot,UsageSnapshot legacy,int w,int h) {
        RemoteViews remote = ReflectionHelpers.callStaticMethod(QuotaCardRenderer.class,"build",
                ClassParameter.from(Context.class,context),ClassParameter.from(int.class,42),
                ClassParameter.from(WidgetOptions.class,options),ClassParameter.from(int.class,w),ClassParameter.from(int.class,h),
                ClassParameter.from(WidgetUsageSnapshot.class,snapshot),ClassParameter.from(UsageSnapshot.class,legacy),
                ClassParameter.from(ResetCreditsSnapshot.class,null),ClassParameter.from(boolean.class,true),
                ClassParameter.from(String.class,""),ClassParameter.from(long.class,NOW),
                ClassParameter.from(boolean.class,QuotaCardRenderer.usesDials(w,h)));
        View view = remote.apply(context,new FrameLayout(context));
        measure(view,w,h);return view;
    }
    private static void measure(View view,int w,int h) { view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h); }
    private static String text(View view,int id) { return ((TextView)view.findViewById(id)).getText().toString(); }
    private static int progress(View view,int id) { return ((ProgressBar)view.findViewById(id)).getProgress(); }
    private static Rect bounds(View root,int id) { View view=root.findViewById(id);Rect rect=new Rect();view.getDrawingRect(rect);((ViewGroup)root).offsetDescendantRectToMyCoords(view,rect);return rect; }
    private static boolean visible(View root,View view) { for(View v=view;v!=root;v=(View)v.getParent()) if(v.getVisibility()!=View.VISIBLE)return false;return true; }
    private static void assertContentFits(View root) {
        checkChildren(root,root);
    }
    private static void checkChildren(View root,View node) {
        if(!visible(root,node))return;
        if(node instanceof TextView && !((TextView)node).getText().toString().isEmpty()) {
            Rect r=bounds(root,node.getId());TextView text=(TextView)node;
            String message=root.getWidth()+"x"+root.getHeight()+" "+text.getText()+" "+r+" size="+text.getTextSize()+" measured="+text.getPaint().measureText(text.getText().toString());
            assertTrue(message,r.left>=0&&r.top>=0&&r.right<=root.getWidth()&&r.bottom<=root.getHeight());
            assertNotNull(message,text.getLayout());
            assertEquals(message,0,text.getLayout().getEllipsisCount(0));
            assertTrue(message,text.getLayout().getLineWidth(0)<=text.getWidth()+1);
        }
        if(node instanceof ViewGroup) for(int i=0;i<((ViewGroup)node).getChildCount();i++)checkChildren(root,((ViewGroup)node).getChildAt(i));
    }
    private static void assertNoRowOverlap(View root) {
        for(int[] ids:new int[][]{{R.id.quota_title_0,R.id.quota_value_0,R.id.quota_graphic_0},{R.id.quota_title_1,R.id.quota_value_1,R.id.quota_graphic_1}}) {
            if(!visible(root,root.findViewById(ids[1])))continue;
            Rect title=bounds(root,ids[0]),value=bounds(root,ids[1]),bar=bounds(root,ids[2]);
            assertTrue(title+" / "+value,title.bottom<=value.top);
            assertTrue(value+" / "+bar,value.bottom<=bar.top);
        }
    }
    private static void assertEnglish(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if(view.getContentDescription()!=null)assertFalse(view.getContentDescription().toString().matches(".*[\\u4e00-\\u9fff].*"));
        if(view instanceof TextView)assertFalse(((TextView)view).getText().toString().matches(".*[\\u4e00-\\u9fff].*"));
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)assertEnglish(((ViewGroup)view).getChildAt(i));
    }
    private static void save(View view,String name) throws Exception {
        File dir=new File("build/reports/widget-previews");dir.mkdirs();
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
