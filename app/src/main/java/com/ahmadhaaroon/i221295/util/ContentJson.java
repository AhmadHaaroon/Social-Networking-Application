package com.ahmadhaaroon.i221295.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Maps the existing public-field screen models to the PHP and SQLite representation. */
public final class ContentJson {

    private ContentJson() {
    }

    public static JSONObject write(Object model) {
        try {
            JSONObject out = new JSONObject();
            for (Field f : model.getClass().getFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object value = f.get(model);
                out.put(f.getName(), value instanceof List ? new JSONArray((List<?>) value) : value == null ? JSONObject.NULL : value);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T read(JSONObject json, Class<T> type) {
        try {
            T out = type.getDeclaredConstructor().newInstance();
            for (Field f : type.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !json.has(f.getName()) || json.isNull(f.getName())) continue;
                String key = f.getName();
                if (f.getType() == String.class) f.set(out, json.getString(key));
                else if (f.getType() == long.class) f.setLong(out, json.getLong(key));
                else if (f.getType() == int.class) f.setInt(out, json.getInt(key));
                else if (f.getType() == float.class) f.setFloat(out, (float) json.getDouble(key));
                else if (f.getType() == boolean.class) f.setBoolean(out, json.getBoolean(key));
                else if (List.class.isAssignableFrom(f.getType())) {
                    List<String> list = new ArrayList<>();
                    JSONArray values = json.getJSONArray(key);
                    for (int i = 0; i < values.length(); i++) list.add(values.getString(i));
                    f.set(out, list);
                }
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
