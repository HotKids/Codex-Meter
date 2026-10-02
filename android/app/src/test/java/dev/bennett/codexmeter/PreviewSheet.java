package dev.bennett.codexmeter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders inflated widget views onto a wallpaper-like backdrop and writes PNG previews to
 * {@code app/build/reports/widget-previews/}, plus one contact sheet for quick review in CI.
 */
final class PreviewSheet {
    static final File OUTPUT_DIR = new File("build/reports/widget-previews");

    private static final int GAP_PX = 24;
    private static final int LABEL_PX = 28;

    private final String name;
    private final List<Bitmap> tiles = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    PreviewSheet(String name) {
        this.name = name;
    }

    /** Measures {@code view} at an exact pixel size, draws it over a backdrop and records it. */
    Bitmap add(String label, View view, int widthPx, int heightPx) throws IOException {
        view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, widthPx, heightPx);
        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawBackdrop(canvas, widthPx, heightPx);
        view.draw(canvas);
        tiles.add(bitmap);
        labels.add(label);
        write(bitmap, new File(OUTPUT_DIR, fileName(label)));
        return bitmap;
    }

    /** Writes every recorded tile into one labelled contact sheet. */
    File writeSheet() throws IOException {
        int columns = Math.min(3, Math.max(1, tiles.size()));
        int cellWidth = 0;
        int cellHeight = 0;
        for (Bitmap tile : tiles) {
            cellWidth = Math.max(cellWidth, tile.getWidth());
            cellHeight = Math.max(cellHeight, tile.getHeight());
        }
        int rows = (tiles.size() + columns - 1) / columns;
        int width = GAP_PX + columns * (cellWidth + GAP_PX);
        int height = GAP_PX + rows * (cellHeight + LABEL_PX + GAP_PX);
        Bitmap sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.rgb(32, 34, 38));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(LABEL_PX * 0.7f);
        for (int index = 0; index < tiles.size(); index++) {
            int x = GAP_PX + (index % columns) * (cellWidth + GAP_PX);
            int y = GAP_PX + (index / columns) * (cellHeight + LABEL_PX + GAP_PX);
            canvas.drawText(labels.get(index), x, y + LABEL_PX * 0.75f, text);
            canvas.drawBitmap(tiles.get(index), x, y + LABEL_PX, null);
        }
        File file = new File(OUTPUT_DIR, name + "-sheet.png");
        write(sheet, file);
        return file;
    }

    private static void drawBackdrop(Canvas canvas, int width, int height) {
        Paint paint = new Paint();
        paint.setShader(new LinearGradient(0, 0, 0, height,
                Color.rgb(108, 170, 172), Color.rgb(84, 112, 168), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
    }

    private static String fileName(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + ".png";
    }

    private static void write(Bitmap bitmap, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
