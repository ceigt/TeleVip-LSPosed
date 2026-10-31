package com.my.televip.language;

import com.my.televip.logging.Logger;
import com.my.televip.utils.JsonAssetReader;
import com.my.televip.virtuals.messenger.LocaleController;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Translator {

    private static final Map<String, JSONObject> langMap = new HashMap<>();

    private static LocaleController localeController;

    public static void init() {
        try {
            loadAllLanguages();

            localeController = new LocaleController();
        } catch (Throwable e) {
            Logger.e(e);
        }
    }
    private static void loadAllLanguages() {
        List<String> files = JsonAssetReader.listFiles("assets/lang/", ".json");

        for (String fileName : files) {
            String langCode = fileName.substring(0, fileName.lastIndexOf('.'));

            JSONObject json = JsonAssetReader.readObject("assets/lang/" + fileName);
            if (json != null) {
                langMap.put(langCode, json);
                if ("id".equals(langCode)) langMap.put("in", json); // Android/Java legacy Indonesian code.
            }
        }
    }

    public static String get(String key) {
        if (localeController == null) return key;
        try {
            if (localeController.getCurrentLocale() != null) {
                String lang = localeController.getCurrentLocale().getLanguage();
                JSONObject langJson = langMap.get(lang);

                if (langJson != null && langJson.has(key)) {
                    return langJson.optString(key);
                }
                JSONObject enJson = langMap.get("en");
                String text;
                if (enJson != null && enJson.has(key)) {
                    text = enJson.optString(key);
                } else {
                    text = key;
                }

                return text;
            }

        } catch (Throwable e) {
            Logger.e(e);
        }

        return key;
    }

    public static String get(String key, Object... args) {
        if (localeController == null) return key;
        try {
            if (localeController.getCurrentLocale() != null) {
                String lang = localeController.getCurrentLocale().getLanguage();

                String text = null;

                JSONObject langJson = langMap.get(lang);

                if (langJson != null && langJson.has(key)) {
                    text = langJson.optString(key);
                }

                if (text == null) {
                    JSONObject enJson = langMap.get("en");
                    if (enJson != null && enJson.has(key)) {
                        text = enJson.optString(key);
                    } else {
                        text = key;
                    }
                }

                if (args != null && args.length > 0) {
                    return String.format(text, args);
                }

                return text;
            }
        } catch (Throwable e) {
            Logger.e(e);
        }

        return key;
    }

}
