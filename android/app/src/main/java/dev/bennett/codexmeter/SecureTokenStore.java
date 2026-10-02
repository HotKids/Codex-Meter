package dev.bennett.codexmeter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Stores OAuth credentials as an AES-GCM blob whose key never leaves the Android Keystore. */
@SuppressLint({"ApplySharedPref"})
public final class SecureTokenStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "codex_meter_auth_key_v1";
    private static final String KEY_BLOB = "blob";
    private static final String BLOB_IV = "iv";
    private static final String BLOB_CIPHERTEXT = "ct";
    private static final int GCM_TAG_BITS = 128;
    private static final Object LOCK = new Object();
    private static final String PREFS = "secure_auth_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private SecureTokenStore() {
    }

    public static void save(Context context, AuthTokens tokens) throws Exception {
        synchronized (LOCK) {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] ciphertext = cipher.doFinal(
                    tokens.toJson().toString().getBytes(StandardCharsets.UTF_8));
            JSONObject blob = new JSONObject();
            blob.put(BLOB_IV, Base64.getEncoder().encodeToString(cipher.getIV()));
            blob.put(BLOB_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext));
            if (!prefs(context).edit().putString(KEY_BLOB, blob.toString()).commit()) {
                throw new Exception("Could not persist encrypted credentials.");
            }
        }
    }

    /** Returns the stored credentials, or null; an unreadable blob is discarded. */
    public static AuthTokens load(Context context) {
        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            String stored = prefs.getString(KEY_BLOB, null);
            if (stored == null || stored.isEmpty()) {
                return null;
            }
            try {
                JSONObject blob = new JSONObject(stored);
                byte[] iv = Base64.getDecoder().decode(blob.getString(BLOB_IV));
                byte[] ciphertext = Base64.getDecoder().decode(blob.getString(BLOB_CIPHERTEXT));
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                        new GCMParameterSpec(GCM_TAG_BITS, iv));
                String json = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
                AuthTokens tokens = AuthTokens.fromJson(new JSONObject(json));
                return tokens.isUsable() ? tokens : null;
            } catch (Exception e) {
                prefs.edit().remove(KEY_BLOB).commit();
                return null;
            }
        }
    }

    public static boolean isSignedIn(Context context) {
        return load(context) != null;
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            prefs(context).edit().clear().commit();
            try {
                KeyStore keyStore = loadKeyStore();
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS);
                }
            } catch (Exception ignored) {
                // Signing out must succeed even if the Keystore entry cannot be removed.
            }
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        Key key = loadKeyStore().getKey(KEY_ALIAS, null);
        if (key instanceof SecretKey) {
            return (SecretKey) key;
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEY_STORE);
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return keyGenerator.generateKey();
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        return keyStore;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
