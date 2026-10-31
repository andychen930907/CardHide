package tw.local.cardhide;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;

public final class CropActivity extends Activity {
    private CropView crop;
    private TextView done, ratioButton, hint;
    private Bitmap image;
    private boolean saving;
    private float screenRatio;
    private Bundle restored;

    @Override public void onCreate(Bundle state) {
        setTheme(getResources().getIdentifier("CardHideDark", "style", getPackageName()));
        super.onCreate(state);
        restored = state;
        Rect screen = getWindowManager().getCurrentWindowMetrics().getBounds();
        screenRatio = (float) screen.width() / screen.height();
        getWindow().setStatusBarColor(0xff111114); getWindow().setNavigationBarColor(0xff111114);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL); body.setBackgroundColor(0xff111114);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView cancel = button("取消"); cancel.setOnClickListener(v -> { if (!saving) finish(); });
        bar.addView(cancel, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView title = button("裁切背景");
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 2));
        done = button("套用"); done.setTextColor(0xffbed4ff); done.setEnabled(false);
        done.setOnClickListener(v -> saveCrop());
        bar.addView(done, new LinearLayout.LayoutParams(0, dp(56), 1));
        body.addView(bar);
        crop = new CropView(); body.addView(crop, new LinearLayout.LayoutParams(-1, 0, 1));
        hint = button("正在讀取圖片…"); hint.setTextSize(13);
        body.addView(hint, new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout options = new LinearLayout(this);
        ratioButton = button("螢幕比例 ✓");
        ratioButton.setOnClickListener(v -> {
            if (image == null || saving) return;
            crop.free = !crop.free;
            if (!crop.free) crop.reset();
            ratioButton.setText(crop.free ? "自由比例 ✓" : "螢幕比例 ✓");
            hint.setText(crop.free ? "拖曳四角調整裁切框；填滿螢幕時可能再裁掉邊緣" : "拖曳圖片調整位置 · 雙指縮放 · 拖曳四角裁切");
        });
        options.addView(ratioButton, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView reset = button("重設構圖"); reset.setOnClickListener(v -> { if (image != null && !saving) crop.reset(); });
        options.addView(reset, new LinearLayout.LayoutParams(0, dp(56), 1));
        body.addView(options); setContentView(body);
        new Thread(() -> {
            try {
                if (getIntent().getData() == null) throw new java.io.IOException("No image");
                Bitmap decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), getIntent().getData()),
                        (decoder, info, source) -> {
                            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                            float scale = Math.min(1f, 3072f / Math.max(w, h));
                            decoder.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        });
                runOnUiThread(() -> {
                    if (isDestroyed()) { decoded.recycle(); return; }
                    image = decoded; crop.reset();
                    if (restored != null) {
                        crop.free = restored.getBoolean("free", false);
                        float[] region = restored.getFloatArray("region");
                        if (region != null) crop.restoreRegion(region);
                        if (!crop.frame.isEmpty()) restored = null;
                    }
                    done.setEnabled(true);
                    ratioButton.setText(crop.free ? "自由比例 ✓" : "螢幕比例 ✓");
                    hint.setText("拖曳圖片調整位置 · 雙指縮放 · 拖曳四角裁切");
                });
            } catch (Exception | OutOfMemoryError e) {
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        Toast.makeText(this, "圖片讀取失敗，原背景已保留", Toast.LENGTH_LONG).show(); finish();
                    }
                });
            }
        }, "decode-crop").start();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (image != null && !crop.frame.isEmpty()) {
            state.putBoolean("free", crop.free); state.putFloatArray("region", crop.region());
        }
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() { if (!saving) super.onBackPressed(); }

    private void saveCrop() {
        if (image == null || saving || crop.frame.isEmpty()) return;
        saving = true; done.setEnabled(false); done.setText("儲存中");
        int[] bounds = crop.pixelRegion();
        Bitmap source = image;
        // Hold the current orientation only during the atomic file write.
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        new Thread(() -> {
            File temp = new File(getFilesDir(), "wallpaper-crop.tmp");
            try {
                Bitmap part = Bitmap.createBitmap(source, bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1]);
                float scale = Math.min(1f, 2560f / Math.max(part.getWidth(), part.getHeight()));
                Bitmap output = Bitmap.createScaledBitmap(part, Math.max(1, Math.round(part.getWidth() * scale)),
                        Math.max(1, Math.round(part.getHeight() * scale)), true);
                try (FileOutputStream stream = new FileOutputStream(temp)) {
                    if (!output.compress(Bitmap.CompressFormat.JPEG, 94, stream)) throw new java.io.IOException("Cannot encode");
                    stream.getFD().sync();
                }
                if (!temp.renameTo(new File(getFilesDir(), "wallpaper.jpg"))) throw new java.io.IOException("Cannot save");
                if (output != part && output != source) output.recycle();
                if (part != source) part.recycle();
                runOnUiThread(() -> { saving = false; setResult(RESULT_OK); finish(); });
            } catch (Exception | OutOfMemoryError error) {
                temp.delete();
                runOnUiThread(() -> {
                    saving = false; done.setEnabled(true); done.setText("套用");
                    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    Toast.makeText(this, "儲存失敗，原背景已保留", Toast.LENGTH_LONG).show();
                });
            }
        }, "save-crop").start();
    }

    private TextView button(String text) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(15);
        view.setTextColor(Color.WHITE); view.setGravity(Gravity.CENTER); view.setPadding(dp(6), 0, dp(6), 0);
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class CropView extends View {
        final RectF frame = new RectF();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final ScaleGestureDetector zoom;
        float scale = 1, x, y, lastX, lastY;
        boolean free;
        int corner = -1;

        CropView() {
            super(CropActivity.this);
            setContentDescription("背景裁切區：拖曳移動圖片，雙指縮放，拖曳四角調整範圍");
            zoom = new ScaleGestureDetector(CropActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    if (image == null || frame.isEmpty()) return false;
                    float min = CropMath.minimumScale(image.getWidth(), image.getHeight(), frame.width(), frame.height());
                    float next = CropMath.clamp(scale * detector.getScaleFactor(), min, min * 12);
                    float factor = next / scale;
                    x = detector.getFocusX() - (detector.getFocusX() - x) * factor;
                    y = detector.getFocusY() - (detector.getFocusY() - y) * factor;
                    scale = next; corner = -1; constrain(); invalidate(); return true;
                }
            });
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            float[] old = image == null || frame.isEmpty() ? null : region();
            reset();
            if (old != null) restoreRegion(old);
            else if (image != null && restored != null) {
                free = restored.getBoolean("free", false); reset();
                float[] region = restored.getFloatArray("region");
                if (region != null) restoreRegion(region);
                restored = null;
            }
        }

        void reset() {
            if (image == null || getWidth() <= dp(40) || getHeight() <= dp(40)) return;
            float ratio = free ? (float) image.getWidth() / image.getHeight() : screenRatio;
            float w = getWidth() - dp(40), h = w / ratio;
            if (h > getHeight() - dp(40)) { h = getHeight() - dp(40); w = h * ratio; }
            frame.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
            scale = CropMath.minimumScale(image.getWidth(), image.getHeight(), w, h);
            x = frame.centerX() - image.getWidth() * scale / 2; y = frame.centerY() - image.getHeight() * scale / 2;
            invalidate();
        }

        void restoreRegion(float[] region) {
            if (image == null || frame.isEmpty()) return;
            float rw = region[2] - region[0], rh = region[3] - region[1];
            if (rw <= 0 || rh <= 0) return;
            if (free) {
                float ratio = rw / rh, w = getWidth() - dp(40), h = w / ratio;
                if (h > getHeight() - dp(40)) { h = getHeight() - dp(40); w = h * ratio; }
                frame.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
            }
            scale = Math.max(frame.width() / rw, frame.height() / rh);
            x = frame.centerX() - (region[0] + region[2]) * scale / 2;
            y = frame.centerY() - (region[1] + region[3]) * scale / 2;
            constrain(); invalidate();
        }

        float[] region() { return new float[]{(frame.left - x) / scale, (frame.top - y) / scale,
                (frame.right - x) / scale, (frame.bottom - y) / scale}; }
        int[] pixelRegion() { return CropMath.sourceRect(frame.left, frame.top, frame.right, frame.bottom,
                x, y, scale, image.getWidth(), image.getHeight()); }
        void constrain() {
            float min = CropMath.minimumScale(image.getWidth(), image.getHeight(), frame.width(), frame.height());
            scale = Math.max(scale, min);
            x = CropMath.offset(x, image.getWidth() * scale, frame.left, frame.right);
            y = CropMath.offset(y, image.getHeight() * scale, frame.top, frame.bottom);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); if (image == null || frame.isEmpty()) return;
            paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.FILL);
            c.drawBitmap(image, null, new RectF(x, y, x + image.getWidth() * scale, y + image.getHeight() * scale), paint);
            paint.setColor(0xb0000000);
            c.drawRect(0, 0, getWidth(), frame.top, paint); c.drawRect(0, frame.bottom, getWidth(), getHeight(), paint);
            c.drawRect(0, frame.top, frame.left, frame.bottom, paint); c.drawRect(frame.right, frame.top, getWidth(), frame.bottom, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0xeeffffff);
            c.drawRect(frame, paint); paint.setColor(0x70ffffff);
            for (int i = 1; i < 3; i++) {
                float tx = frame.left + frame.width() * i / 3, ty = frame.top + frame.height() * i / 3;
                c.drawLine(tx, frame.top, tx, frame.bottom, paint); c.drawLine(frame.left, ty, frame.right, ty, paint);
            }
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            c.drawCircle(frame.left, frame.top, dp(5), paint); c.drawCircle(frame.right, frame.top, dp(5), paint);
            c.drawCircle(frame.left, frame.bottom, dp(5), paint); c.drawCircle(frame.right, frame.bottom, dp(5), paint);
        }

        private void resize(float tx, float ty) {
            boolean left = corner == 0 || corner == 2, top = corner < 2;
            float anchorX = left ? frame.right : frame.left, anchorY = top ? frame.bottom : frame.top;
            float maxW = left ? anchorX - dp(12) : getWidth() - dp(12) - anchorX;
            float maxH = top ? anchorY - dp(12) : getHeight() - dp(12) - anchorY;
            float w = CropMath.clamp(left ? anchorX - tx : tx - anchorX, dp(48), maxW);
            float h = CropMath.clamp(top ? anchorY - ty : ty - anchorY, dp(48), maxH);
            if (!free) {
                w = Math.max(w, h * screenRatio);
                w = Math.min(w, Math.min(maxW, maxH * screenRatio)); h = w / screenRatio;
            }
            frame.set(left ? anchorX - w : anchorX, top ? anchorY - h : anchorY,
                    left ? anchorX : anchorX + w, top ? anchorY : anchorY + h);
            constrain(); invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (image == null || saving) return true;
            zoom.onTouchEvent(event);
            float tx = event.getX(), ty = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    corner = -1; lastX = tx; lastY = ty;
                    float[] corners = {frame.left, frame.top, frame.right, frame.top, frame.left, frame.bottom, frame.right, frame.bottom};
                    for (int i = 0; i < 4; i++) if (Math.hypot(tx - corners[i * 2], ty - corners[i * 2 + 1]) < dp(28)) { corner = i; break; }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!zoom.isInProgress() && event.getPointerCount() == 1) {
                        if (corner >= 0) resize(tx, ty);
                        else { x += tx - lastX; y += ty - lastY; constrain(); invalidate(); }
                    }
                    lastX = tx; lastY = ty; break;
                case MotionEvent.ACTION_POINTER_UP:
                    int remaining = event.getActionIndex() == 0 ? 1 : 0;
                    lastX = event.getX(remaining); lastY = event.getY(remaining); corner = -1; break;
                case MotionEvent.ACTION_UP: corner = -1; performClick(); break;
                case MotionEvent.ACTION_CANCEL: corner = -1; break;
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
