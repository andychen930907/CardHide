package tw.local.cardhide;

/** Geometry shared by the crop gestures and the saved pixel selection. */
public final class CropMath {
    private CropMath() {}
    public static float clamp(float value, float low, float high) {
        return Math.max(low, Math.min(high, value));
    }
    public static float minimumScale(float imageW, float imageH, float cropW, float cropH) {
        return Math.max(cropW / imageW, cropH / imageH);
    }
    public static float offset(float value, float scaledLength, float cropStart, float cropEnd) {
        return clamp(value, cropEnd - scaledLength, cropStart);
    }
    public static int[] sourceRect(float left, float top, float right, float bottom,
                                   float x, float y, float scale, int imageW, int imageH) {
        int l = (int) clamp((left - x) / scale, 0, imageW - 1);
        int t = (int) clamp((top - y) / scale, 0, imageH - 1);
        int r = (int) Math.ceil(clamp((right - x) / scale, l + 1, imageW));
        int b = (int) Math.ceil(clamp((bottom - y) / scale, t + 1, imageH));
        return new int[]{l, t, r, b};
    }
}
