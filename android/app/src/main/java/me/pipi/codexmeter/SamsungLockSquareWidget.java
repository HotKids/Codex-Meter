package me.pipi.codexmeter;

/** Square Samsung lock/AOD widget showing both allowances as numbers. */
public final class SamsungLockSquareWidget extends SamsungLockWidgetProvider {
    @Override
    protected SamsungLockWidgetSupport.Shape shape() {
        return SamsungLockWidgetSupport.Shape.SQUARE;
    }

    @Override
    protected SamsungLockWidgetSupport.Style style() {
        return SamsungLockWidgetSupport.Style.NUMBERS;
    }
}
