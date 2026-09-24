package org.kyowa.familyaddons.storage;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The persisted storage layout, identical to the JavaScript module's
 * {@code storage.json}:
 *
 * <pre>
 * {
 *   "currentProfile": "Peach" | null,
 *   "modEnabled": true,
 *   "<profile>": { "enderchest": {...}, "backpacks": {...}, "scroll": 0 },
 *   ...
 * }
 * </pre>
 *
 * Profiles are stored next to {@code currentProfile}/{@code modEnabled} at the top level
 * (see {@link Adapter}).
 */
public class StorageData {

    public String currentProfile = null;
    public boolean modEnabled = true;
    /** opacity of the page/inventory panels and slots in percent (0 = invisible, 100 = solid) */
    public int alpha = 20;
    /** profile name -> profile data (flattened into the top-level object on disk). */
    public Map<String, ProfileData> profiles = new LinkedHashMap<>();

    public static class ProfileData {
        public TreeMap<Integer, PageData> enderchest = new TreeMap<>();
        public TreeMap<Integer, PageData> backpacks = new TreeMap<>();
        /** saved scroll position for this profile's storage GUI */
        public double scroll = 0;

        /** The empty starting state. */
        public static ProfileData getDefault() {
            ProfileData p = new ProfileData();
            for (int i = 1; i <= 9; i++) {
                PageData page = new PageData();
                page.command = "ec " + i;
                p.enderchest.put(i, page);
            }
            for (int i = 1; i <= 18; i++) {
                PageData page = new PageData();
                page.command = "bp " + i;
                p.backpacks.put(i, page);
            }
            return p;
        }
    }

    public static class PageData {
        /** slot index -> raw item NBT (SNBT) or null for an empty slot. */
        public TreeMap<Integer, String> items = new TreeMap<>();
        public String command;
        public Integer index;
        public String name;
        public Integer size;
    }

    /** Flattens {@link #profiles} into the top level object, exactly like the JS file. */
    public static class Adapter implements JsonSerializer<StorageData>, JsonDeserializer<StorageData> {

        @Override
        public JsonElement serialize(StorageData src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject o = new JsonObject();
            o.add("currentProfile", src.currentProfile == null ? JsonNull.INSTANCE : new JsonPrimitive(src.currentProfile));
            o.add("modEnabled", new JsonPrimitive(src.modEnabled));
            o.add("alpha", new JsonPrimitive(src.alpha));
            for (Map.Entry<String, ProfileData> e : src.profiles.entrySet()) {
                o.add(e.getKey(), context.serialize(e.getValue(), ProfileData.class));
            }
            return o;
        }

        @Override
        public StorageData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            StorageData data = new StorageData();
            if (!json.isJsonObject()) return data;
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                String key = e.getKey();
                JsonElement v = e.getValue();
                if (key.equals("currentProfile")) {
                    data.currentProfile = v == null || v.isJsonNull() ? null : v.getAsString();
                } else if (key.equals("modEnabled")) {
                    data.modEnabled = v != null && !v.isJsonNull() && v.getAsBoolean();
                } else if (key.equals("alpha")) {
                    if (v != null && v.isJsonPrimitive()) data.alpha = Math.max(0, Math.min(100, v.getAsInt()));
                } else if (v != null && v.isJsonObject()) {
                    ProfileData p = context.deserialize(v, ProfileData.class);
                    if (p != null) {
                        if (p.enderchest == null) p.enderchest = new TreeMap<>();
                        if (p.backpacks == null) p.backpacks = new TreeMap<>();
                        for (PageData page : p.enderchest.values()) if (page.items == null) page.items = new TreeMap<>();
                        for (PageData page : p.backpacks.values()) if (page.items == null) page.items = new TreeMap<>();
                        data.profiles.put(key, p);
                    }
                }
            }
            return data;
        }
    }
}
