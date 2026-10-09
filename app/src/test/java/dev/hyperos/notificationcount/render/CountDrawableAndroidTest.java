package dev.hyperos.notificationcount.render;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
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

    @Test public void coloredDigitsAndOverflowKeepTheOriginalAlphaGeometry() {
        CountDrawable drawable = create();
        for (int count = 1; count <= 10; count++) {
            int[] original = draw(drawable, count, Color.WHITE);
            int[] colored = draw(drawable, count, 0xff03d769);
            for (int i = 0; i < original.length; i++) {
                assertEquals(Color.alpha(original[i]), Color.alpha(colored[i]));
                if (Color.alpha(colored[i]) == 255) {
                    assertEquals(0xff03d769, colored[i]);
                }
            }
            assertArrayEquals(original, draw(drawable, count, Color.WHITE));
        }
    }

    @Test public void glyphColorFillsOnlyTheDigitOpening() {
        CountDrawable drawable = create();
        // Large enough that every digit opening has fully transparent pixels.
        drawable.setIconSize(64);
        for (int count = 1; count <= 10; count++) {
            drawable.setGlyphColor(null);
            int[] knockout = drawLarge(drawable, count, 0xff03d769);
            drawable.setGlyphColor(0xff000000);
            int[] filled = drawLarge(drawable, count, 0xff03d769);
            int opened = 0;
            for (int i = 0; i < knockout.length; i++) {
                if (Color.alpha(knockout[i]) == 0 && Color.alpha(filled[i]) > 0) {
                    opened++;
                    assertTrue("Fill escaped the disc for count " + count, insideDisc(i));
                }
            }
            assertTrue("Digit opening not filled for count " + count, opened > 0);
        }
        drawable.setGlyphColor(null);
        assertNull(drawable.getGlyphColor());
        assertTrue(drawable.getFailureReason(), drawable.isHealthy());
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
        int[] before = draw(drawable, 8, Color.WHITE);
        drawable.setIconSize(24);
        int[] after = draw(drawable, 8, Color.WHITE);
        assertEquals(24, drawable.getIntrinsicWidth());
        assertTrue(visiblePixels(after) > visiblePixels(before));
        drawable.setIconSize(16);
        int[] restored = draw(drawable, 8, Color.WHITE);
        assertEquals(16, drawable.getIntrinsicWidth());
        assertEquals(16, drawable.getIntrinsicHeight());
        Rect restoredBounds = visibleBounds(restored);
        assertFalse(restoredBounds.isEmpty());
        assertTrue(new Rect(8, 8, 24, 24).contains(restoredBounds));
        assertTrue(restoredBounds.width() < visibleBounds(after).width());
        assertTrue(restoredBounds.height() < visibleBounds(after).height());
        // Native VectorDrawable caches only grow: shrinking resamples the larger bitmap,
        // so antialiased alpha>0 pixel totals need not match its first 16px rasterization.
    }

    /** Disc of the 16-unit vector drawn at 4px per unit across a 64px bitmap. */
    private boolean insideDisc(int index) {
        float x = (index % 64 + .5f) / 4f, y = (index / 64 + .5f) / 4f;
        float dx = x - 7f, dy = y - 8.7f;
        return dx * dx + dy * dy <= 6f * 6f;
    }

    private int[] drawLarge(CountDrawable drawable, int count, int tint) {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 64, 64);
        drawable.setCount(count);
        drawable.setTint(tint);
        drawable.draw(new Canvas(bitmap));
        int[] pixels = new int[64 * 64];
        bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64);
        bitmap.recycle();
        return pixels;
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

    private Rect visibleBounds(int[] pixels) {
        Rect bounds = new Rect();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                if (Color.alpha(pixels[y * 32 + x]) > 0) bounds.union(x, y, x + 1, y + 1);
            }
        }
        return bounds;
    }
}
