package org.omarchy.gliff;

/** One letterbox calculation shared by display, cursor and pointer input. */
public final class Viewport {
    public final float left, top, width, height;
    private final double logicalWidth, logicalHeight;

    public Viewport(int windowWidth, int windowHeight, int streamWidth, int streamHeight,
                    int viewWidth, int viewHeight, int scaleMilli) {
        if (windowWidth < 1 || windowHeight < 1 || streamWidth < 1 || streamHeight < 1
                || viewWidth < 1 || viewHeight < 1 || scaleMilli < 1) throw new IllegalArgumentException("Invalid viewport");
        float fit = Math.min((float) windowWidth / viewWidth, (float) windowHeight / viewHeight);
        width = viewWidth * fit;
        height = viewHeight * fit;
        left = (windowWidth - width) / 2;
        top = (windowHeight - height) / 2;
        logicalWidth = streamWidth * 1000.0 / scaleMilli;
        logicalHeight = streamHeight * 1000.0 / scaleMilli;
    }

    public boolean contains(float x, float y) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }
    public double remoteX(float x) { return Math.max(0, Math.min(logicalWidth - 0.001, (x - left) / width * logicalWidth)); }
    public double remoteY(float y) { return Math.max(0, Math.min(logicalHeight - 0.001, (y - top) / height * logicalHeight)); }
    public float localX(double x) { return left + (float) (x / logicalWidth * width); }
    public float localY(double y) { return top + (float) (y / logicalHeight * height); }
}
