package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.AZ4;
import com.github.catvod.utils.Notify;
import com.github.catvod.utils.Util;
import com.github.catvod.utils.WebViewUtil;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * @author Qile
 */
public class Sport extends Spider {

    private static String siteUrl;
    private static String bdUrl = "https://tiyu.baidu.com";
    private static Set<String> fbFilters = new HashSet<>();
    private static Map<String, JSONObject> bdSchedules = new TreeMap<>();
    private static Map<String, JSONArray> matchSchedules = new ConcurrentHashMap<>();
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        return header;
    }

    @Override
    public void init(Context context, String extend) {
        if (!extend.isEmpty()) siteUrl = extend;
        final CountDownLatch latch = new CountDownLatch(4);

        homeSchedule(getDate(0), latch);

        homeSchedule(getDate(-1), latch);

        homeSchedule(getDate(1), latch);

        new Thread(() -> {
            updateSiteUrl();
            latch.countDown();
        }).start();

        try {
            latch.await(); // 等待线程结束
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean filter) throws JSONException {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("1", "football", "21", "schedule");
        List<String> typeNames = Arrays.asList("NBA", "足球", "其他直播", "比赛赛程");
        for (int i = 0; i < typeIds.size(); i++)
            classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        JSONObject filterConfig = getFilters();
        List<Vod> list = getList("");
        return Result.string(classes, list, filterConfig);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        String cateId = extend.get("cateId");
        int pageCount = 1;
        List<Vod> list = new ArrayList<>();
        if ("football".equals(tid) && TextUtils.isEmpty(cateId)) {
            List<Vod> allGames = getList("");

            for (Vod vod : allGames) {
                String name = "";

                try {
                    String json = new Gson().toJson(vod);
                    name = new JSONObject(json).optString("vod_name");
                } catch (Exception ignored) {
                }

                if (!TextUtils.isEmpty(name)) {

                    boolean match = false;

                    for (String key : fbFilters) {
                        if (name.contains(key)) {
                            match = true;
                            break;
                        }
                    }

                    if (match) {
                        list.add(vod);
                    }
                }

            }
        } else if ("schedule".equals(tid) && !TextUtils.isEmpty(cateId)
                && !"football".equals(cateId) && !"basketball".equals(cateId)
        ) {
            int num = Integer.parseInt(pg);
            String lastDay = getDate(-1);
            if ("1".equals(pg)) {
                matchSchedules.clear();
            } else {
                JSONArray arr = matchSchedules.get(String.valueOf(num - 1));
                if (arr != null && arr.length() > 0) {
                    JSONObject lastObj = arr.optJSONObject(arr.length() - 1);
                    if (lastObj != null && !TextUtils.isEmpty(lastObj.optString("time"))) {
                        lastDay = lastObj.optString("time");
                    }
                }
            }
            JSONArray data = matchSchedule(cateId, lastDay);
            if (data.length() > 0){
                pageCount = num + 1;
            }
            synchronized (matchSchedules) {
                matchSchedules.put(pg, data);
            }
            for (int i = 0; i < data.length(); i++) {
                JSONObject dayObj = data.optJSONObject(i);
                if (dayObj == null) continue;
                String dateText = dayObj.optString("dateText");
                if (!TextUtils.isEmpty(dateText)) {
                    list.add(new Vod(siteUrl, dateText,
                            "https://pic.imgdb.cn/item/657673d6c458853aeff94ab9.jpg"));
                }
                JSONArray arr = dayObj.optJSONArray("list");
                if (arr == null) continue;
                list.addAll(parseScheduleList(arr, cateId));
            }
        } else if ("schedule".equals(tid)) {
            for (Map.Entry<String, JSONObject> entry : bdSchedules.entrySet()) {
                JSONObject dayObj = entry.getValue();
                String dateText = dayObj.optString("dateText");
                if (!TextUtils.isEmpty(dateText)) {
                    list.add(new Vod(siteUrl, dateText, "https://pic.imgdb.cn/item/657673d6c458853aeff94ab9.jpg"));
                }

                JSONArray arr = dayObj.optJSONArray("list");

                if (arr == null) continue;

                list.addAll(parseScheduleList(arr, cateId));
            }
        } else {
            String urlPath = cateId == null || cateId.isEmpty() ? String.format("/match/%s/live", tid) : String.format("/match/%s/live", cateId);
            list = getList(urlPath);
        }
        return Result.get().page(1, pageCount, 0, list.size()).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws JSONException {
        if (ids.get(0).equals(siteUrl)) return Result.error("比赛尚未开始");
        String base = ids.get(0);
        if (base.startsWith(bdUrl)){
            WebViewUtil.openHtml(base);
            Vod vod = new Vod();
            vod.setVodId(base);
            return Result.string(vod);
        }
        int index = base.lastIndexOf("/");
        String url = base.substring(0, index + 1) + "source";
        String content = OkHttp.string(url, getHeader());
        String result = new JSONObject(content).optString("data");
        result = result.substring(6);
        result = result.substring(0, result.length() - 2);
        String json = new String(Base64.decode(result, Base64.DEFAULT));
        JSONArray linksArray = new JSONObject(json).getJSONArray("links");
        List<String> vodItems = new ArrayList<>();
        for (int i = 0; i < linksArray.length(); i++) {
            JSONObject linkObject = linksArray.getJSONObject(i);
            String text = linkObject.optString("name");
            String href = linkObject.optString("url").replace("#", "***");
            vodItems.add(text + "$" + href);
        }
        Vod vod = new Vod();
        vod.setVodId(ids.get(0));
        vod.setVodPlayFrom("Qile");
        vod.setVodPlayUrl(TextUtils.join("#", vodItems));
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return Result.get().url(id.replace("***", "#")).parse().header(getHeader()).string();
    }

    private void updateSiteUrl() {
        try {

            String html = OkHttp.string("http://www.88kq.net", getHeader());
            Elements aTags = Jsoup.parse(html).select("div.site a[href]");

            List<String> urls = new ArrayList<>();

            for (Element a : aTags) {

                String href = a.attr("href");

                if (!TextUtils.isEmpty(href) && href.contains("www.88kanqiu.")) {
                    urls.add(href);
                }
            }

            String fastest = AZ4.pickFast(urls);

            if (!TextUtils.isEmpty(fastest)) {
                siteUrl = fastest;
            } else {
                Notify.show("site-url not found.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<Vod> getList(String urlPath) {
        Elements lis = Jsoup.parse(OkHttp.string(siteUrl + urlPath, getHeader())).select(".list-group-item");

        List<Vod> list = new ArrayList<>();

        for (Element li : lis) {
            String vid = siteUrl + li.select("a:not(.btn-disabled)").attr("href");

            String name = li.select(".row.d-none").text();
            if (name.isEmpty()) name = li.text();

            String pic = li.select(".col-xs-1").eq(0).select("img").attr("src");
            if (pic.isEmpty()) {
                pic = "https://pic.imgdb.cn/item/657673d6c458853aeff94ab9.jpg";
            }
            if (!pic.startsWith("http")) {
                pic = siteUrl + pic;
            }

            String remark = li.select(".btn.btn-primary").text();

            list.add(new Vod(vid, name, pic, remark));
        }

        return list;
    }

    private JSONObject getFilters() {
        try {

            fbFilters.clear();

            Elements lis = Jsoup.parse(OkHttp.string(siteUrl, getHeader())).select("ul.category-nav li.nav-header.category-nav-header");

            if (lis.size() < 3) return new JSONObject();

            Element start = lis.get(1);
            Element end = lis.get(2);

            JSONArray values = new JSONArray();

            for (Element el = start.nextElementSibling(); el != null && !el.equals(end); el = el.nextElementSibling()) {

                Elements as = el.select("a");

                for (Element a : as) {

                    String name = a.text().trim();

                    JSONObject item = new JSONObject();
                    item.put("n", name);

                    String href = a.attr("href");
                    String v = "";

                    if (!TextUtils.isEmpty(href) && href.split("/").length > 2) {
                        v = href.split("/")[2];
                    }

                    item.put("v", v);
                    values.put(item);

                    // store for fast filtering
                    if (!TextUtils.isEmpty(name)) {
                        fbFilters.add(name);
                    }
                }
            }

            JSONObject block = new JSONObject();
            block.put("key", "cateId");
            block.put("name", "比赛");
            block.put("value", values);

            JSONArray arr = new JSONArray();
            arr.put(block);

            JSONObject root = new JSONObject();
            root.put("football", arr);


            JSONArray scheduleValues = new JSONArray();
            Set<String> scheduleKeys = new HashSet<>();

            for (JSONObject dayObj : bdSchedules.values()) {
                if (dayObj == null) continue;

                JSONArray scheduleList = dayObj.optJSONArray("list");
                if (scheduleList == null) continue;
                for (int i = 0; i < scheduleList.length(); i++) {
                    JSONObject item = scheduleList.optJSONObject(i);
                    if (item == null) continue;

                    String game = item.optString("game");
                    String matchType = item.optString("matchType");

                    if (!"basketball".equals(matchType) && !"football".equals(matchType)) {
                        continue;
                    }

                    if (!TextUtils.isEmpty(game) && !scheduleKeys.contains(game)) {
                        scheduleKeys.add(game);

                        JSONObject obj = new JSONObject();
                        obj.put("n", game);
                        obj.put("v", game);
                        scheduleValues.put(obj);
                    }

                    if (!TextUtils.isEmpty(matchType) && !scheduleKeys.contains(matchType)) {
                        scheduleKeys.add(matchType);

                        JSONObject obj = new JSONObject();
                        obj.put("n", matchType);
                        obj.put("v", matchType);
                        scheduleValues.put(obj);
                    }
                }
            }

            JSONObject scheduleBlock = new JSONObject();
            scheduleBlock.put("key", "cateId");
            scheduleBlock.put("name", "比赛");
            scheduleBlock.put("value", scheduleValues);
            JSONArray scheduleArr = new JSONArray();
            scheduleArr.put(scheduleBlock);
            root.put("schedule", scheduleArr);

            return root;

        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    private String getDate(int offset) {

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

        java.util.Calendar cal = java.util.Calendar.getInstance();

        cal.add(java.util.Calendar.DATE, offset);

        return sdf.format(cal.getTime());

    }

    private void homeSchedule(String date, CountDownLatch latch) {

        new Thread(() -> {
            try {
                String url = "https://tiyu.baidu.com/al/api/home/schedule" + "?type=all&date=" + date;

                String res = OkHttp.string(url, getHeader());
                JSONObject data = new JSONObject(res).optJSONObject("data");

                synchronized (bdSchedules) {
                    bdSchedules.put(date, data);
                }

            } catch (Exception e) {
                SpiderDebug.log("error:" + e.toString());
                e.printStackTrace();
            } finally {
                latch.countDown();
            }

        }).start();
    }

    private JSONArray matchSchedule(String match, String date) {
        JSONArray result = new JSONArray();
        try {
            String url = String.format("https://tiyu.baidu.com/al/api/match/schedules?match=%s&date=%s&direction=after", match, date);
            String res = OkHttp.string(url, getHeader());
            result = new JSONObject(res).optJSONArray("data");
        } catch (Exception ignored) {
        } finally {
            return result;
        }
    }

    private List<Vod> parseScheduleList(JSONArray arr, String gameType) {
        List<Vod> list = new ArrayList<>();
        if (arr == null) return list;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;

            String game = item.optString("game");
            String matchType = item.optString("matchType");
            if (!TextUtils.isEmpty(gameType)){
                if (!gameType.equals(game) && !gameType.equals(matchType)) continue;
            }

            String link = item.optString("link");
            Uri uri = Uri.parse(bdUrl + link);
            Uri newUri = uri.buildUpon()
                    .clearQuery()
                    .appendQueryParameter("matchId", uri.getQueryParameter("matchId"))
                    .appendQueryParameter("tab", "聊天室")
                    .build();

            JSONObject left = item.optJSONObject("leftLogo");
            JSONObject right = item.optJSONObject("rightLogo");

            String leftName = left != null ? left.optString("name") : "";
            String rightName = right != null ? right.optString("name") : "";

            String vs = item.optString("vsLine");
            String status = item.optString("matchStatusText");

            String name = leftName + " " + vs + " " + rightName + " (" + status + ")";

            String startTime = item.optString("startTime");
            String stage = item.optString("matchStage");

            String desc = startTime + " " + stage;

            String pic = left != null ? left.optString("logo") : "";

            list.add(new Vod(newUri.toString(), name, pic, desc));
        }

        return list;
    }
}
