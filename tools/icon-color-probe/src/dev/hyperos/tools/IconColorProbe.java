package dev.hyperos.tools;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One-shot, separate app_process program. No APK installation or app launch. */
public final class IconColorProbe {
    private static final String VERSION = "0.2";
    private static final int SIZE = 64;

    public static void main(String[] args) {
        int status;
        try {
            status = run(args);
        } catch (Throwable failure) {
            System.err.println("probe_status=FAILED error=" + errorType(failure));
            status = 2;
        }
        System.out.flush();
        System.err.flush();
        // Framework initialization can start non-daemon Binder threads.
        System.exit(status);
    }

    private static int run(String[] args) throws Exception {
        if (Process.myUid() != 0) throw new IllegalStateException("ROOT_REQUIRED");
        if (args.length < 3 || !"--out".equals(args[0])) {
            throw new IllegalArgumentException("Expected --out DIR USER:PACKAGE...");
        }
        File output = new File(args[1]);
        if (!output.isDirectory()) throw new IllegalArgumentException("OUTPUT_DIRECTORY_MISSING");
        System.out.println("probe_version=" + VERSION + " sdk=" + Build.VERSION.SDK_INT
                + " uid=" + Process.myUid() + " sample=" + SIZE + "x" + SIZE);
        Context systemContext = systemContext();
        Map<Integer, Context> users = new HashMap<>();
        users.put(0, systemContext);
        Set<String> seen = new HashSet<>();
        int ok = 0;
        int failed = 0;
        int colorful = 0;
        try (PrintWriter tsv = writer(new File(output, "colors.tsv"));
             PrintWriter html = writer(new File(output, "colors.html"))) {
            tsv.println("package\tuser\tsource\ticon_res\tstatus\tdominant\tcandidate\tcolorful_fraction\tvisible_pixels\timage\terror");
            html.println("<!doctype html><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
            html.println("<title>App 图标取色探针 0.2</title><style>body{font:16px sans-serif;margin:20px;background:#f5f5f5;color:#222}article{background:white;padding:16px;margin:12px 0;border-radius:12px;overflow-wrap:anywhere}img{width:64px;height:64px;background:repeating-conic-gradient(#ddd 0% 25%,white 0% 50%) 0/16px 16px}i{display:inline-block;width:30px;height:30px;border:1px solid #888;vertical-align:middle;margin:4px}code{font-size:14px}p{line-height:1.5}</style>");
            html.println("<h2>App 图标取色探针 0.2</h2><p>对照原图、整体主色和排除黑白后的候选色。候选色不保证是品牌色；本报告不读取通知正文。</p>");
            for (int i = 2; i < args.length && seen.size() < 32; i++) {
                String spec = args[i];
                if (!spec.matches("[0-9]{1,5}:[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")) {
                    System.out.println("status=BAD_ARGUMENT");
                    failed++;
                    continue;
                }
                int colon = spec.indexOf(':');
                int user = Integer.parseInt(spec.substring(0, colon));
                String pkg = spec.substring(colon + 1);
                if (!seen.add(user + ":" + pkg)) continue;
                try {
                    Context context = users.get(user);
                    if (context == null) {
                        context = forUser(systemContext, user);
                        users.put(user, context);
                    }
                    Row row = inspect(context, pkg, user, output);
                    ok++;
                    if (row.colors.candidate != null) colorful++;
                    String dominant = ColorSampler.hex(row.colors.dominant);
                    String candidate = ColorSampler.hex(row.colors.candidate);
                    String fraction = String.format(Locale.ROOT, "%.4f", row.colors.colorfulFraction());
                    String res = String.format(Locale.ROOT, "0x%08X", row.iconRes);
                    System.out.println("app=" + pkg + " user=" + user + " source=" + row.source
                            + " status=" + row.colors.status() + " dominant=" + dominant
                            + " candidate=" + candidate + " colorful_fraction=" + fraction
                            + " visible_pixels=" + row.colors.visible);
                    tsv.println(pkg + "\t" + user + "\t" + row.source + "\t" + res + "\t"
                            + row.colors.status() + "\t" + dominant + "\t" + candidate + "\t"
                            + fraction + "\t" + row.colors.visible + "\t" + row.image + "\tNONE");
                    html.println("<article><b>" + pkg + "</b> · user " + user + "<p><img src=\""
                            + row.image + "\" alt=\"原始图标\"></p><p>来源：" + row.source + " / " + res
                            + "</p><p>整体主色：" + swatch(row.colors.dominant) + " " + dominant
                            + "<br>彩色候选：" + swatch(row.colors.candidate) + " " + candidate
                            + "</p><code>" + row.colors.status() + " · 彩色像素比例 " + fraction + "</code></article>");
                } catch (Throwable failure) {
                    failed++;
                    String error = errorType(failure);
                    System.out.println("app=" + pkg + " user=" + user + " status=FAILED error=" + error);
                    tsv.println(pkg + "\t" + user + "\tNONE\tNONE\tFAILED\tNONE\tNONE\t0\t0\tNONE\t" + error);
                    html.println("<article><b>" + pkg + "</b> · user " + user
                            + "<p>FAILED: " + escape(error) + "</p></article>");
                }
            }
            html.println("<p>成功 " + ok + " · 有彩色候选 " + colorful + " · 失败 " + failed + "</p>");
            tsv.flush();
            html.flush();
            if (tsv.checkError() || html.checkError()) throw new IllegalStateException("REPORT_WRITE_FAILED");
        }
        System.out.println("checked=" + seen.size() + " success=" + ok + " colorful=" + colorful + " failed=" + failed);
        System.out.println("report=" + new File(output, "colors.html").getAbsolutePath());
        return failed == 0 ? 0 : 2;
    }

    private static Context systemContext() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Method main = thread.getDeclaredMethod("systemMain");
        main.setAccessible(true);
        Object instance = main.invoke(null);
        Method get = thread.getDeclaredMethod("getSystemContext");
        get.setAccessible(true);
        Context context = (Context) get.invoke(instance);
        if (context == null) throw new IllegalStateException("SYSTEM_CONTEXT_MISSING");
        return context;
    }

    private static Context forUser(Context system, int user) throws Exception {
        Method of = UserHandle.class.getDeclaredMethod("of", int.class);
        of.setAccessible(true);
        Object handle = of.invoke(null, user);
        Method create = Context.class.getDeclaredMethod("createContextAsUser", UserHandle.class, int.class);
        create.setAccessible(true);
        return (Context) create.invoke(system, handle, 0);
    }

    @SuppressWarnings("deprecation")
    private static Row inspect(Context context, String pkg, int user, File output) throws Exception {
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
        int iconRes = info.icon;
        String source = "application-resource";
        Intent launcher = pm.getLaunchIntentForPackage(pkg);
        if (launcher != null) {
            ActivityInfo activity = launcher.resolveActivityInfo(pm, 0);
            if (activity != null && activity.getIconResource() != 0) {
                info = activity.applicationInfo;
                iconRes = activity.getIconResource();
                source = "launcher-resource";
            }
        }
        // Do not report the default Android icon as this app's brand color.
        if (iconRes == 0) throw new IllegalStateException("APP_HAS_NO_DECLARED_ICON");
        Resources resources = pm.getResourcesForApplication(info);
        Drawable icon = resources.getDrawable(iconRes, null).mutate();
        Bitmap image = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        try {
            icon.setBounds(0, 0, SIZE, SIZE);
            icon.draw(new Canvas(image));
            int[] pixels = new int[SIZE * SIZE];
            image.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
            ColorSampler.Result colors = ColorSampler.sample(pixels);
            String name = "user" + user + "_" + pkg + ".png";
            try (FileOutputStream stream = new FileOutputStream(new File(output, name))) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw new IllegalStateException("PNG_WRITE_FAILED");
                }
            }
            return new Row(source, iconRes, colors, name);
        } finally {
            image.recycle();
        }
    }

    private static PrintWriter writer(File file) throws Exception {
        return new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
    }

    private static String swatch(Integer rgb) {
        return rgb == null ? "无" : "<i style=\"background:" + ColorSampler.hex(rgb) + "\"></i>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String errorType(Throwable failure) {
        for (int i = 0; i < 8 && failure.getCause() != null && failure.getCause() != failure; i++) {
            failure = failure.getCause();
        }
        String message = failure.getMessage();
        return failure.getClass().getName()
                + (message != null && message.matches("[A-Z_]+") ? ":" + message : "");
    }

    private static final class Row {
        final String source;
        final int iconRes;
        final ColorSampler.Result colors;
        final String image;

        Row(String source, int iconRes, ColorSampler.Result colors, String image) {
            this.source = source;
            this.iconRes = iconRes;
            this.colors = colors;
            this.image = image;
        }
    }
}
