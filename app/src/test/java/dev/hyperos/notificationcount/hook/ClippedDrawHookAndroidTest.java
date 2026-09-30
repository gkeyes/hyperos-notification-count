package dev.hyperos.notificationcount.hook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Executable;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import io.github.libxposed.api.XposedInterface;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClippedDrawHookAndroidTest {
    @Test public void suppressionPreservesOtherInterceptorsAndRestoresCanvas() throws Throwable {
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        FakeChain chain = new FakeChain(canvas, null);
        int depth = canvas.getSaveCount();
        Object result = new ClippedDrawHook(view -> true).intercept(chain);
        assertSame(chain.result, result);
        assertEquals(1, chain.calls);
        assertEquals(0, Color.alpha(bitmap.getPixel(8, 8)));
        assertEquals(depth, canvas.getSaveCount());
        canvas.drawColor(Color.BLUE);
        assertEquals(Color.BLUE, bitmap.getPixel(8, 8));
    }

    @Test public void unmarkedParentsKeepTheirPixels() throws Throwable {
        FrameLayout topBar = new FrameLayout(RuntimeEnvironment.getApplication());
        FrameLayout shelf = new FrameLayout(RuntimeEnvironment.getApplication());
        View icon = new View(RuntimeEnvironment.getApplication());
        shelf.addView(icon);
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        FakeChain chain = new FakeChain(new Canvas(bitmap), icon);
        new ClippedDrawHook(view -> view.getParent() == topBar).intercept(chain);
        assertEquals(1, chain.calls);
        assertEquals(Color.RED, bitmap.getPixel(8, 8));
    }

    @Test public void originalExceptionStillPropagatesAndCanvasIsRestored() throws Throwable {
        Canvas canvas = new Canvas(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888));
        FakeChain chain = new FakeChain(canvas, null);
        chain.failure = new IllegalStateException("original draw");
        int depth = canvas.getSaveCount();
        try {
            new ClippedDrawHook(view -> true).intercept(chain);
            fail();
        } catch (IllegalStateException failure) {
            assertSame(chain.failure, failure);
        }
        assertEquals(1, chain.calls);
        assertEquals(depth, canvas.getSaveCount());
        assertFalse(canvas.getClipBounds().isEmpty());
    }

    private static final class FakeChain implements XposedInterface.Chain {
        final Canvas canvas;
        final View icon;
        final Object result = new Object();
        int calls;
        Throwable failure;
        FakeChain(Canvas canvas, View icon) { this.canvas = canvas; this.icon = icon; }
        @Override public Executable getExecutable() { return null; }
        @Override public Object getThisObject() { return icon; }
        @Override public List<Object> getArgs() { return List.of(canvas); }
        @Override public Object getArg(int index) { return getArgs().get(index); }
        @Override public Object proceed() throws Throwable {
            calls++;
            if (failure != null) throw failure;
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            canvas.drawRect(0, 0, 16, 16, paint);
            return result;
        }
        @Override public Object proceed(Object[] args) throws Throwable { return proceed(); }
        @Override public Object proceedWith(Object receiver) throws Throwable { return proceed(); }
        @Override public Object proceedWith(Object receiver, Object[] args) throws Throwable { return proceed(); }
    }
}
