package dev.hyperos.notificationcount.render;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import java.lang.reflect.Method;

import dev.hyperos.notificationcount.core.AppIconSource;
import dev.hyperos.notificationcount.core.IconColorSampler;

/** Same declared-resource channel as probe 0.2; no app launch or launcher-theme dependency. */
public final class AppIconColorLoader {
    private static final int SIZE = 64;
    private AppIconColorLoader() { }

    @SuppressWarnings("deprecation")
    public static Integer load(Context host, AppIconSource source) throws Exception {
        Context context = host;
        if (source.userId >= 0) {
            // SystemUI has cross-profile access; resolve lazily so failures only affect color.
            Method of = UserHandle.class.getDeclaredMethod("of", int.class);
            of.setAccessible(true);
            UserHandle user = (UserHandle) of.invoke(null, source.userId);
            Method create = Context.class.getDeclaredMethod("createContextAsUser", UserHandle.class, int.class);
            create.setAccessible(true);
            context = (Context) create.invoke(host, user, 0);
        }
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(source.packageName, 0);
        int resource = info.icon;
        Intent launch = pm.getLaunchIntentForPackage(source.packageName);
        if (launch != null) {
            ActivityInfo activity = launch.resolveActivityInfo(pm, 0);
            if (activity != null && activity.getIconResource() != 0) {
                info = activity.applicationInfo;
                resource = activity.getIconResource();
            }
        }
        if (resource == 0) return null;
        Drawable icon = pm.getResourcesForApplication(info).getDrawable(resource, null).mutate();
        return sample(icon);
    }

    static Integer sample(Drawable icon) {
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        try {
            icon.setBounds(0, 0, SIZE, SIZE);
            icon.draw(new Canvas(bitmap));
            int[] pixels = new int[SIZE * SIZE];
            bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
            return IconColorSampler.candidate(pixels);
        } finally {
            bitmap.recycle();
        }
    }
}
