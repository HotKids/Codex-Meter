package me.pipi.codexmeter;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class, qualifiers = "zh-rCN")
public class UserErrorLocalizationTest {
    @Test
    public void serverDescriptionsStayDiagnosticWhileTheDisplayUsesLocalResources() {
        Context context = RuntimeEnvironment.getApplication();
        for (String body : new String[] {
                "{\"error_description\":\"Synthetic server failure\"}",
                "{\"error\":\"Synthetic server failure\"}",
                "{\"error\":{\"message\":\"Synthetic server failure\"}}",
                "{\"message\":\"Synthetic server failure\"}"}) {
            Exception failure = OAuthClient.responseError(context, body,
                    R.string.auth_error_usage_refresh_failed);
            assertEquals("Synthetic server failure", failure.getMessage());
            assertEquals(context.getString(R.string.auth_error_usage_refresh_failed),
                    failure.getLocalizedMessage());
            assertEquals(failure.getLocalizedMessage(), MainActivity.safeMessage(context, failure));
        }
    }

    @Test
    public void unknownPlatformMessagesUseTheOperationFallback() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(context.getString(R.string.dashboard_operation_failed),
                MainActivity.safeMessage(context, new IllegalStateException("Synthetic platform detail")));
    }

    @Test
    public void commonTransportFailuresHaveLocalizedRecoveryText() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("请求超时，请稍后重试。",
                MainActivity.safeMessage(context, new SocketTimeoutException("Read timed out")));
        for (Exception failure : new Exception[] {
                new UnknownHostException("synthetic.example.test"),
                new ConnectException("Synthetic connection refused")}) {
            assertEquals("无法连接服务器，请检查网络后重试。", MainActivity.safeMessage(context, failure));
        }
        assertEquals("无法建立安全连接，请稍后重试。",
                MainActivity.safeMessage(context, new SSLHandshakeException("Synthetic handshake failure")));
    }

    @Test
    @Config(qualifiers = "en")
    public void englishAlsoUsesTheOperationCopyInsteadOfArbitraryServerText() {
        Context context = RuntimeEnvironment.getApplication();
        Exception failure = OAuthClient.responseError(context,
                "{\"message\":\"Synthetic server failure\"}", R.string.auth_error_usage_refresh_failed);
        assertEquals("Synthetic server failure", failure.getMessage());
        assertEquals("Usage refresh failed.", MainActivity.safeMessage(context, failure));
    }
}
