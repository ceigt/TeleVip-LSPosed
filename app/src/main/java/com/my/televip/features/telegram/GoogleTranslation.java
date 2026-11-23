package com.my.televip.features.telegram;

import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.features.ghostMode.PrivacyProtection;
import com.my.televip.compat.XposedHelpers;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.UserConfig;
import com.my.televip.virtuals.messenger.MessageObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class GoogleTranslation {
    private static volatile boolean installed;
    private static final AtomicInteger sequence = new AtomicInteger(-0x5e000000);
    private static final ThreadPoolExecutor workers = new ThreadPoolExecutor(3, 3, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), task -> {
                Thread thread = new Thread(task, "TeleVip-Translate");
                thread.setDaemon(true);
                return thread;
            });
    private static final Map<Integer, Job> jobs = new ConcurrentHashMap<>();
    private static final Map<String, Source> sources = new LinkedHashMap<>();
    private static final class Source {
        final String text;
        final long time = android.os.SystemClock.elapsedRealtime();
        Source(String text) { this.text = text; }
    }
    private static final class Job {
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        FutureTask<Void> task;
        void cancel() {
            cancelled = true;
            if (task != null) { task.cancel(true); workers.remove(task); }
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }
    }
    private GoogleTranslation() {}
    private static boolean enabled() { return installed && Host.enabled(ConfigManager.googleTranslation); }
    private static String key(long account, long dialog, int message) {
        return account + "/" + dialog + "/" + message;
    }

    public static synchronized void init() {
        if (installed) return;
        try (HookInstallation attempt = HookInstallation.begin()) {
            Field messageOwner = Host.field("MessageObject", "messageOwner");
            Field messageText = Host.field("TLRPC$Message", "message");
            Field messageAccount = Host.field("MessageObject", "currentAccount");
            Field managerAccount = Host.field("BaseController", "currentAccount");
            Field peerField = Host.field("TLRPC$TL_messages_translateText", "peer");
            Field idsField = Host.field("TLRPC$TL_messages_translateText", "id");
            Field textsField = Host.field("TLRPC$TL_messages_translateText", "text");
            Field languageField = Host.field("TLRPC$TL_messages_translateText", "to_lang");
            Field plainText = Host.field("TLRPC$TL_textWithEntities", "text");
            Field entities = Host.field("TLRPC$TL_textWithEntities", "entities");
            Field results = Host.field("TLRPC$TL_messages_translateResult", "result");
            Class<?> requestType = Host.type("TLRPC$TL_messages_translateText");
            java.lang.reflect.Constructor<?> textConstructor = Host.type("TLRPC$TL_textWithEntities").getDeclaredConstructor();
            java.lang.reflect.Constructor<?> resultConstructor = Host.type("TLRPC$TL_messages_translateResult").getDeclaredConstructor();
            Method callback = Host.method("RequestDelegate", "run", 2);
            for (String method : new String[]{"chatEnabled", "contextEnabled", "featureAvailable", "dialogFeatureAvailable"}) {
                HMethod.hookMethod(Host.method("TranslateController", method, method.equals("dialogFeatureAvailable") ? 1 : 0),
                        new BaseMethodHook() {
                            @Override protected void beforeMethod(MethodHookParam call) {
                                if (enabled()) call.setResult(true);
                            }
                        });
            }
            HMethod.hookMethod(Host.method("TranslateController", "pushToTranslate", 3), new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) throws Exception {
                    if (!enabled() || call.args[0] == null) return;
                    Object owner = messageOwner.get(call.args[0]);
                    String text = owner == null ? null : (String) messageText.get(owner);
                    if (text == null || text.length() > 5000) return;
                    MessageObject message = new MessageObject(call.args[0]);
                    int account = messageAccount.getInt(call.args[0]);
                    long accountId = UserConfig.getInstance(account).getClientUserId();
                    if (accountId == 0) return;
                    synchronized (sources) {
                        long cutoff = android.os.SystemClock.elapsedRealtime() - 120000;
                        sources.values().removeIf(source -> source.time < cutoff);
                        String key = key(accountId, message.getDialogId(), message.getMessageOwner().getId());
                        sources.remove(key);
                        sources.put(key, new Source(text));
                        while (sources.size() > 512) sources.remove(sources.keySet().iterator().next());
                    }
                }
            });
            HMethod.hookMethod(Host.method("ConnectionsManager", "sendRequest", 2), new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) throws Exception {
                    if (!enabled() || !requestType.isInstance(call.args[0]) || call.args[1] == null) return;
                    int account = managerAccount.getInt(call.thisObject);
                    long accountId = UserConfig.getInstance(account).getClientUserId();
                    Object delegate = call.args[1];
                    Object request = call.args[0];
                    String language = (String) languageField.get(request);
                    List<String> texts = new ArrayList<>();
                    List<?> direct = (List<?>) textsField.get(request);
                    if (direct != null && !direct.isEmpty()) {
                        if (direct.size() > 20) throw new IllegalArgumentException("Translation batch too large");
                        for (Object text : direct) texts.add((String) plainText.get(text));
                    } else {
                        Object peer = peerField.get(request);
                        long dialog = 0;
                        if (peer != null) {
                            for (String type : new String[]{"user_id", "chat_id", "channel_id"}) {
                                long value = Host.field("TLRPC$InputPeer", type).getLong(peer);
                                if (value != 0) { dialog = type.equals("user_id") ? value : -value; break; }
                            }
                        }
                        List<?> ids = (List<?>) idsField.get(request);
                        if (ids == null || ids.size() > 20) throw new IllegalArgumentException("Invalid translation batch");
                        synchronized (sources) {
                            for (Object id : ids) {
                                Source source = sources.get(key(accountId, dialog, ((Number) id).intValue()));
                                if (source == null || android.os.SystemClock.elapsedRealtime() - source.time > 120000) {
                                    texts.clear();
                                    break;
                                }
                                texts.add(source.text);
                            }
                        }
                    }
                    if (texts.isEmpty() || accountId == 0) return;
                    int token = sequence.getAndDecrement();
                    Job job = new Job();
                    job.task = new FutureTask<>(() -> {
                        Object response = null, error = null;
                        try {
                            if (!enabled() || job.cancelled || UserConfig.getInstance(account).getClientUserId() != accountId) return null;
                            Object result = resultConstructor.newInstance();
                            List<Object> values = new ArrayList<>();
                            for (String text : texts) {
                                if (job.cancelled || !enabled()
                                        || UserConfig.getInstance(account).getClientUserId() != accountId) return null;
                                String translated = GoogleTranslateTransport.translate(text, language, connection -> {
                                    job.connection = connection;
                                    if (job.cancelled) connection.disconnect();
                                });
                                Object value = textConstructor.newInstance();
                                plainText.set(value, translated);
                                entities.set(value, new ArrayList<>());
                                values.add(value);
                            }
                            results.set(result, values);
                            response = result;
                        } catch (Exception failure) {
                            error = XposedHelpers.newInstance(Host.type("TLRPC$TL_error"));
                            XposedHelpers.setIntField(error, Obfuscate.getFieldName("TLRPC$TL_error", "code"), 500);
                            XposedHelpers.setObjectField(error, Obfuscate.getFieldName("TLRPC$TL_error", "text"), "TRANSLATION_FAILED");
                        } finally {
                            Object answer = response, failure = error;
                            PrivacyProtection.post(() -> {
                                try {
                                    if (!job.cancelled && enabled() && UserConfig.getInstance(account).getClientUserId() == accountId)
                                        callback.invoke(delegate, answer, failure);
                                } catch (Throwable callbackFailure) { Logger.e(callbackFailure); }
                                finally { jobs.remove(token, job); }
                            });
                        }
                        return null;
                    });
                    jobs.put(token, job);
                    call.setResult(token);
                    try { workers.execute(job.task); }
                    catch (java.util.concurrent.RejectedExecutionException full) {
                        jobs.remove(token, job);
                        Object error = XposedHelpers.newInstance(Host.type("TLRPC$TL_error"));
                        XposedHelpers.setIntField(error, Obfuscate.getFieldName("TLRPC$TL_error", "code"), 429);
                        XposedHelpers.setObjectField(error, Obfuscate.getFieldName("TLRPC$TL_error", "text"), "TRANSLATION_BUSY");
                        PrivacyProtection.post(() -> {
                            try { callback.invoke(delegate, null, error); }
                            catch (Exception failure) { Logger.e(failure); }
                        });
                    }
                }
            });
            HMethod.hookMethod(Host.method("ConnectionsManager", "cancelRequest", 2), new BaseMethodHook() {
                @Override protected void beforeMethod(MethodHookParam call) {
                    Job job = jobs.remove((int) call.args[0]);
                    if (job != null) {
                        job.cancel();
                        call.setResult(null);
                    }
                }
            });
            installed = attempt.isComplete();
        } catch (Throwable error) { Logger.e(error); }
    }
}
