/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.hippo.vector;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This class uses {@link android.animation.ObjectAnimator} and
 * {@link android.animation.AnimatorSet} to animate the properties of a
 * {@link android.graphics.drawable.VectorDrawable} to create an animated drawable.
 * <p>
 * AnimatedVectorDrawable are normally defined as 3 separate XML files.
 * </p>
 * <p>
 * First is the XML file for {@link android.graphics.drawable.VectorDrawable}.
 * Note that we allow the animation to happen on the group's attributes and path's
 * attributes, which requires they are uniquely named in this XML file. Groups
 * and paths without animations do not need names.
 * </p>
 * <li>Here is a simple VectorDrawable in this vectordrawable.xml file.
 * <pre>
 * &lt;vector xmlns:android=&quot;http://schemas.android.com/apk/res/android&quot;
 *     android:height=&quot;64dp&quot;
 *     android:width=&quot;64dp&quot;
 *     android:viewportHeight=&quot;600&quot;
 *     android:viewportWidth=&quot;600&quot; &gt;
 *     &lt;group
 *         android:name=&quot;rotationGroup&quot;
 *         android:pivotX=&quot;300.0&quot;
 *         android:pivotY=&quot;300.0&quot;
 *         android:rotation=&quot;45.0&quot; &gt;
 *         &lt;path
 *             android:name=&quot;v&quot;
 *             android:fillColor=&quot;#000000&quot;
 *             android:pathData=&quot;M300,70 l 0,-70 70,70 0,0 -70,70z&quot; /&gt;
 *     &lt;/group&gt;
 * &lt;/vector&gt;
 * </pre></li>
 * <p>
 * Second is the AnimatedVectorDrawable's XML file, which defines the target
 * VectorDrawable, the target paths and groups to animate, the properties of the
 * path and group to animate and the animations defined as the ObjectAnimators
 * or AnimatorSets.
 * </p>
 * <li>Here is a simple AnimatedVectorDrawable defined in this avd.xml file.
 * Note how we use the names to refer to the groups and paths in the vectordrawable.xml.
 * <pre>
 * &lt;animated-vector xmlns:android=&quot;http://schemas.android.com/apk/res/android&quot;
 *   android:drawable=&quot;@drawable/vectordrawable&quot; &gt;
 *     &lt;target
 *         android:name=&quot;rotationGroup&quot;
 *         android:animation=&quot;@anim/rotation&quot; /&gt;
 *     &lt;target
 *         android:name=&quot;v&quot;
 *         android:animation=&quot;@anim/path_morph&quot; /&gt;
 * &lt;/animated-vector&gt;
 * </pre></li>
 * <p>
 * Last is the Animator XML file, which is the same as a normal ObjectAnimator
 * or AnimatorSet.
 * To complete this example, here are the 2 animator files used in avd.xml:
 * rotation.xml and path_morph.xml.
 * </p>
 * <li>Here is the rotation.xml, which will rotate the target group for 360 degrees.
 * <pre>
 * &lt;objectAnimator
 *     android:duration=&quot;6000&quot;
 *     android:propertyName=&quot;rotation&quot;
 *     android:valueFrom=&quot;0&quot;
 *     android:valueTo=&quot;360&quot; /&gt;
 * </pre></li>
 * <li>Here is the path_morph.xml, which will morph the path from one shape to
 * the other. Note that the paths must be compatible for morphing.
 * In more details, the paths should have exact same length of commands , and
 * exact same length of parameters for each commands.
 * Note that the path strings are better stored in strings.xml for reusing.
 * <pre>
 * &lt;set xmlns:android=&quot;http://schemas.android.com/apk/res/android&quot;&gt;
 *     &lt;objectAnimator
 *         android:duration=&quot;3000&quot;
 *         android:propertyName=&quot;pathData&quot;
 *         android:valueFrom=&quot;M300,70 l 0,-70 70,70 0,0   -70,70z&quot;
 *         android:valueTo=&quot;M300,70 l 0,-70 70,0  0,140 -70,0 z&quot;
 *         android:valueType=&quot;pathType&quot;/&gt;
 * &lt;/set&gt;
 * </pre></li>
 *
 * @attr ref android.R.styleable#AnimatedVectorDrawable_drawable
 * @attr ref android.R.styleable#AnimatedVectorDrawableTarget_name
 * @attr ref android.R.styleable#AnimatedVectorDrawableTarget_animation
 */
public class AnimatedVectorDrawable extends Drawable implements Animatable2 {
    private static final String LOGTAG = "AnimatedVectorDrawable";

    private static final String ANIMATED_VECTOR = "animated-vector";
    private static final String TARGET = "target";

    private static final boolean DBG_ANIMATION_VECTOR_DRAWABLE = false;

    /** Local, mutable animator set. */
    private final AnimatorSet mAnimatorSet = new AnimatorSet();

    private AnimatedVectorDrawableState mAnimatedVectorState;

    /** Whether the animator set has been prepared. */
    private boolean mHasAnimatorSet;

    private boolean mMutated;

    /** Use a internal AnimatorListener to support callbacks during animation events. */
    private ArrayList<Animatable2.AnimationCallback> mAnimationCallbacks = null;
    private AnimatorListener mAnimatorListener = null;

    public AnimatedVectorDrawable() {
        this(null);
    }

    private AnimatedVectorDrawable(AnimatedVectorDrawableState state) {
        mAnimatedVectorState = new AnimatedVectorDrawableState(state, mCallback);
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mAnimatedVectorState = new AnimatedVectorDrawableState(
                    mAnimatedVectorState, mCallback);
            mMutated = true;
        }
        return this;
    }

    public void clearMutated() {
        if (mAnimatedVectorState.mVectorDrawable != null) {
            mAnimatedVectorState.mVectorDrawable.clearMutated();
        }
        mMutated = false;
    }

    @Override
    public ConstantState getConstantState() {
        mAnimatedVectorState.mChangingConfigurations = getChangingConfigurations();
        return mAnimatedVectorState;
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mAnimatedVectorState.getChangingConfigurations();
    }

    @Override
    public void draw(Canvas canvas) {
        mAnimatedVectorState.mVectorDrawable.draw(canvas);
        if (isStarted()) {
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mAnimatedVectorState.mVectorDrawable.setBounds(bounds);
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return mAnimatedVectorState.mVectorDrawable.setState(state);
    }

    @Override
    protected boolean onLevelChange(int level) {
        return mAnimatedVectorState.mVectorDrawable.setLevel(level);
    }

    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return mAnimatedVectorState.mVectorDrawable.setLayoutDirection(layoutDirection);
        } else {
            return false;
        }
    }

    @Override
    public int getAlpha() {
        return mAnimatedVectorState.mVectorDrawable.getAlpha();
    }

    @Override
    public void setAlpha(int alpha) {
        mAnimatedVectorState.mVectorDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mAnimatedVectorState.mVectorDrawable.setColorFilter(colorFilter);
    }

    @Override
    public void setTintList(ColorStateList tint) {
        mAnimatedVectorState.mVectorDrawable.setTintList(tint);
    }

    @Override
    public void setHotspot(float x, float y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAnimatedVectorState.mVectorDrawable.setHotspot(x, y);
        }
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAnimatedVectorState.mVectorDrawable.setHotspotBounds(left, top, right, bottom);
        }
    }

    @Override
    public void setTintMode(@NonNull PorterDuff.Mode tintMode) {
        mAnimatedVectorState.mVectorDrawable.setTintMode(tintMode);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        mAnimatedVectorState.mVectorDrawable.setVisible(visible, restart);
        return super.setVisible(visible, restart);
    }

    @Override
    public boolean isStateful() {
        return mAnimatedVectorState.mVectorDrawable.isStateful();
    }

    @Override
    public int getOpacity() {
        return mAnimatedVectorState.mVectorDrawable.getOpacity();
    }

    @Override
    public int getIntrinsicWidth() {
        return mAnimatedVectorState.mVectorDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return mAnimatedVectorState.mVectorDrawable.getIntrinsicHeight();
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAnimatedVectorState.mVectorDrawable.getOutline(outline);
        }
    }

    public Insets getOpticalInsets() {
        return mAnimatedVectorState.mVectorDrawable.getOpticalInsets();
    }

    public void inflate(Context context, XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        final AnimatedVectorDrawableState state = mAnimatedVectorState;

        int eventType = parser.getEventType();
        float pathErrorScale = 1;
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                final String tagName = parser.getName();
                if (ANIMATED_VECTOR.equals(tagName)) {
                    final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AnimatedVectorDrawable);
                    int drawableRes = a.getResourceId(
                            R.styleable.AnimatedVectorDrawable_drawable, 0);
                    if (drawableRes != 0) {
                        VectorDrawable vectorDrawable = VectorDrawable.create(context, drawableRes);
                        if (vectorDrawable != null) {
                            vectorDrawable.setAllowCaching(false);
                            vectorDrawable.setCallback(mCallback);
                            pathErrorScale = vectorDrawable.getPixelSize();
                            if (state.mVectorDrawable != null) {
                                state.mVectorDrawable.setCallback(null);
                            }
                            state.mVectorDrawable = vectorDrawable;
                        }
                    }
                    a.recycle();
                } else if (TARGET.equals(tagName)) {
                    final TypedArray a = context.obtainStyledAttributes(attrs,
                            R.styleable.AnimatedVectorDrawableTarget);
                    final String target = a.getString(
                            R.styleable.AnimatedVectorDrawableTarget_name);
                    final int animResId = a.getResourceId(
                            R.styleable.AnimatedVectorDrawableTarget_animation, 0);
                    if (animResId != 0) {
                        state.addTargetAnimator(target, AnimatorInflater.loadAnimator(context, animResId, pathErrorScale));
                    }
                    a.recycle();
                }
            }

            eventType = parser.next();
        }
    }

    private static class AnimatedVectorDrawableState extends ConstantState {
        int mChangingConfigurations;
        VectorDrawable mVectorDrawable;

        /** Fully inflated animators awaiting cloning into an AnimatorSet. */
        ArrayList<Animator> mAnimators;

        /** Map of animators to their target object names */
        ArrayMap<Animator, String> mTargetNameMap;

        public AnimatedVectorDrawableState(AnimatedVectorDrawableState copy,
                Callback owner) {
            if (copy != null) {
                mChangingConfigurations = copy.mChangingConfigurations;

                if (copy.mVectorDrawable != null) {
                    final ConstantState cs = copy.mVectorDrawable.getConstantState();
                    mVectorDrawable = (VectorDrawable) cs.newDrawable();
                    mVectorDrawable = (VectorDrawable) mVectorDrawable.mutate();
                    mVectorDrawable.setCallback(owner);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        //noinspection WrongConstant
                        mVectorDrawable.setLayoutDirection(copy.mVectorDrawable.getLayoutDirection());
                    }
                    mVectorDrawable.setBounds(copy.mVectorDrawable.getBounds());
                    mVectorDrawable.setAllowCaching(false);
                }

                if (copy.mAnimators != null) {
                    mAnimators = new ArrayList<>(copy.mAnimators);
                }

                if (copy.mTargetNameMap != null) {
                    mTargetNameMap = new ArrayMap<>(copy.mTargetNameMap);
                }
            } else {
                mVectorDrawable = new VectorDrawable();
            }
        }

        @Override
        public Drawable newDrawable() {
            return new AnimatedVectorDrawable(this);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new AnimatedVectorDrawable(this);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }

        public void addTargetAnimator(String targetName, Animator animator) {
            if (mAnimators == null) {
                mAnimators = new ArrayList<>(1);
                mTargetNameMap = new ArrayMap<>(1);
            }
            mAnimators.add(animator);
            mTargetNameMap.put(animator, targetName);

            if (DBG_ANIMATION_VECTOR_DRAWABLE) {
                Log.v(LOGTAG, "add animator  for target " + targetName + " " + animator);
            }
        }

        /**
         * Prepares a local set of mutable animators based on the constant
         * state.
         * <p>
         * If there are any pending uninflated animators, attempts to inflate
         * them immediately against the provided resources object.
         *
         * @param animatorSet the animator set to which the animators should
         *                    be added
         */
        public void prepareLocalAnimators(@NonNull AnimatorSet animatorSet) {
            // Perform a deep copy of the constant state's animators.
            final int count = mAnimators == null ? 0 : mAnimators.size();
            if (count > 0) {
                final Animator firstAnim = prepareLocalAnimator(0);
                final AnimatorSet.Builder builder = animatorSet.play(firstAnim);
                for (int i = 1; i < count; ++i) {
                    final Animator nextAnim = prepareLocalAnimator(i);
                    builder.with(nextAnim);
                }
            }
        }

        /**
         * Prepares a local animator for the given index within the constant
         * state's list of animators.
         *
         * @param index the index of the animator within the constant state
         */
        private Animator prepareLocalAnimator(int index) {
            final Animator animator = mAnimators.get(index);
            final Animator localAnimator = animator.clone();
            final String targetName = mTargetNameMap.get(animator);
            final Object target = mVectorDrawable.getTargetByName(targetName);
            localAnimator.setTarget(target);
            return localAnimator;
        }
    }

    @Override
    public boolean isRunning() {
        return mAnimatorSet.isRunning();
    }

    private boolean isStarted() {
        return mAnimatorSet.isStarted();
    }

    /**
     * Resets the AnimatedVectorDrawable to the start state as specified in the animators.
     */
    public void reset() {
        // TODO: Use reverse or seek to implement reset, when AnimatorSet supports them.
        start();
        mAnimatorSet.cancel();
    }

    @Override
    public void start() {
        ensureAnimatorSet();

        // If any one of the animator has not ended, do nothing.
        if (isStarted()) {
            return;
        }

        mAnimatorSet.start();
        invalidateSelf();
    }

    private void ensureAnimatorSet() {
        if (!mHasAnimatorSet) {
            mAnimatedVectorState.prepareLocalAnimators(mAnimatorSet);
            mHasAnimatorSet = true;
        }
    }

    @Override
    public void stop() {
        mAnimatorSet.end();
    }

    /*
    /**
     * Reverses ongoing animations or starts pending animations in reverse.
     * <p>
     * NOTE: Only works if all animations support reverse. Otherwise, this will
     * do nothing.
     * @hide
     */
    /*
    public void reverse() {
        ensureAnimatorSet();

        // Only reverse when all the animators can be reversed.
        if (!canReverse()) {
            Log.w(LOGTAG, "AnimatedVectorDrawable can't reverse()");
            return;
        }

        mAnimatorSet.reverse();
        invalidateSelf();
    }
    */

    /*
    /**
     * @hide
     */
    /*
    public boolean canReverse() {
        return mAnimatorSet.canReverse();
    }
    */

    private final Callback mCallback = new Callback() {
        @Override
        public void invalidateDrawable(Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
            unscheduleSelf(what);
        }
    };

    @Override
    public void registerAnimationCallback(@NonNull AnimationCallback callback) {
        if (callback == null) {
            return;
        }

        // Add listener accordingly.
        if (mAnimationCallbacks == null) {
            mAnimationCallbacks = new ArrayList<>();
        }

        mAnimationCallbacks.add(callback);

        if (mAnimatorListener == null) {
            // Create a animator listener and trigger the callback events when listener is
            // triggered.
            mAnimatorListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    ArrayList<AnimationCallback> tmpCallbacks = new ArrayList<>(mAnimationCallbacks);
                    int size = tmpCallbacks.size();
                    for (int i = 0; i < size; i ++) {
                        tmpCallbacks.get(i).onAnimationStart(AnimatedVectorDrawable.this);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    ArrayList<AnimationCallback> tmpCallbacks = new ArrayList<>(mAnimationCallbacks);
                    int size = tmpCallbacks.size();
                    for (int i = 0; i < size; i ++) {
                        tmpCallbacks.get(i).onAnimationEnd(AnimatedVectorDrawable.this);
                    }
                }
            };
        }
        mAnimatorSet.addListener(mAnimatorListener);
    }

    // A helper function to clean up the animator listener in the mAnimatorSet.
    private void removeAnimatorSetListener() {
        if (mAnimatorListener != null) {
            mAnimatorSet.removeListener(mAnimatorListener);
            mAnimatorListener = null;
        }
    }

    @Override
    public boolean unregisterAnimationCallback(@NonNull AnimationCallback callback) {
        if (mAnimationCallbacks == null || callback == null) {
            // Nothing to be removed.
            return false;
        }
        boolean removed = mAnimationCallbacks.remove(callback);

        //  When the last call back unregistered, remove the listener accordingly.
        if (mAnimationCallbacks.size() == 0) {
            removeAnimatorSetListener();
        }
        return removed;
    }

    @Override
    public void clearAnimationCallbacks() {
        removeAnimatorSetListener();
        if (mAnimationCallbacks == null) {
            return;
        }

        mAnimationCallbacks.clear();
    }

}
