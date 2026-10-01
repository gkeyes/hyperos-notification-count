package dev.hyperos.notificationcount;

import android.os.Build;
import android.util.Log;

import dev.hyperos.notificationcount.hook.SystemUiHooks;
import io.github.libxposed.api.XposedModule;

/** Modern API 102 entry. Only the main SystemUI process is hooked. */
public final class NotificationCountModule extends XposedModule {
    public static final String TARGET_PACKAGE = "com.android.systemui";
    public static final String TAG = "HyperOSNotificationCount";
    private boolean mainSystemUi;
    private boolean installed;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        mainSystemUi = !param.isSystemServer()
                && TARGET_PACKAGE.equals(param.getProcessName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (installed || !mainSystemUi || !param.isFirstPackage()
                || !TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (Build.VERSION.SDK_INT != 37) {
            log(Log.WARN, TAG, "Unsupported Android version; keeping original notification icons");
            return;
        }
        try {
            SystemUiHooks hooks = new SystemUiHooks(this, param.getClassLoader());
            hooks.install();
            installed = true;
            log(Log.INFO, TAG, BuildConfig.VERSION_NAME + ": API 102 hooks registered for SystemUI");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Hook registration failed: " + error.getClass().getSimpleName());
        }
    }
}
