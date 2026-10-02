package dev.bennett.codexmeter;

import static org.junit.Assert.*;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** An in-flight widget refresh must not undo sign-out or replace a newly imported account. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = QuotaCardsTest.TestApp.class, shadows = WidgetTokenRefreshTest.Tokens.class)
public class WidgetTokenRefreshTest {
    @Implements(SecureTokenStore.class)
    public static class Tokens {
        static AuthTokens current;
        @Implementation protected static AuthTokens load(Context context) { return current; }
        @Implementation protected static void save(Context context, AuthTokens tokens) { current = tokens; }
    }

    @Test public void signedOutAndReplacedAccountsRejectLateRefresh() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        AuthTokens original = tokens("account-a", "old");
        AuthTokens refreshed = tokens("account-a", "fresh");
        Tokens.current = null;
        assertFalse(SecureTokenStore.saveIfCurrent(context, original, refreshed));
        assertNull(Tokens.current);
        AuthTokens replacement = tokens("account-b", "imported");
        Tokens.current = replacement;
        assertFalse(SecureTokenStore.saveIfCurrent(context, original, refreshed));
        assertSame(replacement, Tokens.current);
    }

    @Test public void refreshOnlyReplacesTheCredentialsItStartedWith() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        AuthTokens original = tokens("account-a", "old");
        AuthTokens refreshed = tokens("account-a", "fresh");
        Tokens.current = original;
        assertTrue(SecureTokenStore.saveIfCurrent(context, original, refreshed));
        assertSame(refreshed, Tokens.current);
        assertFalse(SecureTokenStore.saveIfCurrent(context, original, tokens("account-a", "late")));
        assertSame(refreshed, Tokens.current);
    }

    private static AuthTokens tokens(String account, String access) {
        return new AuthTokens(access, "fixture-refresh", "", Long.MAX_VALUE, account, "");
    }
}
