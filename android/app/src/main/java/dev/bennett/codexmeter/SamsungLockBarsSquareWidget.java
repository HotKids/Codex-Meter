package dev.bennett.codexmeter;

/** Square Samsung lock/AOD widget showing both allowances as bars. */
public final class SamsungLockBarsSquareWidget extends SamsungLockWidgetProvider {
    @Override
    protected SamsungLockWidgetSupport.Shape shape() {
        return SamsungLockWidgetSupport.Shape.SQUARE;
    }

    @Override
    protected SamsungLockWidgetSupport.Style style() {
        return SamsungLockWidgetSupport.Style.BARS;
    }
}
