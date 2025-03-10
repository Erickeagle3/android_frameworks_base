/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
 *               2018-2020 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;
import com.android.internal.graphics.palette.Palette;
import com.android.internal.util.fh.ColorAnimator;

public class VisualizerView extends View
        implements Palette.PaletteAsyncListener, TunerService.Tunable,
        ColorAnimator.ColorAnimationListener {

    private static final String TAG = VisualizerView.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String LOCKSCREEN_VISUALIZER_ENABLED =
            Settings.Secure.LOCKSCREEN_VISUALIZER_ENABLED;
    private static final String LOCKSCREEN_VISUALIZER_AUTOCOLOR =
            Settings.Secure.LOCKSCREEN_VISUALIZER_AUTOCOLOR;
    private static final String LOCKSCREEN_LAVALAMP_ENABLED =
            Settings.Secure.LOCKSCREEN_LAVALAMP_ENABLED;
    private static final String LOCKSCREEN_LAVALAMP_SPEED =
            Settings.Secure.LOCKSCREEN_LAVALAMP_SPEED;
    private static final String LOCKSCREEN_SOLID_UNITS_COUNT =
            Settings.Secure.LOCKSCREEN_SOLID_UNITS_COUNT;
    private static final String LOCKSCREEN_SOLID_FUDGE_FACTOR =
            Settings.Secure.LOCKSCREEN_SOLID_FUDGE_FACTOR;
    private static final String LOCKSCREEN_SOLID_UNITS_OPACITY =
            Settings.Secure.LOCKSCREEN_SOLID_UNITS_OPACITY;

    private Paint mPaint;
    private Visualizer mVisualizer;
    private ObjectAnimator mVisualizerColorAnimator;

    private ValueAnimator[] mValueAnimators;
    private float[] mFFTPoints;

    private int mStatusBarState;
    private boolean mVisualizerEnabled = false;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mDisplaying = false; // the state we're animating to
    private boolean mDozing = false;
    private boolean mOccluded = false;

    private int mColor;
    private Bitmap mCurrentBitmap;

    private ColorAnimator mLavaLamp;
    private boolean mAutoColor;
    private boolean mLavaLampEnabled;
    private int mLavaLampSpeed;
    private boolean shouldAnimate;
    private int mUnits = 32;
    private float mDbFuzzFactor = 16f;
    private int mWidth, mHeight;
    private int mOpacity = 140;

    private Visualizer.OnDataCaptureListener mVisualizerListener =
            new Visualizer.OnDataCaptureListener() {
        byte rfk, ifk;
        int dbValue;
        float magnitude;

        @Override
        public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
        }

        @Override
        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
            for (int i = 0; i < mUnits; i++) {
                mValueAnimators[i].cancel();
                rfk = fft[i * 2 + 2];
                ifk = fft[i * 2 + 3];
                magnitude = rfk * rfk + ifk * ifk;
                dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mValueAnimators[i].setFloatValues(mFFTPoints[i * 4 + 1],
                        mFFTPoints[3] - (dbValue * mDbFuzzFactor));
                mValueAnimators[i].start();
            }
        }
    };

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                Log.w(TAG, "+++ mLinkVisualizer run()");
            }

            try {
                if (mVisualizer == null) {
                    mVisualizer = new Visualizer(0);
                    shouldAnimate = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "error initializing visualizer", e);
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(66);
            mVisualizer.setDataCaptureListener(mVisualizerListener, Visualizer.getMaxCaptureRate(),
                    false, true);
            mVisualizer.setEnabled(true);

            if (DEBUG) {
                Log.w(TAG, "--- mLinkVisualizer run()");
            }
        }
    };

    private final Runnable mAsyncUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            AsyncTask.execute(mUnlinkVisualizer);
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                Log.w(TAG, "+++ mUnlinkVisualizer run(), mVisualizer: " + mVisualizer);
            }

            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
            }
            shouldAnimate = false;

            if (!mAutoColor && !mLavaLampEnabled) {
                if (mCurrentBitmap != null) {
                    setBitmap(null);
                } else {
                    setColor(Color.TRANSPARENT);
                }
            }

            if (DEBUG) {
                Log.w(TAG, "--- mUninkVisualizer run()");
            }
        }
    };

    public VisualizerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mColor = Color.TRANSPARENT;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mFFTPoints = new float[mUnits * 4];

        mLavaLamp = new ColorAnimator();
        mLavaLamp.setColorAnimatorListener(this);

        loadValueAnimators();
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerView(Context context) {
        this(context, null, 0);
    }

    private void updateViewVisibility() {
        final int curVis = getVisibility();
        final int newVis = mVisible && mStatusBarState != StatusBarState.SHADE
                && mVisualizerEnabled ? View.VISIBLE : View.GONE;
        if (curVis != newVis) {
            setVisibility(newVis);
            checkStateChanged();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        final TunerService tunerService = Dependency.get(TunerService.class);
        tunerService.addTunable(this, LOCKSCREEN_VISUALIZER_ENABLED);
        tunerService.addTunable(this, LOCKSCREEN_VISUALIZER_AUTOCOLOR);
        tunerService.addTunable(this, LOCKSCREEN_LAVALAMP_ENABLED);
        tunerService.addTunable(this, LOCKSCREEN_LAVALAMP_SPEED);
        tunerService.addTunable(this, LOCKSCREEN_SOLID_UNITS_COUNT);
        tunerService.addTunable(this, LOCKSCREEN_SOLID_FUDGE_FACTOR);
        tunerService.addTunable(this, LOCKSCREEN_SOLID_UNITS_OPACITY);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mLavaLamp.stop();
        Dependency.get(TunerService.class).removeTunable(this);
        mCurrentBitmap = null;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case LOCKSCREEN_VISUALIZER_ENABLED:
                mVisualizerEnabled =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case LOCKSCREEN_VISUALIZER_AUTOCOLOR:
                mAutoColor =
                        TunerService.parseIntegerSwitch(newValue, false);
                if (mCurrentBitmap != null && mAutoColor && !mLavaLampEnabled) {
                    Palette.generateAsync(mCurrentBitmap, this);
                } else if (mCurrentBitmap != null) {
                    setBitmap(null);
                } else {
                    setColor(Color.TRANSPARENT);
                }
                break;
            case LOCKSCREEN_LAVALAMP_ENABLED:
                mLavaLampEnabled =
                        TunerService.parseIntegerSwitch(newValue, true);
                break;
            case LOCKSCREEN_LAVALAMP_SPEED:
                mLavaLampSpeed = 10000;
                try {
                    mLavaLampSpeed = Integer.valueOf(newValue);
                } catch (NumberFormatException ex) {}
                mLavaLamp.setAnimationTime(mLavaLampSpeed);
                break;
            case LOCKSCREEN_SOLID_UNITS_COUNT:
                int oldUnits = mUnits;
                mUnits = 32;
                try {
                    mUnits = Integer.valueOf(newValue);
                } catch (NumberFormatException ex) {}
                if (mUnits != oldUnits) {
                    mFFTPoints = new float[mUnits * 4];
                    onSizeChanged(0, 0, 0, 0);
                }
                break;
            case LOCKSCREEN_SOLID_FUDGE_FACTOR:
                mDbFuzzFactor = 16f;
                try {
                    mDbFuzzFactor = Integer.valueOf(newValue);
                } catch (NumberFormatException ex) {}
                break;
            case LOCKSCREEN_SOLID_UNITS_OPACITY:
                mOpacity = 140;
                try {
                    mOpacity = Integer.valueOf(newValue);
                } catch (NumberFormatException ex) {}
                break;
            default:
                break;
        }
    }

    private void loadValueAnimators() {
        if (mValueAnimators != null) {
            for (int i = 0; i < mValueAnimators.length; i++) {
                mValueAnimators[i].cancel();
            }
        }
        mValueAnimators = new ValueAnimator[mUnits];
        for (int i = 0; i < mUnits; i++) {
            final int j = i * 4 + 1;
            mValueAnimators[i] = new ValueAnimator();
            mValueAnimators[i].setDuration(128);
            mValueAnimators[i].addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mFFTPoints[j] = (float) animation.getAnimatedValue();
                    postInvalidate();
                }
            });
        }
    }

    private void setPortraitPoints() {
        float units = Float.valueOf(mUnits);
        float barUnit = mWidth / units;
        float barWidth = barUnit * 8f / 9f;
        barUnit = barWidth + (barUnit - barWidth) * units / (units - 1);
        mPaint.setStrokeWidth(barWidth);

        for (int i = 0; i < mUnits; i++) {
            mFFTPoints[i * 4] = mFFTPoints[i * 4 + 2] = i * barUnit + (barWidth / 2);
            mFFTPoints[i * 4 + 1] = mHeight;
            mFFTPoints[i * 4 + 3] = mHeight;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w > 0) mWidth = w;
        if (h > 0) mHeight = h;

        super.onSizeChanged(mWidth, mHeight, oldw, oldh);

        loadValueAnimators();
        setPortraitPoints();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mVisualizer != null) {
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    @Override
    public void onColorChanged(ColorAnimator colorAnimator, int color) {
        if (mLavaLampEnabled)
            setColor(color);
    }

    @Override
    public void onStartAnimation(ColorAnimator colorAnimator, int firstColor) {
    }

    @Override
    public void onStopAnimation(ColorAnimator colorAnimator, int lastColor) {
    }

    public void setVisible(boolean visible) {
        if (DEBUG) {
            Log.i(TAG, "setVisible() called with visible = [" + visible + "]");
        }
        mVisible = visible;
        updateViewVisibility();
    }

    public void setDozing(boolean dozing) {
        if (mDozing != dozing) {
            if (DEBUG) {
                Log.i(TAG, "setDozing() called with dozing = [" + dozing + "]");
            }
            mDozing = dozing;
            checkStateChanged();
        }
    }

    public void setPlaying(boolean playing) {
        if (mPlaying != playing) {
            if (DEBUG) {
                Log.i(TAG, "setPlaying() called with playing = [" + playing + "]");
            }
            mPlaying = playing;
            checkStateChanged();
        }
    }

    public void setOccluded(boolean occluded) {
        if (mOccluded != occluded) {
            if (DEBUG) {
                Log.i(TAG, "setOccluded() called with occluded = [" + occluded + "]");
            }
            mOccluded = occluded;
            checkStateChanged();
        }
    }

    public void setStatusBarState(int statusBarState) {
        if (mStatusBarState != statusBarState) {
            mStatusBarState = statusBarState;
            updateViewVisibility();
        }
    }

    public void setBitmap(Bitmap bitmap) {
        if (mCurrentBitmap == bitmap)
            return;

        mCurrentBitmap = bitmap;

        if (mCurrentBitmap == null) {
            setColor(Color.TRANSPARENT);
        } else if (mAutoColor && !mLavaLampEnabled) {
            Palette.generateAsync(mCurrentBitmap, this);
        }
    }

    @Override
    public void onGenerated(Palette palette) {
        int color = Color.TRANSPARENT;

        color = palette.getVibrantColor(color);
        if (color == Color.TRANSPARENT) {
            color = palette.getLightVibrantColor(color);
            if (color == Color.TRANSPARENT) {
                color = palette.getDarkVibrantColor(color);
            }
        }

        setColor(color);
    }

    private void setColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = Color.WHITE;
        }

        color = Color.argb(mOpacity, Color.red(color), Color.green(color), Color.blue(color));

        if (mColor != color) {
            mColor = color;

            if (mVisualizer != null && shouldAnimate) {
                shouldAnimate = false;

                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }

                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color",
                        mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(600);
                mVisualizerColorAnimator.setDuration(1200);
                mVisualizerColorAnimator.start();
            }
            mPaint.setColor(mColor);
        }
    }

    private void checkStateChanged() {
        boolean isVisible = getVisibility() == View.VISIBLE;
        if (isVisible && mVisualizerEnabled && mPlaying &&
                !mDozing && !mOccluded) {
            if (!mDisplaying) {
                mDisplaying = true;
                AsyncTask.execute(mLinkVisualizer);
                animate()
                        .alpha(1f)
                        .withEndAction(null)
                        .setDuration(800);
                if (mLavaLampEnabled) mLavaLamp.start();
            }
        } else {
            if (mDisplaying) {
                mDisplaying = false;
                animate()
                        .alpha(0f)
                        .withEndAction(mAsyncUnlinkVisualizer)
                        .setDuration(isVisible ? 600 : 0);
            }
        }
    }
}
