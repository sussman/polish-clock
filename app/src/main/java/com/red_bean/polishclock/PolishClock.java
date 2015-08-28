/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.red_bean.polishclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class PolishClock extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        Paint mBackgroundPaint;
        Paint mHandPaint;
        Bitmap mBackgroundBitmap;
        Bitmap mBackgroundScaledBitmap;
        Bitmap mHourHandBitmap;
        Bitmap mHourHandScaledBitmap;
        Bitmap mMinuteHandBitmap;
        Bitmap mMinuteHandScaledBitmap;
        Bitmap mSecondHandBitmap;
        Bitmap mSecondHandScaledBitmap;
        Matrix mMatrix;

        boolean mAmbient;
        Time mTime;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(PolishClock.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setStatusBarGravity(Gravity.BOTTOM)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = PolishClock.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTime = new Time();

            mMatrix = new Matrix();

            // load the background image just once
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.polclock, null);
            mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

            // load the watch hand images just once
            Drawable hourHandDrawable = resources.getDrawable(R.drawable.hourhand, null);
            mHourHandBitmap = ((BitmapDrawable) hourHandDrawable).getBitmap();
            Drawable minuteHandDrawable = resources.getDrawable(R.drawable.minutehand, null);
            mMinuteHandBitmap = ((BitmapDrawable) minuteHandDrawable).getBitmap();
            Drawable secondHandDrawable = resources.getDrawable(R.drawable.secondhand, null);
            mSecondHandBitmap = ((BitmapDrawable) secondHandDrawable).getBitmap();

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();
            float millis = (float) (mTime.toMillis(false));

            int width = bounds.width();
            int height = bounds.height();

            // Draw the background color, though mostly pointless.
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);

            // Draw the bitmap of the Polish Clock Face, scaled appropriately.
            if (mBackgroundScaledBitmap == null
                    || mBackgroundScaledBitmap.getWidth() != width
                    || mBackgroundScaledBitmap.getHeight() != height) {

                int scaledHeight =
                        (height * mBackgroundBitmap.getHeight()) / mBackgroundBitmap.getWidth();
                mBackgroundScaledBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                        width, scaledHeight, true);
            }
            canvas.drawBitmap(mBackgroundScaledBitmap, 0, 0, null);

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = width / 2f;
            float centerY = height / 2f;

            // Draw hour hand bitmap, scaled and rotated appropriately
            if (mHourHandScaledBitmap == null
                    || mHourHandScaledBitmap.getWidth() != width
                    || mHourHandScaledBitmap.getHeight() != height) {

                int scaledHeight =
                        (height * mHourHandBitmap.getHeight()) / mHourHandBitmap.getWidth();
                mHourHandScaledBitmap = Bitmap.createScaledBitmap(mHourHandBitmap,
                        width, scaledHeight, true);
            }
            float hours = (float) (((millis / 1000.0 / 3600.0) % 1) + mTime.hour);
            float angle = (float)((360.0 / 12.0) * hours);
            mMatrix.reset();
            mMatrix.postTranslate(-mHourHandScaledBitmap.getWidth() / 2, -mHourHandScaledBitmap.getHeight() / 2); // Centers image
            mMatrix.postRotate(angle);
            mMatrix.postTranslate(centerX, centerY);
            canvas.drawBitmap(mHourHandScaledBitmap, mMatrix, null);

            // Draw minute hand bitmap, scaled and rotated appropriately
            if (mMinuteHandScaledBitmap == null
                    || mMinuteHandScaledBitmap.getWidth() != width
                    || mMinuteHandScaledBitmap.getHeight() != height) {

                int scaledHeight =
                        (height * mMinuteHandBitmap.getHeight()) / mMinuteHandBitmap.getWidth();
                mMinuteHandScaledBitmap = Bitmap.createScaledBitmap(mMinuteHandBitmap,
                        width, scaledHeight, true);
            }
            angle = ((float)(360.0 / 60.0)) * mTime.minute + ((float)(mTime.second / 10.0));
            mMatrix.reset();
            mMatrix.postTranslate(-mMinuteHandScaledBitmap.getWidth() / 2, -mMinuteHandScaledBitmap.getHeight() / 2); // Centers image
            mMatrix.postRotate(angle);
            mMatrix.postTranslate(centerX, centerY);
            canvas.drawBitmap(mMinuteHandScaledBitmap, mMatrix, null);

            // Draw second hand bitmap, scaled and rotated appropriately
            if (mAmbient) {
                return;
            }
            if (mSecondHandScaledBitmap == null
                    || mSecondHandScaledBitmap.getWidth() != width
                    || mSecondHandScaledBitmap.getHeight() != height) {

                int scaledHeight =
                        (height * mSecondHandBitmap.getHeight()) / mSecondHandBitmap.getWidth();
                mSecondHandScaledBitmap = Bitmap.createScaledBitmap(mSecondHandBitmap,
                        width, scaledHeight, true);
            }
            angle = (float)((360.0 / 60.0) * mTime.second);
            mMatrix.reset();
            mMatrix.postTranslate(-mSecondHandScaledBitmap.getWidth() / 2, -mSecondHandScaledBitmap.getHeight() / 2); // Centers image
            mMatrix.postRotate(angle);
            mMatrix.postTranslate(centerX, centerY);
            canvas.drawBitmap(mSecondHandScaledBitmap, mMatrix, null);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            PolishClock.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            PolishClock.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<PolishClock.Engine> mWeakReference;

        public EngineHandler(PolishClock.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            PolishClock.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
