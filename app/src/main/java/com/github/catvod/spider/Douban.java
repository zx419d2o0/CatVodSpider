package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Notify;
import com.github.catvod.utils.Util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * @author Qile
 */
public class Douban extends Spider {

    private Context context;
    private static String siteUrl = "https://movie.douban.com";
    private static String apiUrl = "https://m.douban.com/rexxar/api/v2";
    private static int count = 100;

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", Util.CHROME);
        header.put("Referer", "https://movie.douban.com");
        header.put("Cookie",  "dbcl2=\"287441230:LzjOiUGT+LA\";");
        return header;
    }

    @Override
    public void init(Context context, String extend) {
        this.context = context;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {

        final CountDownLatch latch = new CountDownLatch(4);

        final List<Vod> chartList = new ArrayList<>();

        final JSONObject[] movieJson = new JSONObject[1];
        final JSONObject[] tvJson = new JSONObject[1];
        final JSONObject[] yearJson = new JSONObject[1];
        final String[] chartHtml = new String[1];

        // ======================
        // 1️⃣ chart 并发
        // ======================
        new Thread(() -> {
            try {
                chartHtml[0] = OkHttp.string(siteUrl + "/chart", getHeader());
            } catch (Exception ignored) {}
            latch.countDown();
        }).start();

        // ======================
        // 2️⃣ movie
        // ======================
        new Thread(() -> {
            try {
                String res = OkHttp.string(apiUrl + "/movie/recommend", getHeader());
                movieJson[0] = new JSONObject(res);
            } catch (Exception ignored) {}
            latch.countDown();
        }).start();

        // ======================
        // 3️⃣ tv
        // ======================
        new Thread(() -> {
            try {
                String res = OkHttp.string(apiUrl + "/tv/recommend", getHeader());
                tvJson[0] = new JSONObject(res);
            } catch (Exception ignored) {}
            latch.countDown();
        }).start();

        // ======================
        // 4️⃣ year
        // ======================
        new Thread(() -> {
            try {
                String res = OkHttp.string(apiUrl + "/movie/recommend/filter_tags", getHeader());
                yearJson[0] = new JSONObject(res);
            } catch (Exception ignored) {}
            latch.countDown();
        }).start();

        // ======================
        // 等待全部完成
        // ======================
        latch.await();

        // ======================
        // chart 解析
        // ======================
        if (chartHtml[0] != null) {
            Document doc = Jsoup.parse(chartHtml[0]);
            Elements items = doc.select("tr.item");

            for (Element el : items) {
                try {
                    String id = el.select("a").attr("href");
                    String name = el.select("div.pl2 a").text().replaceAll(" ", "");
                    String pic = el.select("img").attr("src");
                    String remarks = el.select("div.star").text().replaceAll(" ", "");

                    chartList.add(new Vod(id, name, pic, "⭐️" + remarks));
                } catch (Exception ignored) {}
            }
        }

        // ======================
        // movie / tv filters
        // ======================
        JSONArray movieArr = new JSONArray();
        JSONArray tvArr = new JSONArray();

// =======================
// 1️⃣ movie filters（直接安全解析）
// =======================
        if (movieJson[0] != null) {
            JSONObject movieData = getRecommend(movieJson[0]);
            JSONArray arr = movieData.optJSONArray("filters");

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    movieArr.put(arr.opt(i));
                }
            }
        }

// =======================
// 2️⃣ tv filters
// =======================
        if (tvJson[0] != null) {
            JSONObject tvData = getRecommend(tvJson[0]);
            JSONArray arr = tvData.optJSONArray("filters");

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    tvArr.put(arr.opt(i));
                }
            }
        }

// =======================
// 3️⃣ year filter（统一注入）
// =======================
        JSONArray yearTags = new JSONArray();

        if (yearJson[0] != null) {
            JSONArray tags = yearJson[0]
                    .optJSONArray("tags")
                    .optJSONObject(0)
                    .optJSONArray("tags");

            if (tags != null) {
                for (int i = 1; i < tags.length(); i++) {
                    String t = tags.optString(i);

                    JSONObject obj = new JSONObject();
                    obj.put("n", t);
                    obj.put("v", t);

                    yearTags.put(obj);
                }
            }
        }

        JSONObject yearFilter = new JSONObject();
        yearFilter.put("key", "year");
        yearFilter.put("name", "年代");
        yearFilter.put("value", yearTags);

// =======================
// 5️⃣ 合并（核心）
// =======================
        movieArr.put(yearFilter);

        tvArr.put(yearFilter);

// =======================
// 6️⃣ rank filter
// =======================
        JSONArray rankArr = new JSONArray();

        for (int i = 0; i < 9; i++) {
            int v = 85 - i * 10;

            JSONObject obj = new JSONObject();
            obj.put("n", "好于" + v + "%的片");
            obj.put("v", v);
            rankArr.put(obj);
        }

        JSONObject rankFilter = new JSONObject();
        rankFilter.put("key", "interval_id");
        rankFilter.put("name", "interval_id");
        rankFilter.put("value", rankArr);

// =======================
// 7️⃣ final filters
// =======================
        JSONObject filters = new JSONObject();

        filters.put("movie", movieArr);
        filters.put("tv", tvArr);
        filters.put("rank", new JSONArray().put(rankFilter));

        // ======================
        // result
        // ======================
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("movie", "tv", "rank", "top");
        List<String> typeNames = Arrays.asList("电影", "电视剧", "电影排行榜", "豆瓣电影 Top 250");
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));

        return Result.string(classes, chartList, filters);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        JSONObject jsonT = null;
        try {
            jsonT = new JSONObject(tid);
        } catch (Exception ignored){

        }
        List<Vod> list = new ArrayList<>();
        int page = pg == null || pg.isEmpty() ? 1 : Integer.parseInt(pg);
        int pageCount = 1;
            try {
                // =========================
                // 1️⃣ jsonT exists
                // =========================
                if (jsonT != null) {
                    String type = jsonT.optString("type");

                    // -------- json list --------
                    if ("json".equals(type)) {

                        JSONArray data = jsonT.optJSONArray("data");

                        if (data != null) {
                            for (int i = 0; i < data.length(); i++) {

                                JSONObject it = data.optJSONObject(i);
                                if (it == null) continue;

                                list.add(new Vod(
                                        it.optString("id"),
                                        it.optString("name"),
                                        "",
                                        "",
                                        new Vod.Style("list")
                                ));
                            }
                        }

                    }

                    // -------- rank --------
                    else if ("rank".equals(type)) {
                        String url = makeUrl(tid, page, extend, jsonT);

                        String res = OkHttp.string(url, getHeader());
                        JSONArray arr = new JSONArray(res);

                        for (int i = 0; i < arr.length(); i++) {

                            JSONObject it = arr.optJSONObject(i);
                            if (it == null) continue;

                            JSONArray rating = it.optJSONArray("rating");
                            JSONArray regions = it.optJSONArray("regions");

                            String rate = "";
                            String region = "";

                            if (rating != null && rating.length() > 0) {
                                rate = rating.optString(0);
                            }

                            if (regions != null && regions.length() > 0) {
                                region = regions.optString(0);
                            }

                            list.add(new Vod(
                                    it.optString("url"),
                                    it.optString("title"),
                                    it.optString("cover_url"),
                                    it.optString("rank") + ". ⭐️" + rate + " " + region
                            ));
                        }

                        pageCount = 40;
                    }

                }

                // =========================
                // 2️⃣ rank page
                // =========================
                else if ("rank".equals(tid)) {
                    String html = OkHttp.string(siteUrl + "/chart", getHeader());
                    Document doc = Jsoup.parse(html);

                    Elements items = doc.select("div.types a");

                    for (Element el : items) {

                        String href = el.attr("href");

                        String type = "";

                        try {
                            String query = href.contains("?") ? href.split("\\?")[1] : "";
                            JSONObject obj = new JSONObject();

                            String[] kvs = query.split("&");
                            for (String kv : kvs) {
                                String[] pair = kv.split("=");
                                if (pair.length == 2 && "type".equals(pair[0])) {
                                    type = pair[1];
                                }
                            }

                        } catch (Exception ignored) {}

                        JSONObject json = new JSONObject();
                        json.put("type", "rank");
                        json.put("data", type);

                        list.add(new Vod(
                                json.toString(),
                                el.text(),
                                "",
                                "",
                                true
                        ));
                    }

                }

                // =========================
                // 3️⃣ top250
                // =========================
                else if ("top".equals(tid)) {
                    String url = siteUrl + "/top250";

                    if (page > 1) {
                        url += "?start=" + ((page - 1) * 25);
                    }

                    String html = OkHttp.string(url, getHeader());
                    Document doc = Jsoup.parse(html);

                    Elements items = doc.select("div.item");

                    for (Element el : items) {

                        String id = el.select("div.pic a").attr("href");
                        String name = el.select("div.info a").first().text().replace(" ", "");
                        String pic = el.select("img").attr("src");
                        String remarks = el.select("div.bd p").first().text().trim();
                        String rank = el.select("em").text();

                        list.add(new Vod(
                                id,
                                rank + ". " + name,
                                pic,
                                remarks
                        ));
                    }

                    pageCount = 10;
                }

                // =========================
                // 4️⃣ normal recommend
                // =========================
                else {
                    String url = makeUrl(tid, page, extend, jsonT);

                    String res = OkHttp.string(url, getHeader());

                    JSONObject data = getRecommend(new JSONObject(res));

                    JSONArray arr = data.optJSONArray("list");

                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {

                            JSONObject it = arr.optJSONObject(i);
                            if (it == null) continue;

                            list.add(new Vod(
                                    it.optString("vod_id"),
                                    it.optString("vod_name"),
                                    it.optString("vod_pic"),
                                    it.optString("vod_remarks")
                            ));
                        }
                    }

                    pageCount = data.optInt("pageCount", 1);
                }

                // =========================
                // result
                // =========================
                return Result.get().page(1, pageCount, 0, list.size()).vod(list).string();

            } catch (Exception e) {
                Notify.show(e.toString());
                return Result.error(e.toString());
            }
    }

    public String detailContent(List<String> ids) {
        return Result.error("长按搜索!");
    }

    public JSONObject getRecommend(JSONObject data) {

        JSONArray recommendCategories = data.optJSONArray("recommend_categories");
        JSONArray filters = new JSONArray();

        try {
            if (recommendCategories != null) {

                for (int i = 0; i < recommendCategories.length(); i++) {

                    JSONObject categorie = recommendCategories.optJSONObject(i);
                    if (categorie == null) continue;

                    JSONArray cateValues = new JSONArray();
                    Set<String> dedup = new HashSet<>();

                    JSONArray list = categorie.optJSONArray("data");

                    if (list != null) {

                        for (int j = 1; j < list.length(); j++) {

                            JSONObject it = list.optJSONObject(j);
                            if (it == null) continue;

                            JSONArray tags = it.optJSONArray("tags");

                            if (tags != null && tags.length() > 0) {

                                for (int k = 0; k < tags.length(); k++) {
                                    String tag = tags.optString(k);

                                    if (!TextUtils.isEmpty(tag) && dedup.add(tag)) {
                                        JSONObject obj = new JSONObject();
                                        obj.put("n", tag);
                                        obj.put("v", tag);
                                        cateValues.put(obj);
                                    }
                                }

                            } else {

                                String text = it.optString("text");

                                if (!TextUtils.isEmpty(text) && dedup.add(text)) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("n", text);
                                    obj.put("v", text);
                                    cateValues.put(obj);
                                }
                            }
                        }
                    }

                    JSONObject categoryObj = new JSONObject();
                    categoryObj.put("key", categorie.optString("type"));
                    categoryObj.put("name", categorie.optString("type"));
                    categoryObj.put("value", cateValues);

                    filters.put(categoryObj);
                }
            }

            // =========================
            // sort
            // =========================
            JSONObject sortObj = new JSONObject();
            JSONArray sortValues = new JSONArray();

            JSONArray sorts = data.optJSONArray("sorts");
            if (sorts != null) {
                for (int i = 1; i < sorts.length(); i++) {
                    JSONObject sort = sorts.optJSONObject(i);
                    if (sort == null) continue;

                    JSONObject obj = new JSONObject();
                    obj.put("n", sort.optString("text"));
                    obj.put("v", sort.optString("name"));
                    sortValues.put(obj);
                }
            }

            sortObj.put("key", "sort");
            sortObj.put("name", "排序");
            sortObj.put("value", sortValues);

            filters.put(sortObj);

            // =========================
            // list（对应 TS items.map）
            // =========================
            JSONArray listArr = new JSONArray();

            JSONArray items = data.optJSONArray("items");
            if (items != null) {

                for (int i = 0; i < items.length(); i++) {

                    JSONObject it = items.optJSONObject(i);
                    if (it == null) continue;

                    try {
                        JSONObject vod = new JSONObject();
                        vod.put("vod_id", "https://movie.douban.com/subject/" + it.optString("id"));
                        vod.put("vod_name", it.optString("title"));

                        JSONObject pic = it.optJSONObject("pic");
                        if (pic == null) continue;
                        vod.put("vod_pic", pic != null ? pic.optString("normal") : "");

                        vod.put("vod_remarks", it.optString("card_subtitle"));

                        listArr.put(vod);

                    } catch (Exception ignored) {}
                }
            }

            // =========================
            // pagecount（TS: data.total/this.count|0 + 1）
            // =========================
            int total = data.optInt("total", 0);
            int pageCount = (total / count) + 1;

            JSONObject result = new JSONObject();
            result.put("pageCount", pageCount);
            result.put("list", listArr);
            result.put("filters", filters);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    public String makeUrl(String t, int page, HashMap<String, String> extend, JSONObject jsonT) {
        SpiderDebug.log(String.format(
                "t:%s page:%d extend:%s jsonT:%s",
                t,
                page,
                String.valueOf(extend),
                String.valueOf(jsonT)
        ));
        try {

            // =========================
            // 1️⃣ http直链
            // =========================
            if (t != null && t.startsWith("http")) {
                return t;
            }

            // =========================
            // 2️⃣ chart模式
            // =========================
            if (jsonT != null) {
                int interval = 90;
                if (extend != null && extend.containsKey("interval_id")){
                    interval = Integer.parseInt(extend.get("interval_id"));
                }
                StringBuilder url = new StringBuilder("https://movie.douban.com/j/chart/top_list");
                url.append("?start=").append((page - 1) * this.count);
                url.append("&limit=").append(this.count);
                url.append("&interval_id=").append(getIntervalString(interval));
                url.append("&type=").append(jsonT.optString("data"));

                return url.toString();
            }

            // =========================
            // 3️⃣ recommend模式
            // =========================
            String base = apiUrl + "/" + t + "/recommend";

            StringBuilder url = new StringBuilder(base);
            url.append("?start=").append((page - 1) * count);
            url.append("&count=").append(count);

            if (extend != null && !extend.isEmpty()) {

                List<String> tags = new ArrayList<>();

                for (Map.Entry<String, String> entry : extend.entrySet()) {

                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (TextUtils.isEmpty(value)) {
                        continue;
                    }

                    if ("sort".equals(key)) {
                        url.append("&sort=").append(value);
                    } else {
                        tags.add(value);
                    }
                }

                if (!tags.isEmpty()) {
                    url.append("&tags=").append(String.join(",", tags));
                }
            }

            return url.toString();

        } catch (Exception e) {
            return "";
        }
    }

    public String getIntervalString(int secondNumber) {

        int second = secondNumber;

        if (second <= 0) second = 90;

        int first = second + 10;

        if (first > 100) first = 100;

        if (first == 100) {
            second = first - 10;
        }

        return first + ":" + second;
    }
}
