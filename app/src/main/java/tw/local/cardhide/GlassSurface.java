package tw.local.cardhide;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A wallpaper-aligned, lightly refracting surface. Content is never blurred. */
final class GlassSurface extends Drawable {
    private final View root;
    private final Bitmap sample;
    private final float radius;
    private final boolean dark, glass, hasImage;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private int opacity = 255;
    private boolean pressed, selected;
    private View anchor;
    void setAnchor(View view) { anchor = view; }

    GlassSurface(View root, Bitmap original, Bitmap sample, float radius, boolean dark, boolean glass) {
        this.root = root; this.sample = sample; this.radius = radius;
        this.dark = dark; this.glass = glass; this.hasImage = original != null;
    }

    @Override public void draw(Canvas canvas) {
        RectF r = new RectF(getBounds());
        if (r.isEmpty()) return;
        Path clip = new Path(); clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        int save = canvas.save(); canvas.clipPath(clip);
        if (glass && sample != null && (anchor != null || getCallback() instanceof View) && root.getWidth() > 0) {
            View host = anchor != null ? anchor : (View) getCallback();
            int[] position = new int[2], origin = new int[2];
            host.getLocationOnScreen(position); root.getLocationOnScreen(origin);
            float scale = Math.max((float) root.getWidth() / sample.getWidth(), (float) root.getHeight() / sample.getHeight());
            float left = (root.getWidth() - sample.getWidth() * scale) / 2f - (position[0] - origin[0]);
            float top = (root.getHeight() - sample.getHeight() * scale) / 2f - (position[1] - origin[1]);
            int lens = canvas.save();
            canvas.scale(pressed ? 1.025f : 1.012f, pressed ? 1.025f : 1.012f, r.centerX(), r.centerY());
            paint.setShader(null); paint.setColor(Color.WHITE); paint.setAlpha(opacity);
            canvas.drawBitmap(sample, null, new RectF(left, top, left + sample.getWidth() * scale, top + sample.getHeight() * scale), paint);
            canvas.restoreToCount(lens);
        }
        // Low opacity lets the picture remain visible even with the glass switch off.
        int top = dark ? (hasImage ? 0x60101017 : 0x70272732) : (hasImage ? 0x75ffffff : 0x8affffff);
        int bottom = dark ? 0x38101017 : 0x28ffffff;
        if (!glass) { top = dark ? 0x50101017 : 0x60ffffff; bottom = top; }
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, top, bottom, Shader.TileMode.CLAMP));
        paint.setAlpha(opacity); canvas.drawRect(r, paint); paint.setShader(null);
        if (selected || pressed) {
            paint.setColor(dark ? 0x26ffffff : 0x26000000); canvas.drawRect(r, paint);
        }
        if (glass) {
            paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0x36ffffff, 0x04ffffff, 0x00ffffff, 0x14ffffff}, new float[]{0, 0.28f, 0.65f, 1}, Shader.TileMode.CLAMP));
            canvas.drawRect(r, paint); paint.setShader(null);
        }
        canvas.restoreToCount(save);
        float density = root.getResources().getDisplayMetrics().density;
        r.inset(density * 0.6f, density * 0.6f);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density * (glass ? 1.2f : 0.7f));
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                glass ? new int[]{0xcfffffff, 0x18ffffff, 0x80ffffff} : new int[]{0x40ffffff, 0x20ffffff},
                null, Shader.TileMode.CLAMP));
        paint.setAlpha(opacity); canvas.drawRoundRect(r, radius, radius, paint);
        paint.setShader(null); paint.setStyle(Paint.Style.FILL);
    }

    @Override public void getOutline(Outline outline) { outline.setRoundRect(getBounds(), radius); }
    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] state) {
        boolean p = false, s = false;
        for (int value : state) { p |= value == android.R.attr.state_pressed; s |= value == android.R.attr.state_selected; }
        boolean changed = p != pressed || s != selected; pressed = p; selected = s;
        if (changed) invalidateSelf(); return changed;
    }
    @Override public void setAlpha(int alpha) { opacity = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    static Bitmap softSample(Bitmap source) {
        float ratio = Math.min(1f, 384f / Math.max(source.getWidth(), source.getHeight()));
        Bitmap small = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * ratio)),
                Math.max(1, Math.round(source.getHeight() * ratio)), true);
        int w = small.getWidth(), h = small.getHeight();
        int[] pixels = new int[w * h], out = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int red = 0, green = 0, blue = 0;
                for (int k = -2; k <= 2; k++) {
                    int i = pass == 0 ? y * w + Math.max(0, Math.min(w - 1, x + k))
                            : Math.max(0, Math.min(h - 1, y + k)) * w + x;
                    int c = pixels[i]; red += Color.red(c); green += Color.green(c); blue += Color.blue(c);
                }
                out[y * w + x] = Color.rgb(red / 5, green / 5, blue / 5);
            }
            int[] swap = pixels; pixels = out; out = swap;
        }
        Bitmap result = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        if (small != source) small.recycle();
        return result;
    }
}
