package dev.hyperos.notificationcount.hook;

import android.graphics.Canvas;
import android.view.View;
import java.util.function.Predicate;
import io.github.libxposed.api.XposedInterface;

/** Suppresses pixels while retaining the original draw and other modules' interceptor calls. */
public final class ClippedDrawHook implements XposedInterface.Hooker {
    private final Predicate<View> shouldSuppress;

    public ClippedDrawHook(Predicate<View> shouldSuppress) {
        this.shouldSuppress = shouldSuppress;
    }

    @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
        if (!shouldSuppress.test((View) chain.getThisObject())) return chain.proceed();
        Canvas canvas = (Canvas) chain.getArg(0);
        int save = canvas.save();
        try {
            canvas.clipRect(0, 0, 0, 0);
            return chain.proceed();
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
