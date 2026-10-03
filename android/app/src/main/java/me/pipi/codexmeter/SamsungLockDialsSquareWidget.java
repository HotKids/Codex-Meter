package me.pipi.codexmeter;

/** Square Samsung lock/AOD widget showing both allowances as gauges. */
public final class SamsungLockDialsSquareWidget extends SamsungLockWidgetProvider {
    @Override
    protected SamsungLockWidgetSupport.Shape shape() {
        return SamsungLockWidgetSupport.Shape.SQUARE;
    }

    @Override
    protected SamsungLockWidgetSupport.Style style() {
        return SamsungLockWidgetSupport.Style.DIALS;
    }
}
