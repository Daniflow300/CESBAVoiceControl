package com.cesba.voicecontrol;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Onda de voz animada. Reacciona al volumen del microfono
 * (onRmsChanged) y respira suavemente cuando esta en reposo.
 */
public class WaveView extends View {

    private static final int BARRAS = 30;

    private final float[] alturas = new float[BARRAS];
    private final Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float nivel = 0f;
    private boolean activo = false;

    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        pincel.setColor(0xFFF0B323);
        pincel.setStrokeCap(Paint.Cap.ROUND);
    }

    /** rmsdB tipicamente va de -2 a 10. */
    public void setLevel(float rmsdB) {
        float n = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f));
        if (n > nivel) nivel = n;
    }

    public void setActive(boolean activo) {
        this.activo = activo;
        if (!activo) nivel = 0f;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float paso = w / (float) BARRAS;
        float grosor = paso * 0.45f;
        pincel.setStrokeWidth(grosor);

        long t = SystemClock.uptimeMillis();
        boolean moviendose = activo;

        for (int i = 0; i < BARRAS; i++) {
            float fase = (float) (Math.sin(t / 170.0 + i * 0.55) * 0.5 + 0.5);
            float centro = 1f - Math.abs((i - BARRAS / 2f) / (BARRAS / 2f)) * 0.55f;

            float objetivo;
            if (activo) {
                objetivo = 0.10f + nivel * centro * (0.35f + 0.65f * fase);
            } else {
                objetivo = 0.05f + 0.025f * fase;
            }

            alturas[i] += (objetivo - alturas[i]) * 0.25f;
            if (Math.abs(objetivo - alturas[i]) > 0.01f) moviendose = true;

            float alto = Math.max(h * alturas[i], grosor);
            float x = paso * (i + 0.5f);

            pincel.setAlpha(activo ? 255 : 90);
            canvas.drawLine(x, h / 2f - alto / 2f, x, h / 2f + alto / 2f, pincel);
        }

        nivel *= 0.93f;

        if (moviendose) postInvalidateOnAnimation();
    }
}
