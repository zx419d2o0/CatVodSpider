package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.AZ4;
import com.github.catvod.utils.Notify;
import com.github.catvod.utils.Sub;

import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Qile
 */
public class Fengye extends Spider {

    private static String siteUrl;
    private static Set<String> domains = new HashSet<>();
    private static String searchApi = "";
    AtomicReference<String> keyword = new AtomicReference<>();

    @Override
    public void init(Context context, String extend) {
        if (!extend.isEmpty()) siteUrl = extend;
        updateSiteUrl();
        Sub.startHookMonitor();
    }

    @Override
    public String homeContent(boolean filter) throws JSONException {
        List<Vod> list = new ArrayList<>();
        list.add(new Vod("search", "仅支持搜索", "", "", true));
        for (String domain : domains) {
            list.add(new Vod("config", domain, "", "", "config|" + domain));
        }
        String html = OkHttp.string(siteUrl);
        Elements forms = Jsoup.parse(html).select("form");
        String[] actions = forms.first().attr("action").split("/");
        if (actions.length > 1) {
            searchApi = actions[1];
        }
        return Result.string(list);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        if ("1".equals(pg)) {
            keyword.set(null);
            CountDownLatch latch = new CountDownLatch(1);
            Init.post(() -> {
                AZ4.showInput("搜索", text -> {
                    if (text != null) {
                        keyword.set(text);
                    }
                    latch.countDown();
                });
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String text = keyword.get();
        if (!TextUtils.isEmpty(text)) {
            return searchContent(text, false, pg);
        }
        return Result.error("search");
    }

    @Override
    public String action(String action){
        String[] parts = action.split("\\|", 2);
        String key = action, value = action;
        if (parts.length > 1) {
            key = parts[0];
            value = parts[1];
        }

        if ("config".equals(key)) {
            siteUrl = value;
            return Result.notify("设置新域名:" + siteUrl);
        }

        return "ok";
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        int pageCount = 99;
        String wd = key.replaceAll("\\s+", "");
        try {
            wd = URLEncoder.encode(wd, "UTF-8").replace("+", "%20");
        } catch (Exception ignored) {
        }
        String url = String.format("%s%s/%s----------%s---.html", siteUrl, searchApi, wd, pg);

        Elements divs = Jsoup.parse(OkHttp.string(url)).select("div.module-card-item.module-item");

        List<Vod> list = new ArrayList<>();

        for (Element div : divs) {
            String vodId = div.select("a").first().attr("href");

            String vodName = div.select("strong").text().trim();

            String tags = div.select("div.module-item-note").text().trim();

            String vodPic = div.select("div.module-item-pic img").attr("data-src");

            String vodRemarks = div.select("div.module-card-item-class").text().trim();

            String content = div.select("div.module-info-item-content").text().trim();

            list.add(new Vod(vodId, vodName + "[" + tags + "]", vodPic, vodRemarks + " " + content));
        }
        if (list.isEmpty()) pageCount = 1;
        return Result.get().page(1, pageCount, 0, list.size()).vod(list).string();
    }

    @Override
    public String detailContent(List<String> ids) throws JSONException {
        String id = ids.get(0);
        if (!id.startsWith("/detail")) return Result.error("只能播放搜索内容");
        Document doc = Jsoup.parse(OkHttp.string(AZ4.joinUrl(siteUrl, ids.get(0))));
        String vodName = doc.select("div.module-info-main h1").text().trim();
        String vodYear = doc.select("div.module-info-main div.module-info-tag-link").first().text();
        String vodContent = doc.select("div.module-info-content div.module-info-introduction-content p").text().trim();
        Elements infos = doc.select("div.module-info-content span.module-info-item-content");
        String vodDirector = "", vodActor = "", vodTag = "";
        if (infos.size() > 3) {
            vodDirector = infos.first().text().trim();
            vodActor = infos.get(1).text().trim();
            vodTag = infos.last().text().trim();
        }
        List<String> vodPlayFrom = new ArrayList<>();
        List<String> vodPlayUrl = new ArrayList<>();
        Elements uls = doc.select("ul.mx-anthology-grid");
        for (int i = 0; i < uls.size(); i++) {
            vodPlayFrom.add("线路" + (i + 1));
            List<String> urls = new ArrayList<>();
            Elements aTags = uls.get(i).select("a");
            for (Element a : aTags) {
                String title = a.text().trim();
                String url = a.attr("href");
                urls.add(title + "$" + url);
            }
            Collections.reverse(urls);
            vodPlayUrl.add(TextUtils.join("#", urls));
        }
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(vodName);
        vod.setVodYear(vodYear);
        vod.setVodContent(vodContent);
        vod.setVodDirector(vodDirector);
        vod.setVodActor(vodActor);
        vod.setVodTag(vodTag);
        vod.setVodPlayFrom(TextUtils.join("$$$", vodPlayFrom));
        vod.setVodPlayUrl(TextUtils.join("$$$", vodPlayUrl));
        return Result.string(vod);
    }

    //    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        String html = OkHttp.string(AZ4.joinUrl(siteUrl, id));
        String url = id;
        Matcher matcher = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+?\\.m3u8)\"").matcher(html);
        if (matcher.find()) {
            url = matcher.group(1).replace("\\/", "/");
        }
        return Result.get().url(url).string();
    }

    private void updateSiteUrl() {
        try {
            String html = OkHttp.string("https://vip1949.com");
            Element script = Jsoup.parse(html).selectFirst("script");
            String js = script.html();
            if (js.contains("const domains")) {
                Pattern pattern = Pattern.compile("url\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(js);
                while (matcher.find()) {
                    String url = matcher.group(1);
                    domains.add(url);
                    siteUrl = url;
                }
            }
        } catch (Exception e) {
            Notify.show(e.toString());
        }
    }
}
