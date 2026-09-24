package org.kyowa.familyaddons.storage;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FamilyStorage – the stored pages, /familystorage, profile detection and setItems.
 */
public class FamilyStorage {

    public static final Logger LOGGER = LoggerFactory.getLogger("FamilyStorage");

    public static PersistentObject storage;
    /** True once init ran; the mixins stay quiet otherwise (old mod installed alongside). */
    public static boolean active = false;

    private static final Pattern PROFILE_PLAYING = Pattern.compile("You are playing on profile: (\\S+)");
    private static final Pattern PROFILE_SWITCHING = Pattern.compile("Switching to profile (\\S+)\\.\\.\\.");
    private static final Pattern ENDER_CHEST_TITLE = Pattern.compile("Ender Chest \\((\\d+)/\\d+\\)");
    private static final Pattern BACKPACK_TITLE = Pattern.compile("(?:.+)Backpack(?:.+)\\(Slot #(\\d+)\\)");

    /** Called from FamilyAddons' client init. Stands down if the old separate mod is still installed. */
    public static void init() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("familystorage")) {
            LOGGER.warn("FamilyStorage is installed as its own mod; FamilyAddons' built-in Storage Overlay stays off. Remove the FamilyStorage jar.");
            return;
        }
        LOGGER.info("Storage Overlay loading...");

        storage = new PersistentObject("storage", new StorageData());

        // /familystorage and /fs (toggle), plus "alpha <0-100>" on both
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(buildCommand("familystorage"));
            dispatcher.register(buildCommand("fs"));
        });

        // packetReceived (S02PacketChat, non action bar): profile detection
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            onChatMessage(message);
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                onChatMessage(message));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Scheduler.tick();
            StorageOverlay.tick();
            if (client.level != null) pollProfile();
        });

        Cfg.migrateFrom(storage.data);
        active = true;
        LOGGER.info("Storage Overlay loaded!");
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name) {
        return ClientCommands.literal(name)
                .executes(ctx -> {
                    Cfg.setEnabled(!Cfg.enabled());
                    storage.save();
                    Utils.chat("&e[FamilyStorage] Family Storage gui enabled is now: &6" + Cfg.enabled());
                    return 1;
                })
                .then(ClientCommands.literal("alpha")
                        .executes(ctx -> {
                            Utils.chat("&e[FamilyStorage] Panel opacity is &6" + Cfg.alpha()
                                    + "%&e. Use &6/fs alpha <0-100>");
                            return 1;
                        })
                        .then(ClientCommands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> {
                                    Cfg.setAlpha(IntegerArgumentType.getInteger(ctx, "percent"));
                                    storage.save();
                                    Utils.chat("&e[FamilyStorage] Panel opacity set to &6" + Cfg.alpha() + "%");
                                    return 1;
                                })));
    }

    // ---------------- profile storage ----------------

    private static boolean warnedNoProfile = false;

    public static void setProfileStorage(String profile) {
        String previous = storage.data.currentProfile;
        storage.data.currentProfile = profile;
        // Pages saved under the placeholder belong to the first real profile seen.
        if ("default".equals(previous) && !"default".equals(profile) && !storage.data.profiles.containsKey(profile)) {
            StorageData.ProfileData held = storage.data.profiles.remove("default");
            if (held != null) storage.data.profiles.put(profile, held);
        }
        storage.save();
        if (storage.data.profiles.containsKey(profile)) return;
        storage.data.profiles.put(profile, StorageData.ProfileData.getDefault());
        storage.save();
        Utils.chat("&e[FamilyStorage] Initialized storage data for profile " + profile);
    }

    /** The profile name the tab list shows ("Profile: Apple"), or null. Formatting stripped. */
    private static final Pattern TAB_PROFILE = Pattern.compile("^Profile: (\\S+)");

    private static String profileFromTabList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        for (var entry : mc.getConnection().getOnlinePlayers()) {
            Component name = entry.getTabListDisplayName();
            if (name == null) continue;
            Matcher m = TAB_PROFILE.matcher(Utils.removeFormatting(name.getString()).trim());
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static int tabPollTicks = 0;

    /**
     * Reads the profile off the tab list once a second. The chat lines only come
     * when you enter SkyBlock, so someone who installed the mod mid-session never
     * saw one, and used to be told to switch lobbies every time storage opened.
     */
    private static void pollProfile() {
        if (++tabPollTicks < 20) return;
        tabPollTicks = 0;
        String seen = profileFromTabList();
        if (seen != null && !seen.equals(storage.data.currentProfile)) setProfileStorage(seen);
    }

    public static StorageData.ProfileData getStorage() {
        if (storage.data.currentProfile == null) {
            // Not known yet: try the tab list right now; failing that, work under a
            // placeholder profile rather than nagging, and say so once.
            String seen = profileFromTabList();
            if (seen != null) setProfileStorage(seen);
            else {
                if (!warnedNoProfile) {
                    warnedNoProfile = true;
                    Utils.chat("&e[FamilyStorage] Could not read your profile yet; saving under \"default\" until it shows up.");
                }
                storage.data.currentProfile = "default";
                storage.save();
            }
        }
        StorageData.ProfileData profile = storage.data.profiles.get(storage.data.currentProfile);
        if (profile == null) {
            profile = StorageData.ProfileData.getDefault();
            storage.data.profiles.put(storage.data.currentProfile, profile);
        }
        return profile;
    }

    // ---------------- chat ----------------

    private static void onChatMessage(Component message) {
        try {
            String plain = Utils.removeFormatting(message.getString());
            Matcher match;
            if ((match = PROFILE_PLAYING.matcher(plain)).find()) {
                setProfileStorage(match.group(1));
            } else if ((match = PROFILE_SWITCHING.matcher(plain)).find()) {
                setProfileStorage(match.group(1));
            }
        } catch (Exception e) {
            LOGGER.error("Chat handler failed", e);
        }
    }

    // ---------------- setItems ----------------

    /** Saves the currently open container (ender chest page or backpack). */
    public static void setItems() {
        setItems(false);
    }

    /**
     * Saves the currently open container (ender chest page or backpack).
     *
     * @param skipIfEmpty when true, a page whose storage slots are all empty is not written –
     *                    used for the early snapshots after opening, when the server may not have
     *                    sent the contents yet (the snapshot on close always writes).
     */
    public static void setItems(boolean skipIfEmpty) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        AbstractContainerMenu container = screen.getMenu();
        int size = container.slots.size();
        String name = screen.getTitle().getString();
        List<ItemStack> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) items.add(container.getSlot(i).getItem());

        Matcher match;
        TreeMap<Integer, StorageData.PageData> pages;
        String defaultCommand;
        if ((match = ENDER_CHEST_TITLE.matcher(name)).find()) {
            pages = getStorage().enderchest;
            defaultCommand = "ec ";
        } else if ((match = BACKPACK_TITLE.matcher(name)).find()) {
            pages = getStorage().backpacks;
            defaultCommand = "bp ";
        } else {
            return;
        }

        if (skipIfEmpty) {
            boolean anyItem = false;
            for (int i = 9; i < size - 36; i++) {
                if (!items.get(i).isEmpty()) {
                    anyItem = true;
                    break;
                }
            }
            if (!anyItem) return; // contents not received yet
        }

        int page = Integer.parseInt(match.group(1));
        StorageData.PageData data = pages.get(page);
        if (data == null) {
            data = new StorageData.PageData();
            data.command = defaultCommand + page;
            pages.put(page, data);
        }
        data.index = page - 1;
        data.name = name;
        data.size = size - 45;
        if (data.items == null) data.items = new TreeMap<>();
        for (int i = 9; i < size - 36; i++) {
            ItemStack stack = items.get(i);
            data.items.put(i, stack.isEmpty() ? null : Utils.getRawNBT(stack));
        }
        storage.save();
    }
}
