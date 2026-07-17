package com.github.catvod.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.text.TextUtils;
import android.widget.EditText;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AZ4 {
    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(Math.min(8, Runtime.getRuntime().availableProcessors() * 2));
    public static String pickFast(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;

        AtomicReference<String> result = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean(false);

        CountDownLatch latch = new CountDownLatch(1);

        for (String url : urls) {
            POOL.execute(() -> {
                if (done.get()) return;

                try {
                    String html = OkHttp.string(url);

                    // 只要能正常返回（不为空）就算成功
                    if (!TextUtils.isEmpty(html) && done.compareAndSet(false, true)) {
                        result.set(url);
                        latch.countDown();
                    }

                } catch (Exception ignored) {
                }
            });
        }

        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        return result.get();
    }

    public static String joinUrl(String base, String path) {
        return base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }

    public static Activity getActivity() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
        Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
        activitiesField.setAccessible(true);
        Map<?, ?> activities = (Map<?, ?>) activitiesField.get(activityThread);
        for (Object activityRecord : activities.values()) {
            Class<?> activityRecordClass = activityRecord.getClass();
            Field pausedField = activityRecordClass.getDeclaredField("paused");
            pausedField.setAccessible(true);
            if (!pausedField.getBoolean(activityRecord)) {
                Field activityField = activityRecordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Activity activity = (Activity) activityField.get(activityRecord);
                return activity;
            }
        }
        return null;
    }

    public interface InputCallback {
        void onResult(String text);
    }
    private static AlertDialog dialog;
    public static void showInput(String title, InputCallback callback){
            try {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }

                EditText input = new EditText(getActivity());

                dialog = new AlertDialog.Builder(getActivity())
                        .setTitle(title)
                        .setView(input)
                        .setNegativeButton("取消", (dialog, which) -> {
                            if (callback != null){
                                callback.onResult(null);
                            }
                        })
                        .setPositiveButton("确定", (dialog, which) -> {

                            if (callback != null) {
                                callback.onResult(input.getText().toString());
                            }

                        })
                        .show();

            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private static WeakReference<Activity> cachedActivity;
    public static Activity getTopActivity() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Map<Object, Object> activities;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                activities = (HashMap<Object, Object>) activitiesField.get(activityThread);
            } else {
                activities = (android.util.ArrayMap<Object, Object>) activitiesField.get(activityThread);
            }
            for (Object activityRecord : activities.values()) {
                Class<?> activityRecordClass = activityRecord.getClass();
                java.lang.reflect.Field pausedField = activityRecordClass.getDeclaredField("paused");
                pausedField.setAccessible(true);
                if (!pausedField.getBoolean(activityRecord)) {
                    java.lang.reflect.Field activityField = activityRecordClass.getDeclaredField("activity");
                    activityField.setAccessible(true);
                    Activity activity = (Activity) activityField.get(activityRecord);
                    if (activity != null) {
                        cachedActivity = new WeakReference<>(activity);
                        return activity;
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log("获取TopActivity失败: " + e.getMessage());
        }

        // 如果反射获取失败，尝试从缓存返回
        if (cachedActivity != null) {
            Activity activity = cachedActivity.get();
            if (activity != null && !activity.isFinishing()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
                    return null;
                }
                return activity;
            }
        }
        return null;
    }

    public static int getPort() {
        Class<?> clz = null;
        int port = 9978;
        try {
            clz = Class.forName("com.github.catvod.Proxy");
            port = (int) clz.getMethod("getPort").invoke(null);
        } catch (Exception e) {
            SpiderDebug.log("❌ 获取代理端口异常: " + e.getMessage());
        }
        return port;
    }
}