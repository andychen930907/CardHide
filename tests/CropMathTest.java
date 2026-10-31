import tw.local.cardhide.CropMath;

public final class CropMathTest {
    public static void main(String[] args) {
        near(CropMath.minimumScale(4000, 2000, 600, 900), .45f);
        near(CropMath.offset(500, 1800, 30, 630), 30);
        near(CropMath.offset(-3000, 1800, 30, 630), -1170);
        int[] all = CropMath.sourceRect(0, 0, 600, 900, 0, 0, .3f, 2000, 3000);
        rect(all, 0, 0, 2000, 3000);
        int[] zoom = CropMath.sourceRect(100, 200, 500, 800, -100, -200, 2, 1000, 1000);
        rect(zoom, 100, 200, 300, 500);
        int[] edge = CropMath.sourceRect(-1, -1, 2001, 3001, 0, 0, 1, 2000, 3000);
        rect(edge, 0, 0, 2000, 3000);
        // Fuzz the real save geometry after arbitrary zoom and pan gestures.
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < 10000; i++) {
            int iw = 1 + random.nextInt(4000), ih = 1 + random.nextInt(4000);
            float l = random.nextFloat() * 500, t = random.nextFloat() * 500;
            float w = 10 + random.nextFloat() * 800, h = 10 + random.nextFloat() * 800;
            float scale = CropMath.minimumScale(iw, ih, w, h) * (1 + random.nextFloat() * 11);
            float x = CropMath.offset(random.nextFloat() * 10000 - 5000, iw * scale, l, l + w);
            float y = CropMath.offset(random.nextFloat() * 10000 - 5000, ih * scale, t, t + h);
            int[] r = CropMath.sourceRect(l, t, l + w, t + h, x, y, scale, iw, ih);
            if (r[0] < 0 || r[1] < 0 || r[2] > iw || r[3] > ih || r[2] <= r[0] || r[3] <= r[1])
                throw new AssertionError("Out-of-bounds crop after constrained pan");
        }
        System.out.println("PASS: crop edges, zoom, pan and 10000 randomized bounded selections");
    }
    private static void near(float a, float b) { if (Math.abs(a - b) > .001f) throw new AssertionError(a + " != " + b); }
    private static void rect(int[] r, int l, int t, int right, int bottom) {
        if (!java.util.Arrays.equals(r, new int[]{l, t, right, bottom})) throw new AssertionError(java.util.Arrays.toString(r));
    }
}
