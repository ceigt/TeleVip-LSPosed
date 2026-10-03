package com.my.televip.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.CompoundButton;

/** Resource-independent switch for a settings row injected into another application. */
public final class Android16Switch extends CompoundButton {
    // Keep the Android 16 proportions at a size close to Telegram's native switch.
    private static final float CONTROL_SCALE = 0.55f;
    private static final float CONTROL_WIDTH_DP = 64f * CONTROL_SCALE;
    private static final float LABEL_GAP_DP = 12f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final Path check = new Path();
    private final boolean dark;
    private float position;
    private ValueAnimator animator;

    public Android16Switch(Context context, boolean dark) {
        super(context);
        this.dark = dark;
        setButtonDrawable(null);
        setClickable(true);
        setFocusable(true);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setSingleLine(false);
        setMinHeight(dp(56));
        setTextSize(15);
        setTextColor(dark ? 0xffe4e2e9 : 0xff1b1c20);
        android.util.TypedValue background = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true)
                && background.resourceId != 0) setBackgroundResource(background.resourceId);
    }

    @Override public int getCompoundPaddingRight() {
        return super.getCompoundPaddingRight() + (getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 0 : dp(CONTROL_WIDTH_DP + LABEL_GAP_DP));
    }

    @Override public int getCompoundPaddingLeft() {
        return super.getCompoundPaddingLeft() + (getLayoutDirection() == LAYOUT_DIRECTION_RTL ? dp(CONTROL_WIDTH_DP + LABEL_GAP_DP) : 0);
    }

    @Override public CharSequence getAccessibilityClassName() { return "android.widget.Switch"; }

    @Override public void setChecked(boolean checked) {
        super.setChecked(checked);
        if (animator != null) animator.cancel();
        float target = checked ? 1f : 0f;
        if (!isLaidOut()) {
            position = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(position, target);
        animator.setDuration(180);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            position = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        position = isChecked() ? 1f : 0f;
        super.onDetachedFromWindow();
    }

    @Override public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (animator != null) animator.cancel();
        position = isChecked() ? 1f : 0f;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float left = rtl ? getPaddingLeft() : getWidth() - getPaddingRight() - dp(CONTROL_WIDTH_DP);
        float cy = getHeight() / 2f;
        int saved = canvas.save();
        canvas.scale(CONTROL_SCALE, CONTROL_SCALE, left, cy);
        track.set(left, cy - dp(20), left + dp(64), cy + dp(20));
        int active = dark ? 0xffaccaf0 : 0xff204f75;
        int inactive = dark ? 0xff363941 : 0xffe3e3eb;
        int outline = dark ? 0xff94969f : 0xff767983;
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(inactive, active, position));
        paint.setAlpha(isEnabled() ? 255 : 100);
        canvas.drawRoundRect(track, dp(20), dp(20), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(outline);
        paint.setAlpha((int) ((1f - position) * (isEnabled() ? 255 : 100)));
        canvas.drawRoundRect(track.left + dp(1), track.top + dp(1), track.right - dp(1), track.bottom - dp(1), dp(19), dp(19), paint);
        paint.setStyle(Paint.Style.FILL);
        float offset = rtl ? 1f - position : position;
        float cx = left + dp(20) + dp(24) * offset;
        float radius = dp(10) + dp(5) * position;
        paint.setColor(blend(outline, dark ? 0xff12334f : 0xfff7f9ff, position));
        paint.setAlpha(isEnabled() ? 255 : 100);
        canvas.drawCircle(cx, cy, radius, paint);
        check.reset();
        check.moveTo(cx - dp(6), cy);
        check.lineTo(cx - dp(1.5f), cy + dp(4.5f));
        check.lineTo(cx + dp(7), cy - dp(4.5f));
        paint.setColor(active);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setAlpha((int) (position * (isEnabled() ? 255 : 100)));
        canvas.drawPath(check, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        canvas.restoreToCount(saved);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static int blend(int from, int to, float fraction) {
        return Color.rgb((int) (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction));
    }
}
