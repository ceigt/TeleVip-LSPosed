package com.my.televip.features.telegram;

public final class TranslationTests {
    public static int run() throws Exception {
        if (!GoogleTranslateTransport.parse("[[[\"hello\",\"a\"],[\" world\",\"b\"]]]").equals("hello world"))
            throw new AssertionError("Translation segment order");
        for (String bad : new String[]{"[]", "[[]]", "[null]", "invalid"}) {
            boolean rejected = false;
            try { GoogleTranslateTransport.parse(bad); } catch (Exception expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Malformed translation accepted");
        }
        return 5;
    }
}
