package com.wenzejin.core.mr.assertion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class TestAssertionTypeAdapter extends TypeAdapter<TestAssertion> {

    private final Gson gson = new GsonBuilder().registerTypeAdapter(TestAssertion.class, this)
            .create();  // 可以直接使用一个 gson 实例

    @Override
    public void write(JsonWriter out, TestAssertion value) throws IOException {
        out.beginObject();
        out.name("type").value(value.getClass().getSimpleName());  // 写入类型信息
        out.name("content");
        gson.toJson(value, value.getClass(), out);  // 序列化对象
        out.endObject();
    }

    @Override
    public TestAssertion read(JsonReader in) throws IOException {
        in.beginObject();
        String type = null;
        JsonElement content = null;

        // 读取 JSON 中的字段
        while (in.hasNext()) {
            String name = in.nextName();
            if (name.equals("type")) {
                type = in.nextString();
            } else if (name.equals("content")) {
                content = gson.fromJson(in, JsonElement.class);  // 解析为 JsonElement
            }
        }
        in.endObject();

        // 获取对应的 TestAssertion 实现类
        Class<? extends TestAssertion> assertionClass = TestAssertionRegistry.get(type);

        if (assertionClass != null) {
            // 使用 gson 来反序列化为相应的实现类
            return gson.fromJson(content, assertionClass);
        } else {
            throw new IOException("Unknown TestAssertion type: " + type);
        }
    }
}