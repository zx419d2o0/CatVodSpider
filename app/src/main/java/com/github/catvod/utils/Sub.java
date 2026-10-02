package com.github.catvod.utils;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 字幕搜索按钮功能
 */
public class Sub extends PlayerFunc {
    private final String shooter_api_key;

    public Sub(String shooter_api_key) {
        this.shooter_api_key = shooter_api_key;
    }

    @Override
    public String getText() {
        return "字幕搜索";
    }

    @Override
    public String getTag() {
        return "btnSubtitle";
    }

    @Override
    public int getIndex() {
        return 3;
    }

    @Override
    public void onClick(View view) {
        if (!(view.getContext() instanceof Activity)) {
            return;
        }

        showSearchDialog((Activity) view.getContext());
    }

    @Override
    public boolean onLongClick(View view) {
        Notify.show("功能未实现");
        return true;
    }

    public void showSearchDialog(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            int pad = (int) (activity.getResources().getDisplayMetrics().density * 16);
            layout.setPadding(pad, pad, pad, pad);

            // Input box + source selector
            LinearLayout searchLayout = new LinearLayout(activity);
            searchLayout.setOrientation(LinearLayout.HORIZONTAL);

            final android.widget.EditText input = new android.widget.EditText(activity);
            input.setHint("请输入内容");

            searchLayout.addView(input, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            ));

            android.widget.Spinner source = new android.widget.Spinner(activity);
            String[] sources = {"射手", "迅雷"};

            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    activity,
                    android.R.layout.simple_spinner_item,
                    sources
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            source.setAdapter(adapter);

            searchLayout.addView(source, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            layout.addView(searchLayout, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            // Results area
            android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
            LinearLayout resultContainer = new LinearLayout(activity);
            resultContainer.setOrientation(LinearLayout.VERTICAL);

            scrollView.addView(resultContainer);

            layout.addView(scrollView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            ));

            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity)
                    .setTitle("搜索")
                    .setView(layout)
                    .setPositiveButton("搜索", null)
                    .setNegativeButton("取消", null)
                    .create();

            dialog.setOnShowListener(d -> {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v ->
                                searchSubtitle(
                                        activity,
                                        input,
                                        resultContainer,
                                        source.getSelectedItemPosition()
                                )
                        );
            });

            dialog.show();
        });
    }

    private void searchSubtitle(
            Activity activity,
            android.widget.EditText input,
            LinearLayout resultContainer,
            int source
    ) {
        String keyword = input.getText().toString().trim();

        if (keyword.isEmpty()) {
            Notify.show("请输入关键词");
            return;
        }

        new Thread(() -> {
            try {
                JSONArray data = source == 0
                        ? searchShooterSubtitle(keyword)
                        : searchThunderSubtitle(keyword);

                activity.runOnUiThread(() ->
                        showResults(activity, resultContainer, data)
                );
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                        Notify.show("请求失败:" + e.getMessage())
                );
            }
        }).start();
    }

    /**
     * 迅雷字幕搜索
     */
    private static JSONArray searchThunderSubtitle(String keyword) throws Exception {
        String api = "http://api-shoulei-ssl.xunlei.com/oracle/subtitle?name=" + keyword;
        String content = OkHttp.string(api);

        JSONObject json = new JSONObject(content);
        JSONArray data = json.optJSONArray("data");

        return data != null ? data : new JSONArray();
    }

    /**
     * 射手字幕搜索
     */
    private JSONArray searchShooterSubtitle(String keyword) throws Exception {
        String api = "https://api.assrt.net/v1/sub/search?q="
                + android.net.Uri.encode(keyword)
                + "&pos=0&filelist=1";

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + shooter_api_key);

        String content = OkHttp.string(api, headers);

        JSONObject json = new JSONObject(content);
        JSONObject sub = json.optJSONObject("sub");
        JSONArray subs = sub != null ? sub.optJSONArray("subs") : null;

        JSONArray data = new JSONArray();

        if (subs == null) {
            return data;
        }

        for (int i = 0; i < subs.length(); i++) {
            JSONObject item = subs.optJSONObject(i);
            if (item == null) {
                continue;
            }

            String id = item.optString("id");
            if (id.isEmpty()) {
                continue;
            }

            JSONArray fileList = item.optJSONArray("filelist");
            if (fileList == null || fileList.length() == 0) {
                continue;
            }

            JSONObject file = fileList.optJSONObject(0);
            if (file == null) {
                continue;
            }

            String name = file.optString("f");
            if (name.isEmpty()) {
                continue;
            }

            data.put(new JSONObject()
                    .put("name", name)
                    .put("duration", 0)
                    .put("url", "shooter," + id));
        }

        return data;
    }

    private JSONArray listShooterSubtitle(String id) throws Exception {
        String api = "https://api.assrt.net/v1/sub/detail?id=" + android.net.Uri.encode(id);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + shooter_api_key);

        String content = OkHttp.string(api, headers);

        JSONObject json = new JSONObject(content);
        JSONObject sub = json.optJSONObject("sub");
        JSONArray subs = sub != null ? sub.optJSONArray("subs") : null;
        JSONArray data = new JSONArray();

        if (subs == null) {
            return data;
        }

        for (int i = 0; i < subs.length(); i++) {
            JSONObject item = subs.optJSONObject(i);
            if (item == null) {
                continue;
            }

            JSONArray fileList = item.optJSONArray("filelist");
            if (fileList == null) {
                continue;
            }

            for (int j = 0; j < fileList.length(); j++) {
                JSONObject file = fileList.optJSONObject(j);
                if (file == null) {
                    continue;
                }

                String url = file.optString("url");
                if (!url.isEmpty()) {
                    data.put(url);
                }
            }
        }

        return data;
    }

    /**
     * 显示字幕搜索结果
     */
    private void showResults(
            Activity activity,
            LinearLayout resultContainer,
            JSONArray data
    ) {
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
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString("name");
            String url = item.optString("url");
            long duration = item.optLong("duration");

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    rowHeight
            );
            rowLp.topMargin = rowMargin;
            rowLp.bottomMargin = rowMargin;

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
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0,
                    rowHeight,
                    1f
            );
            row.addView(nameScroll, nameLp);

            TextView durTv = new TextView(activity);
            durTv.setGravity(Gravity.CENTER);
            durTv.setText(formatDuration(duration));

            LinearLayout.LayoutParams durLp = new LinearLayout.LayoutParams(
                    durWidth,
                    rowHeight
            );
            row.addView(durTv, durLp);

            TextView push = new TextView(activity);
            push.setGravity(Gravity.CENTER);
            push.setPadding(
                    (int) (4 * density),
                    (int) (4 * density),
                    (int) (4 * density),
                    (int) (4 * density)
            );
            push.setCompoundDrawablesWithIntrinsicBounds(
                    android.R.drawable.ic_menu_send,
                    0,
                    0,
                    0
            );
            push.setOnClickListener(v2 -> pushSubtitle(url));

            LinearLayout.LayoutParams pushLp = new LinearLayout.LayoutParams(
                    pushWidth,
                    rowHeight
            );
            row.addView(push, pushLp);

            resultContainer.addView(row, rowLp);

            View divider = new View(activity);
            divider.setBackgroundColor(
                    android.graphics.Color.parseColor("#DDDDDD")
            );

            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (1 * density)
            );
            resultContainer.addView(divider, divLp);
        }
    }

    private void pushSubtitle(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            String pushUrl =
                    "http://127.0.0.1:" + AZ4.getPort()
                            + "/action?do=refresh&type=subtitle&path="
                            + android.net.Uri.encode(value);
            Notify.show(value);
            OkHttp.string(pushUrl);
            return;
        }

        String[] parts = value.split(",", 2);
        if (parts.length != 2 || !"shooter".equals(parts[0])) {
            return;
        }

        new Thread(() -> {
            try {
                JSONArray urls = listShooterSubtitle(parts[1]);

                for (int i = 0; i < urls.length(); i++) {
                    String url = urls.optString(i);
                    if (url.isEmpty()) {
                        continue;
                    }

                    String pushUrl =
                            "http://127.0.0.1:" + AZ4.getPort()
                                    + "/action?do=refresh&type=subtitle&path="
                                    + Uri.encode(url);
                    Notify.show(String.format("%d", urls.length()));
                    OkHttp.string(pushUrl);
                }
            } catch (Exception e) {
                Notify.show("获取字幕失败:" + e.getMessage());
            }
        }).start();
    }

    private static String formatDuration(long duration) {
        if (duration <= 0) return "";

        long totalSeconds = duration / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%02d:%02d", minutes, seconds);
    }
}