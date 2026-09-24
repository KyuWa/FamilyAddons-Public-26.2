package org.kyowa.familyaddons.storage;

import org.kyowa.familyaddons.config.FamilyConfigManager;
import org.kyowa.familyaddons.config.StorageConfig;

/** The overlay's two settings, kept in FamilyAddons' config (Storage Overlay category). */
public final class Cfg {
    private Cfg() {}

    private static StorageConfig cfg() { return FamilyConfigManager.INSTANCE.getConfig().storage; }

    public static boolean enabled() { return cfg().enabled; }
    public static void setEnabled(boolean v) { cfg().enabled = v; FamilyConfigManager.INSTANCE.save(); }
    public static boolean showValue() { return cfg().containerValue; }

    /** 0 off, 1 left of the pages, 2 right of them. */
    public static int valuePanelSide() { return cfg().valuePanelSide; }

    public static int alpha() { return Math.max(0, Math.min(100, Math.round(cfg().alpha))); }
    public static void setAlpha(int v) { cfg().alpha = Math.max(0, Math.min(100, v)); FamilyConfigManager.INSTANCE.save(); }

    /** First run after the merge: carry the old mod's on/off and opacity over from its storage.json. */
    public static void migrateFrom(StorageData old) {
        StorageConfig c = cfg();
        if (c.migrated) return;
        c.enabled = old.modEnabled;
        c.alpha = Math.max(0, Math.min(100, old.alpha));
        c.migrated = true;
        FamilyConfigManager.INSTANCE.save();
    }
}
