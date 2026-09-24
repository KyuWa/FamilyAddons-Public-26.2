package org.kyowa.familyaddons.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * A JSON file in {@code config/FamilyStorage/} that is
 * loaded on construction and written on {@link #save()}.
 */
public class PersistentObject {

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(StorageData.class, new StorageData.Adapter())
            .create();

    private final File file;
    public StorageData data;

    public PersistentObject(String name, StorageData obj) {
        File dir = new File(Minecraft.getInstance().gameDirectory, "config/FamilyStorage");
        this.file = new File(dir, name + ".json");
        StorageData loaded = null;
        if (file.exists()) {
            try {
                String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (!text.isBlank()) loaded = GSON.fromJson(text, StorageData.class);
            } catch (Exception e) {
                FamilyStorage.LOGGER.error("Failed to read " + file, e);
            }
        }
        if (loaded == null) {
            this.data = obj;
            write();
        } else {
            this.data = loaded;
        }
    }

    private static final java.util.concurrent.ExecutorService WRITER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "FamilyStorage-save");
                t.setDaemon(true);
                return t;
            });

    /**
     * Saves the current state of the persistent object to the associated file. The JSON is built
     * on the calling thread (the data is only ever touched from the render thread); the disk write
     * happens on a background thread so the game does not hitch.
     */
    public PersistentObject save() {
        String json;
        try {
            json = GSON.toJson(data);
        } catch (Exception e) {
            FamilyStorage.LOGGER.error("Failed to serialise " + file, e);
            return this;
        }
        WRITER.submit(() -> writeText(json));
        return this;
    }

    private void write() {
        try {
            writeText(GSON.toJson(data));
        } catch (Exception e) {
            FamilyStorage.LOGGER.error("Failed to write " + file, e);
        }
    }

    private void writeText(String json) {
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            FamilyStorage.LOGGER.error("Failed to write " + file, e);
        }
    }
}
