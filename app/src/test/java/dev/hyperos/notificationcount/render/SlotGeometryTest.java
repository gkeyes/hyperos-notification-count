package dev.hyperos.notificationcount.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SlotGeometryTest {
    @Test
    public void wideHostKeepsOneFixedSlot() {
        assertEquals(14, SlotGeometry.fittedSize(240, 24, 14));
        assertEquals(113, SlotGeometry.centeredOffset(240, 14));
        assertEquals(5, SlotGeometry.centeredOffset(24, 14));
    }

    @Test
    public void narrowHostFitsWithoutOverflow() {
        assertEquals(8, SlotGeometry.fittedSize(8, 24, 14));
        assertEquals(0, SlotGeometry.centeredOffset(8, 8));
        assertEquals(8, SlotGeometry.centeredOffset(24, 8));
    }

    @Test
    public void shortHostPreservesSquareShape() {
        assertEquals(6, SlotGeometry.fittedSize(24, 6, 14));
        assertEquals(9, SlotGeometry.centeredOffset(24, 6));
        assertEquals(0, SlotGeometry.centeredOffset(6, 6));
    }

    @Test
    public void emptyBoundsHaveNoVisibleSlot() {
        assertEquals(0, SlotGeometry.fittedSize(0, 24, 14));
        assertEquals(0, SlotGeometry.fittedSize(24, 0, 14));
        assertEquals(0, SlotGeometry.fittedSize(-1, 24, 14));
        assertEquals(0, SlotGeometry.fittedSize(24, 24, 0));
    }

    @Test
    public void oddRemainderKeepsSlotInsideBounds() {
        assertEquals(1, SlotGeometry.centeredOffset(17, 14));
        assertEquals(0, SlotGeometry.centeredOffset(1, 1));
    }
}
