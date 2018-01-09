/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.systemui.shared.recents.model.Task;

/**
 * Contains options for a recent task when long-pressing its icon.
 */
public class TaskMenuView extends AbstractFloatingView {

    private static final Rect sTempRect = new Rect();

    /** Note that these will be shown in order from top to bottom, if available for the task. */
    private static final TaskSystemShortcut[] MENU_OPTIONS = new TaskSystemShortcut[] {
            new TaskSystemShortcut.Widgets(),
            new TaskSystemShortcut.AppInfo(),
            new TaskSystemShortcut.Install()
    };

    private static final long OPEN_CLOSE_DURATION = 220;

    private Launcher mLauncher;
    private TextView mTaskIconAndName;
    private AnimatorSet mOpenCloseAnimator;
    private TaskView mTaskView;
    private View mWidgetsOptionView;

    public TaskMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float r = getResources().getDimensionPixelSize(R.dimen.task_menu_background_radius);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTaskIconAndName = findViewById(R.id.task_icon_and_name);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            DragLayer dl = mLauncher.getDragLayer();
            if (!dl.isEventOverView(this, ev)) {
                // TODO: log this once we have a new container type for it?
                close(true);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (animate) {
            animateClose();
        } else {
            closeComplete();
        }
    }

    @Override
    public void logActionCommand(int command) {
        // TODO
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_TASK_MENU) != 0;
    }

    public static boolean showForTask(TaskView taskView) {
        Launcher launcher = Launcher.getLauncher(taskView.getContext());
        final TaskMenuView taskMenuView = (TaskMenuView) launcher.getLayoutInflater().inflate(
                        R.layout.task_menu, launcher.getDragLayer(), false);
        return taskMenuView.populateAndShowForTask(taskView);
    }

    private boolean populateAndShowForTask(TaskView taskView) {
        if (isAttachedToWindow()) {
            return false;
        }
        mLauncher.getDragLayer().addView(this);
        mTaskView = taskView;
        addMenuOptions(mTaskView.getTask());
        orientAroundTaskView(mTaskView);
        post(this::animateOpen);
        return true;
    }

    private void addMenuOptions(Task task) {
        Drawable icon = task.icon.getConstantState().newDrawable();
        int iconSize = getResources().getDimensionPixelSize(R.dimen.task_thumbnail_icon_size);
        icon.setBounds(0, 0, iconSize, iconSize);
        mTaskIconAndName.setCompoundDrawables(null, icon, null, null);
        mTaskIconAndName.setText(TaskUtils.getTitle(mLauncher, task));

        for (TaskSystemShortcut menuOption : MENU_OPTIONS) {
            OnClickListener onClickListener = menuOption.getOnClickListener(mLauncher, task);
            if (onClickListener != null) {
                addMenuOption(menuOption, onClickListener);
            }
        }
    }

    private void addMenuOption(TaskSystemShortcut menuOption, OnClickListener onClickListener) {
        DeepShortcutView menuOptionView = (DeepShortcutView) mLauncher.getLayoutInflater().inflate(
                R.layout.system_shortcut, this, false);
        menuOptionView.getIconView().setBackgroundResource(menuOption.iconResId);
        menuOptionView.getBubbleText().setText(menuOption.labelResId);
        menuOptionView.setOnClickListener(onClickListener);
        addView(menuOptionView);

        if (menuOption instanceof TaskSystemShortcut.Widgets) {
            mWidgetsOptionView = menuOptionView;
        }
    }

    private void orientAroundTaskView(TaskView taskView) {
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(taskView, sTempRect);
        Rect insets = mLauncher.getDragLayer().getInsets();
        setX(sTempRect.left + (sTempRect.width() - getMeasuredWidth()) / 2 - insets.left);
        setY(sTempRect.top - mTaskIconAndName.getPaddingTop() - insets.top);
    }

    private void animateOpen() {
        animateOpenOrClosed(false);
        mIsOpen = true;
    }

    private void animateClose() {
        animateOpenOrClosed(true);
    }

    private void animateOpenOrClosed(boolean closing) {
        if (mOpenCloseAnimator != null && mOpenCloseAnimator.isRunning()) {
            return;
        }
        mOpenCloseAnimator = LauncherAnimUtils.createAnimatorSet();
        mOpenCloseAnimator.play(createOpenCloseOutlineProvider()
                .createRevealAnimator(this, closing));
        mOpenCloseAnimator.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                if (closing) {
                    closeComplete();
                }
            }
        });
        mOpenCloseAnimator.play(ObjectAnimator.ofFloat(this, ALPHA, closing ? 0 : 1));
        mOpenCloseAnimator.setDuration(OPEN_CLOSE_DURATION);
        mOpenCloseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mOpenCloseAnimator.start();
    }

    private void closeComplete() {
        mIsOpen = false;
        mLauncher.getDragLayer().removeView(this);
    }

    private RoundedRectRevealOutlineProvider createOpenCloseOutlineProvider() {
        int iconSize = getResources().getDimensionPixelSize(R.dimen.task_thumbnail_icon_size);
        float fromRadius = iconSize / 2;
        float toRadius = getResources().getDimensionPixelSize(
                R.dimen.task_menu_background_radius);
        Point iconCenter = new Point(getWidth() / 2, mTaskIconAndName.getPaddingTop() + iconSize / 2);
        Rect fromRect = new Rect(iconCenter.x, iconCenter.y, iconCenter.x, iconCenter.y);
        Rect toRect = new Rect(0, 0, getWidth(), getHeight());
        return new RoundedRectRevealOutlineProvider(fromRadius, toRadius, fromRect, toRect) {
            @Override
            public boolean shouldRemoveElevationDuringAnimation() {
                return true;
            }
        };
    }

    @Override
    protected void onWidgetsBound() {
        TaskSystemShortcut widgetsOption = new TaskSystemShortcut.Widgets();
        View.OnClickListener onClickListener = widgetsOption.getOnClickListener(
                mLauncher, mTaskView.getTask());

        if (onClickListener != null && mWidgetsOptionView == null) {
            // We didn't have any widgets cached but now there are some, so add the option.
            addMenuOption(widgetsOption, onClickListener);
        } else if (onClickListener == null && mWidgetsOptionView != null) {
            // No widgets exist, but we previously added the option so remove it.
            removeView(mWidgetsOptionView);
        }
    }
}
