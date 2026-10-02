package com.github.catvod.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 播放器Hook管理器
 *
 * 负责播放器检测、控件查找以及通知注册功能。
 */
public class AppHook {

    // 保存所有已注册功能，共用一次播放器检测
    private static final List<PlayerFunc> FUNCTIONS = new ArrayList<>();

    private static Timer hookTimer;
    private static boolean running;

    public static synchronized void register(PlayerFunc func) {
        if (func == null || FUNCTIONS.contains(func)) {
            return;
        }

        FUNCTIONS.add(func);
        start();
    }

    public static synchronized void unregister(PlayerFunc func) {
        if (func == null) {
            return;
        }

        FUNCTIONS.remove(func);

        if (FUNCTIONS.isEmpty()) {
            stop();
        }
    }

    private static synchronized void start() {
        if (running) {
            return;
        }

        running = true;

        // 后台定时检查播放器页面，找到目标控件后通知功能注入
        hookTimer = new Timer("PlayerHook", true);
        hookTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Activity activity = AZ4.getTopActivity();
                if (activity == null) {
                    return;
                }

                activity.runOnUiThread(() -> {
                    View root = activity.getWindow().getDecorView();
                    // 查找硬解/软解所在按钮容器
                    ViewGroup parent = findButtonGroup(root);

                    if (parent != null) {
                        notifyFunctions(parent);
                    }
                });
            }
        }, 1000, 1000);
    }

    private static synchronized void stop() {
        running = false;

        if (hookTimer != null) {
            hookTimer.cancel();
            hookTimer.purge();
            hookTimer = null;
        }
    }

    private static synchronized void notifyFunctions(ViewGroup parent) {
        // 使用副本遍历，避免功能注册变化导致并发修改
        for (PlayerFunc func : new ArrayList<>(FUNCTIONS)) {
            if (func != null) {
                func.setParent(parent);
                func.execute();
            }
        }
    }

    private static ViewGroup findButtonGroup(View view) {
        if (view == null) {
            return null;
        }

        if (view instanceof TextView) {
            String text = ((TextView) view).getText().toString();

            // 根据播放器已有按钮定位目标按钮组
            if ("硬解".equals(text) || "软解".equals(text)) {
                if (view.getParent() instanceof ViewGroup) {
                    return (ViewGroup) view.getParent();
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {
                ViewGroup result = findButtonGroup(group.getChildAt(i));
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }
}
