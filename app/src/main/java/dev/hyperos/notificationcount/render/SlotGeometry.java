package dev.hyperos.notificationcount.render;

/** Geometry for one fixed icon slot; independent of Android for local checks. */
final class SlotGeometry {
    private SlotGeometry() {
    }

    static int fittedSize(int width, int height, int preferredSize) {
        if (width <= 0 || height <= 0 || preferredSize <= 0) {
            return 0;
        }
        return Math.min(preferredSize, Math.min(width, height));
    }

    static int centeredOffset(int availableSize, int fittedSize) {
        return Math.max(0, (availableSize - fittedSize) / 2);
    }
}
