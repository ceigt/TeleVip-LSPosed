package com.my.televip;

import com.my.televip.compat.XposedBridge;
import com.my.televip.utils.Utils;
import io.github.libxposed.api.XposedModule;

/** libxposed API 102 entry point. */
public final class ModernModule extends XposedModule {
    @Override
    public void onPackageReady(PackageReadyParam param) {
        XposedBridge.setModule(this);
        Utils.modulePath = getModuleApplicationInfo().sourceDir;
        new MainHook().handleLoadPackage(param.getPackageName(), param.getClassLoader());
    }
}
