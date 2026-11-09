package org.omarchy.gliff;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.Surface;
import android.view.TextureView;
import android.graphics.SurfaceTexture;
import android.widget.FrameLayout;
import android.view.View;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.omarchy.gliff.Protocol.*;

public final class DesktopView extends FrameLayout implements TextureView.SurfaceTextureListener {
    public interface Listener {
        void surfaceReady(Surface surface);
        void surfaceLost();
        void resized(int width, int height);
        void grabRequested();
        boolean isGrabbed();
        void send(int type, Object... fields);
        void button(int code, boolean pressed);
        void mouseButtons(int action, int mask);
        void releaseInput();
    }
    private final TextureView texture;
    private final View cursor;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Surface surface;
    private int streamW = 1920, streamH = 1080, viewW = 1920, viewH = 1080, scale = 1000;
    private float mouseX, mouseY;
    private Bitmap cursorBitmap;
    private int hotX, hotY;
    private boolean cursorVisible = true, mouseOver;
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Runnable resize;

    public DesktopView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        resize = () -> listener.resized(getWidth(), getHeight());
        setBackgroundColor(Color.BLACK);
        setFocusable(true);
        setFocusableInTouchMode(true);
        texture = new TextureView(context);
        texture.setSurfaceTextureListener(this);
        addView(texture);
        cursor = new View(context) {
            @Override protected void onDraw(Canvas canvas) {
                if (!cursorVisible || !mouseOver) return;
                Viewport v = viewport();
                if (cursorBitmap != null) {
                    float sx = v.width / viewW, sy = v.height / viewH;
                    float x = mouseX - hotX * sx, y = mouseY - hotY * sy;
                    canvas.drawBitmap(cursorBitmap, null,
                            new RectF(x, y, x + cursorBitmap.getWidth() * sx, y + cursorBitmap.getHeight() * sy), cursorPaint);
                } else {
                    android.graphics.Path arrow = new android.graphics.Path();
                    arrow.moveTo(mouseX, mouseY); arrow.lineTo(mouseX + 4, mouseY + 19);
                    arrow.lineTo(mouseX + 8, mouseY + 12); arrow.lineTo(mouseX + 16, mouseY + 12); arrow.close();
                    cursorPaint.setColor(Color.WHITE); cursorPaint.setStyle(Paint.Style.FILL); canvas.drawPath(arrow, cursorPaint);
                    cursorPaint.setColor(Color.BLACK); cursorPaint.setStyle(Paint.Style.STROKE); cursorPaint.setStrokeWidth(1); canvas.drawPath(arrow, cursorPaint);
                    cursorPaint.setStyle(Paint.Style.FILL);
                }
            }
        };
        cursor.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(cursor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setContentDescription("Remote Omarchy desktop. Click to capture input. Shift Escape releases input.");
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = new Surface(st);
        listener.surfaceReady(surface);
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        listener.surfaceLost();
        if (surface != null) { surface.release(); surface = null; }
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    public void configure(int w, int h, int vw, int vh, int scaleMilli) {
        streamW = w; streamH = h; viewW = vw; viewH = vh; scale = scaleMilli;
        requestLayout();
        cursor.invalidate();
    }

    public Viewport viewport() {
        return new Viewport(Math.max(1, getWidth()), Math.max(1, getHeight()), streamW, streamH, viewW, viewH, scale);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Viewport v = viewport();
        texture.layout(Math.round(v.left), Math.round(v.top), Math.round(v.left + v.width), Math.round(v.top + v.height));
        cursor.layout(0, 0, getWidth(), getHeight());
        if (changed) { main.removeCallbacks(resize); main.postDelayed(resize, 250); }
    }

    public void capture(boolean enabled) {
        requestFocus();
        setPointerIcon(PointerIcon.getSystemIcon(getContext(), enabled ? PointerIcon.TYPE_NULL : PointerIcon.TYPE_DEFAULT));
        if (enabled && hasWindowFocus()) {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice device = InputDevice.getDevice(id);
                if (device != null && !device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)
                        && (device.supportsSource(InputDevice.SOURCE_MOUSE) || device.supportsSource(InputDevice.SOURCE_TOUCHPAD))) {
                    requestPointerCapture();
                    break;
                }
            }
        } else releasePointerCapture();
        cursor.invalidate();
    }

    @Override public void onPointerCaptureChange(boolean hasCapture) {
        super.onPointerCaptureChange(hasCapture);
        if (!hasCapture) listener.releaseInput();
    }

    private boolean motion(MotionEvent e, boolean relative) {
        if (getWidth() == 0 || getHeight() == 0) return true;
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_EXIT && !relative) { mouseOver = false; cursor.invalidate(); return true; }
        if (!listener.isGrabbed()) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS) {
                listener.grabRequested();
            } else return true;
        }
        Viewport v = viewport();
        if (relative) {
            boolean touchpad = e.isFromSource(InputDevice.SOURCE_TOUCHPAD);
            mouseX += touchpad ? e.getAxisValue(MotionEvent.AXIS_RELATIVE_X) : e.getX();
            mouseY += touchpad ? e.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) : e.getY();
        } else {
            if (!v.contains(e.getX(), e.getY()) && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
                    && action != MotionEvent.ACTION_BUTTON_RELEASE) return true;
            mouseX = e.getX(); mouseY = e.getY();
        }
        mouseX = Math.max(v.left, Math.min(v.left + v.width - 0.01f, mouseX));
        mouseY = Math.max(v.top, Math.min(v.top + v.height - 0.01f, mouseY));
        mouseOver = true;
        listener.send(3, v.remoteX(mouseX), v.remoteY(mouseY));
        if (action == MotionEvent.ACTION_CANCEL) { listener.releaseInput(); return true; }
        boolean mouse = e.isFromSource(InputDevice.SOURCE_MOUSE) || relative;
        if (mouse) {
            listener.mouseButtons(action, e.getButtonState());
        } else {
            if (action == MotionEvent.ACTION_DOWN) listener.button(272, true);
            if (action == MotionEvent.ACTION_UP) { listener.button(272, false); performClick(); }
        }
        if (action == MotionEvent.ACTION_SCROLL) {
            scroll(0, -e.getAxisValue(MotionEvent.AXIS_VSCROLL));
            scroll(1, -e.getAxisValue(MotionEvent.AXIS_HSCROLL));
        }
        cursor.invalidate();
        return true;
    }

    private void scroll(int axis, float steps) {
        if (steps != 0) {
            listener.send(5, axis, (double) steps * 15, Math.round(steps), false);
            listener.send(5, axis, 0.0, null, true);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) { return motion(e, false); }
    @Override public boolean onGenericMotionEvent(MotionEvent e) { return motion(e, false); }
    @Override public boolean onHoverEvent(MotionEvent e) { return motion(e, false); }
    @Override public boolean onCapturedPointerEvent(MotionEvent e) { return motion(e, true); }
    @Override public boolean performClick() { super.performClick(); return true; }

    public void cursor(Frame frame) throws IOException {
        Object[] f = frame.fields;
        if (frame.type == 3) {
            int w = integer(f, 1), h = integer(f, 2);
            if (w > 512 || h > 512 || (long) w * h * 4 != frame.payload.length || integer(f, 5) != frame.payload.length)
                throw new IOException("Invalid remote cursor");
            int hx = (int) number(f, 3), hy = (int) number(f, 4);
            if (hx < 0 || hy < 0 || hx > w || hy > h) throw new IOException("Invalid cursor hotspot");
            Bitmap bitmap = null;
            if (w > 0 && h > 0) {
                int[] pixels = new int[w * h];
                ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(pixels);
                bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
            }
            Bitmap image = bitmap;
            main.post(() -> { cursorBitmap = image; hotX = hx; hotY = hy; cursor.invalidate(); });
        } else {
            boolean visible = bool(f, 3);
            main.post(() -> { cursorVisible = visible; cursor.invalidate(); });
        }
    }
}
