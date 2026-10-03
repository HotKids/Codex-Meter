package me.pipi.codexmeter;

/** Wide Samsung lock/AOD widget showing both allowances as gauges. */
public final class SamsungLockDialsWideWidget extends SamsungLockWidgetProvider {
    @Override
    protected SamsungLockWidgetSupport.Shape shape() {
        return SamsungLockWidgetSupport.Shape.WIDE;
    }

    @Override
    protected SamsungLockWidgetSupport.Style style() {
        return SamsungLockWidgetSupport.Style.DIALS;
    }
}
