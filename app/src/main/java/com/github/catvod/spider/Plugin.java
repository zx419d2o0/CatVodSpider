package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Sub;
import com.google.gson.JsonObject;

public class Plugin extends Spider {

    private JsonObject extend;

    @Override
    public void init(Context context, String extend) {
        this.extend = Json.safeObject(extend);
        String SubKey = this.extend.get("shooter_key").getAsString();
        new Sub(SubKey).start();
    }
}
