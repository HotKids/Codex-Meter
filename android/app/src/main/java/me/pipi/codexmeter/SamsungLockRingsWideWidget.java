package me.pipi.codexmeter;

/** Wide Samsung lock/AOD widget showing both allowances as rings. */
public final class SamsungLockRingsWideWidget extends SamsungLockWidgetProvider {
    @Override
    protected SamsungLockWidgetSupport.Shape shape() {
        return SamsungLockWidgetSupport.Shape.WIDE;
    }

    @Override
    protected SamsungLockWidgetSupport.Style style() {
        return SamsungLockWidgetSupport.Style.RINGS;
    }
}
