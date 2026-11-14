package com.aadith.mustre.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HttpHandler {

    private static final String TAG = "HttpHandler";

    public interface MusicDataCallback {
        void onSuccess(String result);
        void onError(Exception e);
    }

    private static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(5, 10, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final ExecutorService SHARED_EXECUTOR = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean isRunningOnEmulator() {
        return android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(android.os.Build.PRODUCT)
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu");
    }

    public void makeHttpGetRequest(String url, MusicDataCallback callback) {
        if (url == null || url.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError(new IllegalArgumentException("URL cannot be empty")));
            return;
        }

        String normalizedUrl = url.trim();
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            normalizedUrl = "http://" + normalizedUrl;
        }

        HttpUrl httpUrl = HttpUrl.parse(normalizedUrl);
        if (httpUrl == null) {
            String encoded = android.net.Uri.encode(normalizedUrl, ":/@?&=#+%,;[]");
            httpUrl = HttpUrl.parse(encoded);
        }
        if (httpUrl == null) {
            mainHandler.post(() -> callback.onError(new IllegalArgumentException("Invalid URL format: " + url)));
            return;
        }

        makeHttpGetRequest(httpUrl, callback);
    }

    public void makeHttpGetRequest(HttpUrl httpUrl, MusicDataCallback callback) {
        if (httpUrl == null) {
            mainHandler.post(() -> callback.onError(new IllegalArgumentException("HttpUrl cannot be null")));
            return;
        }

        SHARED_EXECUTOR.execute(() -> {
            Request request = new Request.Builder()
                    .url(httpUrl)
                    .header("User-Agent", "Mustre-AndroidApp/1.0")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Connection", "keep-alive")
                    .build();

            try {
                String result = executeWithRetry(request, httpUrl);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.w(TAG, "HTTP GET failed for " + httpUrl + ": " + e.getMessage());
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private String executeWithRetry(Request request, HttpUrl httpUrl) throws IOException {
        try (Response response = SHARED_CLIENT.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
        } catch (IOException initialEx) {
            // Check if socket was closed/reset by server or connection failed
            String host = httpUrl.host();
            boolean isEmulator = isRunningOnEmulator();
            boolean isLocalHost = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);

            // 1. Emulator fallback to host bridge 10.0.2.2 if applicable
            if (!"10.0.2.2".equals(host) && (isLocalHost || isEmulator)) {
                Log.d(TAG, "Connection to " + host + " failed (" + initialEx.getClass().getSimpleName() + ": "
                        + initialEx.getMessage() + "), trying emulator bridge 10.0.2.2");
                HttpUrl fallbackUrl = httpUrl.newBuilder().host("10.0.2.2").build();
                Request fallbackRequest = new Request.Builder()
                        .url(fallbackUrl)
                        .header("User-Agent", "Mustre-AndroidApp/1.0")
                        .header("Connection", "close")
                        .build();
                try (Response response = SHARED_CLIENT.newCall(fallbackRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        return response.body().string();
                    } else {
                        throw new IOException("HTTP " + response.code() + " " + response.message());
                    }
                } catch (Exception fallbackEx) {
                    Log.w(TAG, "Fallback to 10.0.2.2 also failed: " + fallbackEx.getMessage());
                }
            }

            // 2. Automatic retry with Connection: close (bypassing stale pooled socket)
            if (initialEx instanceof SocketException || initialEx instanceof SocketTimeoutException || initialEx instanceof ConnectException) {
                Log.d(TAG, "Encountered socket exception (" + initialEx.getMessage() + "), retrying with fresh connection...");
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {}

                Request freshRequest = request.newBuilder()
                        .header("Connection", "close")
                        .build();
                try (Response response = SHARED_CLIENT.newCall(freshRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        return response.body().string();
                    } else {
                        throw new IOException("HTTP " + response.code() + " " + response.message());
                    }
                }
            }

            throw initialEx;
        }
    }

    public void makeHttpGetRequest(String baseUrl, Map<String, String> queryParams, MusicDataCallback callback) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError(new IllegalArgumentException("Invalid base URL: " + baseUrl)));
            return;
        }

        String normalizedUrl = baseUrl.trim();
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            normalizedUrl = "http://" + normalizedUrl;
        }

        HttpUrl parsedUrl = HttpUrl.parse(normalizedUrl);
        if (parsedUrl == null) {
            String encoded = android.net.Uri.encode(normalizedUrl, ":/@?&=#+%,;[]");
            parsedUrl = HttpUrl.parse(encoded);
        }
        if (parsedUrl == null) {
            mainHandler.post(() -> callback.onError(new IllegalArgumentException("Invalid base URL: " + baseUrl)));
            return;
        }

        HttpUrl.Builder urlBuilder = parsedUrl.newBuilder();
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
                }
            }
        }

        makeHttpGetRequest(urlBuilder.build(), callback);
    }
}
