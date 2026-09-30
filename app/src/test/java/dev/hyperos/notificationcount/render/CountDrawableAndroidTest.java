package dev.hyperos.notificationcount.render;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Executes Android 17 vector inflation and Skia drawing without touching a physical device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 37)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CountDrawableAndroidTest {
    @Test public void everyProvidedStateRendersAndOverflowIsStable() {
        CountDrawable drawable = create();
        Set<Integer> images = new HashSet<>();
        for (int count = 1; count <= 10; count++) {
            int[] pixels = draw(drawable, count, Color.WHITE);
            assertTrue("State " + count, visiblePixels(pixels) > 0);
            images.add(Arrays.hashCode(pixels));
        }
        assertEquals("Each vector must select a distinct glyph", 10, images.size());
        assertArrayEquals(draw(drawable, 10, Color.WHITE), draw(drawable, 99, Color.WHITE));
        assertArrayEquals(draw(drawable, 10, Color.WHITE), draw(drawable, Integer.MAX_VALUE, Color.WHITE));
        assertTrue(drawable.getFailureReason(), drawable.isHealthy());
    }

    @Test public void zeroHidesAndNativeTintSwitchPreservesGeometry() {
        CountDrawable drawable = create();
        assertEquals(0, visiblePixels(draw(drawable, 0, Color.WHITE)));
        int[] white = draw(drawable, 4, Color.WHITE);
        int[] black = draw(drawable, 4, Color.BLACK);
        for (int i = 0; i < white.length; i++) {
            assertEquals(Color.alpha(white[i]), Color.alpha(black[i]));
            if (Color.alpha(white[i]) > 0) {
                assertEquals(255, Color.red(white[i]));
                assertEquals(0, Color.red(black[i]));
            }
        }
        assertEquals(0, visiblePixels(draw(drawable, 0, Color.WHITE)));
    }

    @Test public void overlayDoesNotEnterNativeChildListAndCanBeRemoved() {
        FrameLayout container = new FrameLayout(RuntimeEnvironment.getApplication());
        FrameLayout nativeChild = new FrameLayout(RuntimeEnvironment.getApplication());
        container.addView(nativeChild);
        CountDrawable drawable = create();
        container.getOverlay().add(drawable);
        assertEquals(1, container.getChildCount());
        assertSame(container, nativeChild.getParent());
        container.getOverlay().remove(drawable);
        assertEquals(1, container.getChildCount());
    }

    @Test public void missingResourcesFailBeforeNativeIconsCanBeSuppressed() {
        CountDrawable drawable = new CountDrawable(null);
        assertFalse(drawable.isHealthy());
        assertNotNull(drawable.getFailureReason());
        assertEquals(0, visiblePixels(draw(drawable, 5, Color.WHITE)));
    }

    @Test public void repeatedCountsDoNotScheduleExtraFrames() {
        CountDrawable drawable = create();
        int[] invalidations = {0};
        drawable.setCallback(new Drawable.Callback() {
            @Override public void invalidateDrawable(Drawable who) { invalidations[0]++; }
            @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { }
            @Override public void unscheduleDrawable(Drawable who, Runnable what) { }
        });
        drawable.setCount(3);
        drawable.setCount(3);
        assertEquals(1, invalidations[0]);
        drawable.setCount(10);
        drawable.setCount(100);
        assertEquals(2, invalidations[0]);
    }

    @Test public void densityChangeResizesTheGlyphInsideExistingBounds() {
        CountDrawable drawable = create();
        drawable.setIconSize(16);
        int before = visiblePixels(draw(drawable, 8, Color.WHITE));
        drawable.setIconSize(24);
        int after = visiblePixels(draw(drawable, 8, Color.WHITE));
        assertEquals(24, drawable.getIntrinsicWidth());
        assertTrue(after > before);
        drawable.setIconSize(16);
        assertEquals(before, visiblePixels(draw(drawable, 8, Color.WHITE)));
    }

    private CountDrawable create() {
        Resources resources = RuntimeEnvironment.getApplication().getResources();
        CountDrawable drawable = new CountDrawable(resources);
        assertTrue(drawable.getFailureReason(), drawable.isHealthy());
        return drawable;
    }

    private int[] draw(CountDrawable drawable, int count, int tint) {
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 32, 32);
        drawable.setCount(count);
        drawable.setTint(tint);
        drawable.draw(new Canvas(bitmap));
        int[] pixels = new int[32 * 32];
        bitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32);
        bitmap.recycle();
        return pixels;
    }

    private int visiblePixels(int[] pixels) {
        int visible = 0;
        for (int pixel : pixels) if (Color.alpha(pixel) > 0) visible++;
        return visible;
    }
}
