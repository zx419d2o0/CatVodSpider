package com.github.catvod.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.catvod.bean.Result;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.spider.Init;

import java.net.URLEncoder;
import java.util.Timer;
import java.util.TimerTask;

public class Sub {
    private static Timer hookTimer;
    private static volatile boolean isMonitoring = false;
    private static volatile String activeInstanceToken = "";
    private static final String RUNTIME_PREFS_NAME = "leo_danmaku_runtime";
    private static boolean isFirstDetection = true;
    private static final Object runLock = new Object();
    private static final String KEY_ACTIVE_INSTANCE_TOKEN = "active_instance_token";
    private static Timer playbackCheckTimer;
    private static Runnable delayedPushTask = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static boolean isLeoButtonInjected = false;
    public static long lastButtonClickTime = 0;
    private static int nextViewId = 10000;

    private static String btnText = "增强";
    private static String btnTag = "btnExt";

    // 启动Hook监控
    public static void startHookMonitor() {
        if (hookTimer != null || isMonitoring) {
            SpiderDebug.log("⚠️ Hook监控已在运行中" + String.valueOf(hookTimer) + String.valueOf(isMonitoring));
            return;
        }

        claimActiveInstance();
        SpiderDebug.log("🚀 启动Hook监控");
        isMonitoring = true;
        isFirstDetection = true;

        hookTimer = new Timer("DanmakuHookTimer", true);
        hookTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (runLock) {
                    try {
                        if (!isCurrentActiveInstance()) {
                            stopHookMonitorForTakeover();
                            return;
                        }
                        Activity act = AZ4.getTopActivity();
                        if (act != null && !act.isFinishing()) {
                            // 检查是否是播放界面
                            String className = act.getClass().getName().toLowerCase();
                            if (isPlayerActivity(className)) {
//                            DanmakuSpider.log("[Monitor] 检测到播放界面: " + className);

                                // 注入Leo弹幕按钮0
                                if (!isLeoButtonInjected) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                injectLeoButton(act);
                                            } catch (Exception e) {
                                                SpiderDebug.log("❌ 按钮注入异常: " + e.getMessage());
                                            }
                                        }
                                    });
                                }

                                // 检查播放状态
//                            checkPlaybackStatus(act);

                                // Hook获取标题
//                            String newTitle = extractTitleFromView(act.getWindow().getDecorView());
//                                Media media = getMedia();
//                                if (media == null) return;
//
//                                if (TextUtils.isEmpty(media.getUrl())) {
//                                    return;
//                                }
//
//                                boolean wasPlaying = isVideoPlaying;
//                                isVideoPlaying = media.isPlaying();
//
//                                // 获取媒体信息。即使尚未开始播放，也可以先发起自动匹配。
//                                lastEpisodeInfo = getEpisodeInfo(media, act);
//
//                                if (isVideoPlaying && !wasPlaying) {
//                                    videoPlayStartTime = System.currentTimeMillis();
////                                    DanmakuSpider.log("▶️ 检测到视频开始播放");
//                                } else if (!isVideoPlaying && wasPlaying) {
//                                    DanmakuSpider.log("⏸️ 检测到视频停止播放");
//
//                                    // 清空缓存和队列
//                                    pendingPushes.clear();
//                                    lastPushTime.clear();
//                                    autoPushSessions.clear();
//                                    videoPlayStartTime = 0;
//                                }
//
//                                DanmakuConfig config = DanmakuConfigManager.getConfig(act);
//
//                                // 检测是否开启自动查询或者已经手动查询过
//                                if (!config.isAutoPushEnabled() && TextUtils.isEmpty(DanmakuManager.lastManualDanmakuUrl)) {
//                                    return;
//                                }
//
//                                processDetectedTitle(act);
                            } else {
                                // 不在播放界面，重置播放状态
                                isLeoButtonInjected = false;
//                            SpiderDebug.log("不在播放界面，重置播放状态");
                            }
                        }
                    } catch (Exception e) {
                        SpiderDebug.log("❌ Hook监控异常: " + e.getMessage());
                    }
                }
            }
        }, 2000, 1000);

        // 启动播放状态检查定时器
//        startPlaybackCheckTimer();
    }

    private static void claimActiveInstance() {
        activeInstanceToken = System.currentTimeMillis() + "-" + java.util.UUID.randomUUID();
        android.content.SharedPreferences prefs = getRuntimePrefs();
        if (prefs != null) {
            prefs.edit().putString(KEY_ACTIVE_INSTANCE_TOKEN, activeInstanceToken).commit();
        }
    }

    private static android.content.SharedPreferences getRuntimePrefs() {
        try {
            android.content.Context context = Init.context();
            if (context == null) {
                Activity activity = AZ4.getTopActivity();
                if (activity != null) context = activity.getApplicationContext();
            }
            return context != null ? context.getSharedPreferences(RUNTIME_PREFS_NAME, android.content.Context.MODE_PRIVATE) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isCurrentActiveInstance() {
        if (TextUtils.isEmpty(activeInstanceToken)) return true;
        android.content.SharedPreferences prefs = getRuntimePrefs();
        if (prefs == null) return true;
        String activeToken = prefs.getString(KEY_ACTIVE_INSTANCE_TOKEN, "");
        return activeInstanceToken.equals(activeToken);
    }

    private static void stopHookMonitorForTakeover() {
        boolean wasRunning = isMonitoring || hookTimer != null || playbackCheckTimer != null;
        cleanupMonitorState();
        if (wasRunning) SpiderDebug.log("♻️ 检测到新实例接管，当前实例优雅退出");
    }

    private static void cleanupMonitorState() {
        isMonitoring = false;

        // 取消延迟任务
        if (delayedPushTask != null) {
            mainHandler.removeCallbacks(delayedPushTask);
            delayedPushTask = null;
        }

        if (hookTimer != null) {
            hookTimer.cancel();
            hookTimer = null;
        }

        if (playbackCheckTimer != null) {
            playbackCheckTimer.cancel();
            playbackCheckTimer = null;
        }
    }

    // 判断是否为播放界面
    private static boolean isPlayerActivity(String className) {
        return className.contains("videoactivity") || className.contains("detailactivity");

//        DanmakuSpider.log("className: " + className);

//        return className.contains("videoactivity") ||
//                className.contains("playeractivity") ||
//                className.contains("ijkplayer") ||
//                className.contains("exoplayer");

//        return className.contains("player") || className.contains("video") ||
//                className.contains("detail") || className.contains("play") ||
//                className.contains("media") || className.contains("movie") ||
//                className.contains("tv") || className.contains("film");
    }

    // === 修改后的按钮注入逻辑 ===

    private static void injectLeoButton(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

//        DanmakuSpider.log("[按钮注入] 开始尝试注入按钮");

        try {
            // 先检查是否已经有Leo弹幕按钮存在
            View root = activity.getWindow().getDecorView();
            View existing = root.findViewWithTag(btnTag);
            if (existing != null) {
//                DanmakuSpider.log("[按钮注入] 按钮已存在，跳过");
                return;
            }

            // 遍历视图树寻找合适的锚点按钮
            traverseForButton(root, 0);
        } catch (Exception e) {
            SpiderDebug.log("[按钮注入] 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 轻量级遍历寻找按钮锚点
    private static void traverseForButton(View view, int depth) {
        if (view == null || depth > 15) {
            SpiderDebug.log("[按钮注入] 遍历结束，没有找到按钮锚点");
            return; // 增加深度限制，播放器UI可能比较深
        }

        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0) {
                String text = cs.toString();
                // 寻找合适的锚点按钮
                if (text.equals("硬解") || text.equals("软解")) {
//                if (text.equals("硬解") || text.equals("软解") || text.equals("字幕") || text.equals("视轨") || text.equals("音轨")) {
                    SpiderDebug.log("[按钮注入] 找到按钮锚点: " + text + " 深度: " + depth);
                    if (view.getParent() instanceof ViewGroup) {
                        injectButton((ViewGroup) view.getParent(), tv);
                        return; // 找到一个就返回
                    }
                }
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            // 修剪：跳过列表控件
            if (view instanceof android.widget.ListView ||
                    view.getClass().getName().contains("RecyclerView")) {
                return;
            }

            for (int i = 0; i < group.getChildCount(); i++) {
                traverseForButton(group.getChildAt(i), depth + 1);
            }
        }
    }

    // 注入Leo弹幕按钮（原版逻辑）
    private static void injectButton(ViewGroup parent, TextView anchor) {
        try {
            View existing = parent.findViewWithTag(btnTag);
            String anchorText = anchor.getText().toString();
            boolean isTargetAnchor = anchorText.contains("音轨");

            // 逻辑：如果已存在按钮
            if (existing != null) {
                // 如果当前锚点是"音轨"，我们重新定位按钮
                if (isTargetAnchor) {
                    ((ViewGroup) existing.getParent()).removeView(existing);
                    SpiderDebug.log("[按钮注入] 移除旧按钮，准备重新注入");
                } else {
//                    DanmakuSpider.log("[按钮注入] 按钮已存在，跳过");
                    return; // 否则不重复添加
                }
            }

            if (isInRecyclerView(parent)) {
                SpiderDebug.log("[按钮注入] 在RecyclerView中，跳过");
                return;
            }

            TextView btn;
            if (existing != null && isTargetAnchor) {
                btn = (TextView) existing; // 复用
            } else {
                // 创建新的Leo弹幕按钮
                btn = new TextView(parent.getContext());
                btn.setText(btnText);
                btn.setTag(btnTag);
                btn.setTextColor(anchor.getTextColors());
                btn.setTextSize(0, anchor.getTextSize());
                btn.setGravity(Gravity.CENTER);
                btn.setPadding(20, 10, 20, 10);
                btn.setSingleLine(true);

                // 修复焦点问题 - 添加必要的焦点设置
                btn.setFocusable(true);
                btn.setFocusableInTouchMode(true);
                btn.setClickable(true);

                // 设置背景（使用锚点的背景）
                if (anchor.getBackground() != null && anchor.getBackground().getConstantState() != null) {
                    btn.setBackground(anchor.getBackground().getConstantState().newDrawable());
                } else {
                    btn.setBackgroundColor(Color.parseColor("#4CAF50"));
                }

                // 按钮点击事件 - 这是核心注册逻辑
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (parent.getContext() instanceof Activity) {
                            Activity activity = (Activity) parent.getContext();
                            // 添加防抖动检查
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastButtonClickTime < 500) {
                                SpiderDebug.log("[按钮点击] 防抖动：点击过于频繁");
                                return;
                            }
                            lastButtonClickTime = currentTime;

                            SpiderDebug.log("[按钮点击] 打开搜索对话框");
                            showSearchDialog(activity);
                        }
                    }
                });

                // 设置长按事件（可选）
                btn.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (parent.getContext() instanceof Activity) {
                            Activity activity = (Activity) parent.getContext();
                            // 添加防抖动检查
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastButtonClickTime < 500) {
                                SpiderDebug.log("[按钮长按] 防抖动：操作过于频繁");
                                return true;
                            }
                            lastButtonClickTime = currentTime;

                            SpiderDebug.log("[按钮长按] 打开搜索对话框");
//                            DanmakuUIHelper.showQRCodeDialog((Activity) parent.getContext(), "http://" + NetworkUtils.getLocalIpAddress() + ":9810");

                            // 显示菜单
                            Notify.show("功能未实现");
//                            showLeoButtonMenu(activity);
                        }
                        return true;
                    }
                });
            }

            // 布局参数设置
            ViewGroup.LayoutParams anchorLp = anchor.getLayoutParams();
            ViewGroup.LayoutParams params = null;

            if (anchorLp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams((LinearLayout.LayoutParams) anchorLp);
                llp.weight = 0;
                params = llp;
            } else if (anchorLp instanceof ViewGroup.MarginLayoutParams) {
                params = new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) anchorLp);
            } else {
                params = new ViewGroup.LayoutParams(anchorLp);
            }

            // 插入位置逻辑
            int insertIndex = -1;
            boolean isInsertBefore = anchorText.equals("弹幕搜索") || anchorText.contains("搜索") || isTargetAnchor;
            int anchorIndex = parent.indexOfChild(anchor);

            if (isInsertBefore) {
                insertIndex = anchorIndex;
            } else {
                insertIndex = anchorIndex + 1;
            }

            // 如果按钮已经在正确位置，直接返回
            if (existing != null && parent.indexOfChild(existing) == insertIndex) {
                if (existing.getVisibility() != View.VISIBLE) existing.setVisibility(View.VISIBLE);
                return;
            }

            // 为RelativeLayout设置特殊规则
            if (parent instanceof android.widget.RelativeLayout) {
                android.widget.RelativeLayout.LayoutParams rlp = new android.widget.RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (anchor.getId() == View.NO_ID) {
                    // 使用兼容方式生成View ID
                    anchor.setId(generateViewId());
                }
                if (isInsertBefore) {
                    rlp.addRule(android.widget.RelativeLayout.LEFT_OF, anchor.getId());
                } else {
                    rlp.addRule(android.widget.RelativeLayout.RIGHT_OF, anchor.getId());
                }
                rlp.alignWithParent = true;
                rlp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
                params = rlp;
            }

            // 设置边距
            if (params instanceof ViewGroup.MarginLayoutParams) {
                int existingLeft = 0;
                int existingRight = 0;
                if (anchorLp instanceof ViewGroup.MarginLayoutParams) {
                    existingLeft = ((ViewGroup.MarginLayoutParams) anchorLp).leftMargin;
                    existingRight = ((ViewGroup.MarginLayoutParams) anchorLp).rightMargin;
                }

                // 如果原始边距太小，使用默认值
                if (existingLeft < 5 && existingRight < 5) {
                    existingLeft = 20;
                    existingRight = 20;
                }

                ((ViewGroup.MarginLayoutParams) params).leftMargin = existingLeft;
                ((ViewGroup.MarginLayoutParams) params).rightMargin = existingRight > 0 ? existingRight : existingLeft;
                ((ViewGroup.MarginLayoutParams) params).topMargin = 0;
                ((ViewGroup.MarginLayoutParams) params).bottomMargin = 0;
            }

            // 确保按钮在最顶层显示
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                btn.setElevation(10f); // 设置阴影层级，确保按钮在顶层
            }
            btn.bringToFront(); // 将按钮置于最前

            try {
                if (insertIndex >= 0 && insertIndex <= parent.getChildCount()) {
                    parent.addView(btn, insertIndex, params);
                } else {
                    parent.addView(btn, params);
                }

                // 重新请求布局
                parent.requestLayout();
                parent.post(new Runnable() {
                    @Override
                    public void run() {
                        // 确保按钮正确显示
                        btn.setVisibility(View.VISIBLE);
                        btn.setClickable(true);
                    }
                });

                SpiderDebug.log("✅ Leo弹幕按钮注入成功 123123");
                isLeoButtonInjected = true;
            } catch (Exception e) {
                SpiderDebug.log("❌ 添加按钮失败: " + e.getMessage());
                parent.addView(btn);
            }
        } catch (Exception e) {
            SpiderDebug.log("❌ 注入按钮异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 检查是否在RecyclerView中
    private static boolean isInRecyclerView(View view) {
        View p = view;
        while (p != null) {
            if (p.getClass().getName().contains("RecyclerView")) return true;
            if (p.getParent() instanceof View) {
                p = (View) p.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    // 兼容低版本的View ID生成方法
    private static int generateViewId() {
        // 确保ID为正数且不与其他ID冲突
        nextViewId++;
        // 确保ID不会超过Android允许的最大值（0xFFFFFF，因为高8位有特殊用途）
        if (nextViewId > 0x00FFFFFF) {
            nextViewId = 10000;
        }
        return nextViewId;
    }

    public static void showSearchDialog(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            // Main vertical layout
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (activity.getResources().getDisplayMetrics().density * 16);
            layout.setPadding(pad, pad, pad, pad);

            // Input box
            final android.widget.EditText input = new android.widget.EditText(activity);
            input.setHint("请输入内容");
            layout.addView(input, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // Results area (scrollable)
            android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
            LinearLayout resultContainer = new LinearLayout(activity);
            resultContainer.setOrientation(LinearLayout.VERTICAL);
            scrollView.addView(resultContainer);
            layout.addView(scrollView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ));

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                    .setTitle("搜索")
                    .setView(layout)
                    .setPositiveButton("搜索", null)
                    .setNegativeButton("取消", null)
                    .create();

            dialog.setOnShowListener(d -> {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String keyword = input.getText().toString().trim();
                    if (keyword.isEmpty()) {
                        Notify.show("请输入关键词");
                        return;
                    }
                    new Thread(() -> {
                        try {
                            String api = "http://api-shoulei-ssl.xunlei.com/oracle/subtitle?name=" + keyword;
                            String content = OkHttp.string(api);
                            org.json.JSONObject json = new org.json.JSONObject(content);
                            org.json.JSONArray data = json.optJSONArray("data");
                            activity.runOnUiThread(() -> {
                                resultContainer.removeAllViews();
                                if (data == null || data.length() == 0) {
                                    Notify.show("没有数据");
                                    return;
                                }
                                float density = activity.getResources().getDisplayMetrics().density;
                                int rowHeight = (int) (76 * density);
                                int rowMargin = (int) (6 * density);
                                int durWidth = (int) (90 * density);
                                int pushWidth = (int) (56 * density);
                                for (int i = 0; i < data.length(); i++) {
                                    org.json.JSONObject item = data.optJSONObject(i);
                                    if (item == null) continue;
                                    String name = item.optString("name");
                                    String url = item.optString("url");
                                    long duration = item.optLong("duration");

                                    LinearLayout row = new LinearLayout(activity);
                                    row.setOrientation(LinearLayout.HORIZONTAL);
                                    row.setGravity(Gravity.CENTER_VERTICAL);
                                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
                                    rowLp.topMargin = rowMargin;
                                    rowLp.bottomMargin = rowMargin;
                                    // name column: weight=1, width=0
                                    HorizontalScrollView nameScroll = new HorizontalScrollView(activity);
                                    nameScroll.setHorizontalScrollBarEnabled(true);
                                    nameScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
                                    TextView nameTv = new TextView(activity);
                                    nameTv.setText(name);
                                    nameTv.setSingleLine(true);
                                    nameTv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                                    nameTv.setHorizontallyScrolling(true);
                                    nameTv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                                    nameScroll.addView(nameTv, new ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                                    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, rowHeight, 1f);
                                    row.addView(nameScroll, nameLp);
                                    // duration column
                                    TextView durTv = new TextView(activity);
                                    durTv.setGravity(Gravity.CENTER);
                                    durTv.setText(formatDuration(duration));
                                    LinearLayout.LayoutParams durLp = new LinearLayout.LayoutParams(durWidth, rowHeight);
                                    row.addView(durTv, durLp);
                                    // push button
                                    TextView push = new TextView(activity);
                                    push.setGravity(Gravity.CENTER);
                                    push.setPadding((int)(4*density), (int)(4*density), (int)(4*density), (int)(4*density));
                                    push.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_send, 0, 0, 0);
                                    push.setOnClickListener(v2 -> {
                                        Notify.show(url);
                                        String pushUrl =
                                                "http://127.0.0.1:" + AZ4.getPort()
                                                        + "/action?do=refresh&type=subtitle&path="
                                                        + android.net.Uri.encode(url);
                                        OkHttp.string(pushUrl);
                                    });
                                    LinearLayout.LayoutParams pushLp = new LinearLayout.LayoutParams(pushWidth, rowHeight);
                                    row.addView(push, pushLp);
                                    resultContainer.addView(row, rowLp);
                                    // divider line
                                    View divider = new View(activity);
                                    divider.setBackgroundColor(Color.parseColor("#DDDDDD"));
                                    LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT, (int)(1 * density));
                                    resultContainer.addView(divider, divLp);
                                }
                            });
                        } catch (Exception e) {
                            activity.runOnUiThread(() -> Notify.show("请求失败:" + e.getMessage()));
                        }
                    }).start();
                });
            });
            dialog.show();
        });
    }

    private static String formatDuration(long duration) {

        if (duration <= 0) return "";

        long totalSeconds = duration / 1000;

        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;


        if (hours > 0) {
            return String.format(
                    "%02d:%02d:%02d",
                    hours,
                    minutes,
                    seconds
            );
        }


        return String.format(
                "%02d:%02d",
                minutes,
                seconds
        );
    }
}