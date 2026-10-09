package dev.hyperos.notificationcount.render;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;

import dev.hyperos.notificationcount.R;

/**
 * Draws one 16dp local vector in the center of the bounds supplied by the host.
 * Bounds are host-view coordinates; the host owns padding and island clipping.
 */
public final class CountDrawable extends Drawable {
    private static final float ICON_SIZE_DP = 16f;
    private static final int OVERFLOW_STATE = 10;
    // Disc geometry of the vectors, in their 16x16 viewport (see the drawable XML).
    private static final float DISC_CX = 7f;
    private static final float DISC_CY = 8.7f;
    // Stay inside the disc's antialiased edge so the fill never shows around it.
    private static final float GLYPH_FILL_RADIUS = 5.5f;
    private static final float VIEWPORT = 16f;
    private static final int[] RESOURCE_IDS = {
            R.drawable.notification_count_1,
            R.drawable.notification_count_2,
            R.drawable.notification_count_3,
            R.drawable.notification_count_4,
            R.drawable.notification_count_5,
            R.drawable.notification_count_6,
            R.drawable.notification_count_7,
            R.drawable.notification_count_8,
            R.drawable.notification_count_9,
            R.drawable.notification_count_overflow
    };

    private final Drawable[] states = new Drawable[OVERFLOW_STATE + 1];
    private final Rect iconBounds = new Rect();
    private int iconSize;
    private int state;
    private int tint = Color.WHITE;
    private int alpha = 255;
    private ColorFilter colorFilter;
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Integer glyphColor;
    private boolean healthy = true;
    private String failureReason;

    public CountDrawable(Resources moduleResources) {
        int size = Math.round(ICON_SIZE_DP);
        if (moduleResources != null) {
            try {
                size = Math.max(1, Math.round(
                        ICON_SIZE_DP * moduleResources.getDisplayMetrics().density));
            } catch (Throwable failure) {
                fail("read density", failure);
            }
        }
        iconSize = size;

        if (moduleResources == null) {
            fail("load resources", new IllegalArgumentException("moduleResources is null"));
            return;
        }

        // Resolve every state before the host replaces any existing icon.
        for (int index = 0; index < RESOURCE_IDS.length; index++) {
            try {
                Drawable drawable = moduleResources.getDrawable(RESOURCE_IDS[index], null);
                if (!(drawable instanceof VectorDrawable)) {
                    throw new IllegalStateException("Resource is not an Android VectorDrawable");
                }
                drawable = drawable.mutate();
                drawable.setTint(tint);
                states[index + 1] = drawable;
            } catch (Throwable failure) {
                fail("load state " + (index + 1), failure);
            }
        }
    }

    /** Zero/negative counts are hidden; all counts above nine share the overflow vector. */
    public void setCount(int count) {
        int nextState = count <= 0 ? 0 : Math.min(count, OVERFLOW_STATE);
        if (state == nextState) {
            return;
        }
        state = nextState;
        invalidateSafely();
    }

    /** Recomputes the slot after the host display density changes. */
    public void setIconSize(int pixels) {
        int size = Math.max(1, pixels);
        if (iconSize == size) return;
        iconSize = size;
        onBoundsChange(getBounds());
        invalidateSafely();
    }

    @Override
    public void setTint(int color) {
        if (tint == color) {
            return;
        }
        tint = color;
        if (!healthy) {
            return;
        }
        try {
            for (Drawable drawable : states) {
                if (drawable != null) {
                    drawable.setTint(color);
                }
            }
            invalidateSafely();
        } catch (Throwable failure) {
            fail("set tint", failure);
        }
    }

    /**
     * Fills the knocked-out digit with this color; null keeps it transparent.
     * Drawn under the disc, so only the digit opening shows it.
     */
    public void setGlyphColor(Integer color) {
        if (java.util.Objects.equals(glyphColor, color)) return;
        glyphColor = color;
        invalidateSafely();
    }

    public Integer getGlyphColor() {
        return glyphColor;
    }

    @Override
    public void setAlpha(int alpha) {
        int nextAlpha = Math.max(0, Math.min(alpha, 255));
        if (this.alpha == nextAlpha) {
            return;
        }
        this.alpha = nextAlpha;
        if (!healthy) {
            return;
        }
        try {
            for (Drawable drawable : states) {
                if (drawable != null) {
                    drawable.setAlpha(nextAlpha);
                }
            }
            invalidateSafely();
        } catch (Throwable failure) {
            fail("set alpha", failure);
        }
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        if (this.colorFilter == colorFilter) {
            return;
        }
        this.colorFilter = colorFilter;
        if (!healthy) {
            return;
        }
        try {
            for (Drawable drawable : states) {
                if (drawable != null) {
                    drawable.setColorFilter(colorFilter);
                }
            }
            invalidateSafely();
        } catch (Throwable failure) {
            fail("set color filter", failure);
        }
    }

    @Override
    public ColorFilter getColorFilter() {
        return colorFilter;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        int size = SlotGeometry.fittedSize(bounds.width(), bounds.height(), iconSize);
        int left = bounds.left + SlotGeometry.centeredOffset(bounds.width(), size);
        int top = bounds.top + SlotGeometry.centeredOffset(bounds.height(), size);
        iconBounds.set(left, top, left + size, top + size);
    }

    @Override
    public void draw(Canvas canvas) {
        if (!healthy || state == 0 || alpha == 0 || iconBounds.isEmpty()) {
            return;
        }
        try {
            Drawable drawable = states[state];
            if (drawable == null) {
                throw new IllegalStateException("Selected vector is missing");
            }
            int saveCount = canvas.save();
            try {
                canvas.clipRect(getBounds());
                if (glyphColor != null && colorFilter == null) {
                    float scale = iconBounds.width() / VIEWPORT;
                    glyphPaint.setColor(glyphColor);
                    glyphPaint.setAlpha(Color.alpha(glyphColor) * alpha / 255);
                    canvas.drawCircle(iconBounds.left + DISC_CX * scale, iconBounds.top + DISC_CY * scale,
                            GLYPH_FILL_RADIUS * scale, glyphPaint);
                }
                drawable.setBounds(iconBounds);
                drawable.draw(canvas);
            } finally {
                canvas.restoreToCount(saveCount);
            }
        } catch (Throwable failure) {
            fail("draw state " + state, failure);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return iconSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return iconSize;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return !healthy || state == 0 || alpha == 0
                ? PixelFormat.TRANSPARENT : PixelFormat.TRANSLUCENT;
    }

    /** Failure is latched so the host can restore its original notification icons. */
    public boolean isHealthy() {
        return healthy;
    }

    /** Returns the first failing operation and exception type, or null while healthy. */
    public String getFailureReason() {
        return failureReason;
    }

    private void fail(String operation, Throwable failure) {
        if (!healthy) {
            return;
        }
        healthy = false;
        failureReason = operation + ": " + failure.getClass().getName();
        // One invalidation lets the host observe a drawing failure on its next pass.
        invalidateSafely();
    }

    private void invalidateSafely() {
        try {
            invalidateSelf();
        } catch (Throwable failure) {
            if (healthy) {
                healthy = false;
                failureReason = "invalidate: " + failure.getClass().getName();
            }
        }
    }
}
