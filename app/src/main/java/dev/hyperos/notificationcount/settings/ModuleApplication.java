package dev.hyperos.notificationcount.settings;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executors;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Receives the framework service in the module app, without adding the app to hook scope. */
public class ModuleApplication extends Application {
    private SettingsStore settings;

    @Override public void onCreate() {
        super.onCreate();
        Handler main = new Handler(Looper.getMainLooper());
        settings = new SettingsStore(Executors.newSingleThreadExecutor(), main::post);
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService service) {
                main.post(() -> settings.connect(service,
                        () -> service.getRemotePreferences(FilterPreferences.GROUP)));
            }

            @Override public void onServiceDied(XposedService service) {
                main.post(() -> settings.disconnect(service));
            }
        });
    }

    public SettingsStore getSettingsStore() { return settings; }
}
