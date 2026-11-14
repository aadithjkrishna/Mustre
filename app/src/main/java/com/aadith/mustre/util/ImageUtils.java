package com.aadith.mustre.util;

import android.graphics.Bitmap;

public class ImageUtils {

    /**
     * Determines whether a bitmap is predominantly light by computing average pixel luminance.
     */
    public static boolean isBitmapLight(Bitmap bitmap) {
        if (bitmap == null) return false;
        try {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 16, 16, false);
            long luminanceSum = 0;
            int count = 16 * 16;
            int[] pixels = new int[count];
            scaled.getPixels(pixels, 0, 16, 0, 0, 16, 16);

            for (int pixel : pixels) {
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                double luminance = (0.299 * r + 0.587 * g + 0.114 * b);
                luminanceSum += (long) luminance;
            }

            double avgLuminance = luminanceSum / (double) count;
            if (scaled != bitmap && !scaled.isRecycled()) {
                scaled.recycle();
            }
            return avgLuminance > 150;
        } catch (Exception e) {
            return false;
        }
    }
}
