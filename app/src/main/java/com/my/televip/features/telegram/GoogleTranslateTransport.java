package com.my.televip.features.telegram;

import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Bounded HTTPS transport; callers decide explicitly which text can leave the device. */
final class GoogleTranslateTransport {
    interface Connection { void opened(HttpURLConnection connection); }
    private GoogleTranslateTransport() {}
    static String translate(String text, String language, Connection observer) throws Exception {
        if (text == null || text.length() > 5000 || language == null
                || !language.matches("[a-zA-Z]{2,3}(?:-[a-zA-Z]{2,8})?")) {
            throw new IllegalArgumentException("Invalid translation input");
        }
        if (text.trim().isEmpty()) return text;
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://translate.googleapis.com/translate_a/single").openConnection();
        observer.opened(connection);
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] body = ("client=gtx&sl=auto&tl=" + URLEncoder.encode(language, "UTF-8")
                    + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8")).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
            if (connection.getResponseCode() != 200) throw new java.io.IOException("Translation unavailable");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted() || bytes.size() + count > 1024 * 1024) {
                        throw new java.io.IOException("Translation response exceeded limits or was cancelled");
                    }
                    bytes.write(buffer, 0, count);
                }
            }
            return parse(bytes.toString("UTF-8"));
        } finally { connection.disconnect(); }
    }
    static String parse(String response) throws Exception {
        JSONArray parts = new JSONArray(response).getJSONArray(0);
        StringBuilder translated = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONArray part = parts.optJSONArray(i);
            if (part != null && !part.isNull(0)) translated.append(part.getString(0));
        }
        if (translated.length() == 0) throw new java.io.IOException("Empty translation");
        return translated.toString();
    }

}
