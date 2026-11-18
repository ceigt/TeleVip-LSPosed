package com.my.televip.features.connections;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.compat.XC_MethodHook.MethodHookParam;
import com.my.televip.hooks.HMethod;
import com.my.televip.hooks.HookInstallation;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.obfuscate.RuntimeMappings;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.messenger.FileLoadOperation;

public class DownloadSpeed {

    public static volatile boolean isEnable = false;

    public static synchronized void init() {
        if (isEnable) return;
        Class<?> owner = ClassLoad.getClass(ClassNames.FILE_LOAD_OPERATION);
        try (HookInstallation attempt = HookInstallation.begin()) {
            if (!attempt.require(owner)) return;
            if ("org.telegram.messenger".equals(Utils.pkgName)
                    && RuntimeMappings.active().resolveMethod("FileLoadOperation", "updateDownloadByteBudget") != null) {
                installConnectionRotation(owner);
            }
            installPremiumSettings();
            isEnable = attempt.isComplete();
        } catch (Throwable error) {
            Logger.e(error);
        }
    }

    private static void installPremiumSettings() {
        HMethod.hookMethod(ClassLoad.getClass(ClassNames.FILE_LOAD_OPERATION), Obfuscate.getMethodName("FileLoadOperation", "updateParams"), new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) {
                if (enabled()) {
                    FileLoadOperation fileLoadOperation = new FileLoadOperation(param.thisObject);
                    // Extra connections can cause server waits for non-Premium accounts.
                    if (!fileLoadOperation.hasRealPremium()) return;

                    int downloadChunkSizeBig = 1024 * 512;
                    int maxDownloadRequests = 8;

                    long defaultMaxFileSize = 1024L * 1024L * 2000L;

                    int maxCdnParts = (int) (defaultMaxFileSize / downloadChunkSizeBig);

                    fileLoadOperation.setDownloadChunkSizeBig(downloadChunkSizeBig);
                    fileLoadOperation.setMaxDownloadRequests(maxDownloadRequests);
                    fileLoadOperation.setMaxDownloadRequestsBig(maxDownloadRequests);
                    fileLoadOperation.setMaxCdnParts(maxCdnParts);
                    param.setResult(null);
                }
            }
        });
    }

    private static void installConnectionRotation(Class<?> owner) throws ReflectiveOperationException {
        Method dispatch = nativeMethod(owner, "dispatchDownloadChunk", int.class);
        Field chunk = nativeField(owner, "activeDownloadChunk", int.class);
        Field requests = nativeField(owner, "activeDownloadRequests", int.class);
        Field budget = nativeField(owner, "nativeDownloadBudget", boolean.class);
        Field connections = nativeField(owner, "downloadConnectionsCount", int.class);
        Field total = nativeField(owner, "totalDownloadBytes", long.class);
        Field forceSmall = nativeField(owner, "forceSmallChunk", boolean.class);
        Field story = nativeField(owner, "downloadStory", boolean.class);
        Set<Field> distinct = new HashSet<>(Arrays.asList(chunk, requests, budget, connections, total,
                forceSmall, story));
        if (distinct.size() != 7) throw new NoSuchFieldException("Overlapping download policy fields");

        ThreadLocal<Set<MethodHookParam>> widened = new ThreadLocal<>();
        AtomicBoolean logged = new AtomicBoolean();
        HMethod.hookMethod(dispatch, new BaseMethodHook() {
            @Override protected void beforeMethod(MethodHookParam call) throws IllegalAccessException {
                Object operation = call.thisObject;
                if (!enabled() || !DownloadTuning.widenConnections((int) call.args[0],
                        budget.getBoolean(operation), forceSmall.getBoolean(operation),
                        story.getBoolean(operation), total.getLong(operation), connections.getInt(operation),
                        chunk.getInt(operation), requests.getInt(operation))) return;
                Set<MethodHookParam> calls = widened.get();
                if (calls == null) {
                    calls = Collections.newSetFromMap(new IdentityHashMap<>());
                    widened.set(calls);
                }
                calls.add(call);
                // Let Telegram select and track the actual slot for sending, cancellation and retries.
                connections.setInt(operation, DownloadTuning.ROTATION_SLOTS);
                if (logged.compareAndSet(false, true)) {
                    Logger.l("Download tuning: ordinary slots=4 requests=" + requests.getInt(operation)
                            + " chunk=" + chunk.getInt(operation) + " native budget kept");
                }
            }

            @Override protected void afterMethod(MethodHookParam call) throws IllegalAccessException {
                Set<MethodHookParam> calls = widened.get();
                if (calls == null) return;
                try {
                    if (calls.remove(call)) connections.setInt(call.thisObject, DownloadTuning.NATIVE_SLOTS);
                } finally {
                    if (calls.isEmpty()) widened.remove();
                }
            }
        });
    }

    private static boolean enabled() {
        return isEnable && ConfigManager.downloadSpeed != null && ConfigManager.downloadSpeed.isEnable();
    }

    private static Method nativeMethod(Class<?> owner, String key, Class<?>... parameters)
            throws NoSuchMethodException {
        String name = RuntimeMappings.active().resolveMethod("FileLoadOperation", key);
        if (owner == null || name == null) throw new NoSuchMethodException(key);
        Method method = owner.getDeclaredMethod(name, parameters);
        if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException(key + " has unexpected signature");
        }
        return method;
    }

    private static Field nativeField(Class<?> owner, String key, Class<?> type) throws NoSuchFieldException {
        String name = RuntimeMappings.active().resolveField("FileLoadOperation", key);
        if (name == null) throw new NoSuchFieldException(key);
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type || Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException(key + " has unexpected type");
        }
        field.setAccessible(true);
        return field;
    }

}
