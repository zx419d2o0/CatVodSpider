package com.github.catvod.utils;

import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public abstract class PlayerFunc {

    private static final long CLICK_DEBOUNCE_TIME = 500L;

    // 点击防抖时间
    private long lastClickTime;

    protected ViewGroup parent;

    // 是否已经完成按钮注入
    private boolean injected;

    protected PlayerFunc() {
    }

    public void start() {
        AppHook.register(this);
    }

    void setParent(ViewGroup parent) {
        if (this.parent != parent) {
            injected = false;
        }

        this.parent = parent;
    }

    /**
     * 执行功能注入
     */
    protected void execute() {
        if (injected) {
            return;
        }

        injectButton();
    }

    /**
     * 创建并插入按钮
     */
    protected void injectButton() {
        if (injected || parent == null || hasView(getTag())) {
            return;
        }
        int position = getIndex();

        View reference = position > 0 && position <= parent.getChildCount()
                ? parent.getChildAt(position - 1)
                : null;

        ViewGroup.LayoutParams params = reference != null
                ? reference.getLayoutParams()
                : new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

        if (position < 0 || position > parent.getChildCount()) {
            position = parent.getChildCount();
        }

        TextView button = createButton();
        parent.addView(button, position, params);
        injected = true;
        parent = null;
    }

    private TextView createButton() {
        TextView button = new TextView(parent.getContext());

        button.setText(getText());
        button.setTag(getTag());
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setPadding(20, 10, 20, 10);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(10);
        }

        button.setOnClickListener(this::handleClick);
        button.setOnLongClickListener(this::handleLongClick);

        return button;
    }

    private void handleClick(View view) {
        long now = System.currentTimeMillis();

        if (now - lastClickTime < CLICK_DEBOUNCE_TIME) {
            return;
        }

        lastClickTime = now;
        onClick(view);
    }

    private boolean handleLongClick(View view) {
        return onLongClick(view);
    }

    protected abstract String getText();

    protected abstract String getTag();

    protected abstract int getIndex();

    protected void onClick(View view) {
    }

    protected boolean onLongClick(View view) {
        return false;
    }

    protected boolean hasView(String tag) {
        return parent != null
                && tag != null
                && parent.findViewWithTag(tag) != null;
    }

    /**
     * 清理引用
     */
    public void destroy() {
        AppHook.unregister(this);
        injected = false;
        parent = null;
    }
}
