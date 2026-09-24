package org.kyowa.familyaddons.storage;

import org.kyowa.familyaddons.features.ItemValue;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The drawing half of the storage overlay: startDraw, drawStorage and their state. */
public final class StorageOverlay {

    private StorageOverlay() {}

    /**
     * Minimum size (in GUI units) the layout needs: three 175px page columns, and one page row
     * plus the inventory block. If the window is too small for that at the game's GUI scale the
     * overlay steps down to the next smaller scale until it fits.
     */
    private static final int MIN_LAYOUT_WIDTH = 530;
    private static final int MIN_LAYOUT_HEIGHT = 220;

    /** Gap between the bottom of the page area and the main inventory block. */
    private static final double INVENTORY_OFFSET = 10;

    // -------- draw data --------

    static final class DrawPage {
        String name;
        Integer index;
        int size;
        String command;
        List<ItemStack> items = new ArrayList<>();
    }

    static final class DrawData {
        List<DrawPage> enderchest = new ArrayList<>();
        List<DrawPage> backpacks = new ArrayList<>();
        List<ItemStack> inventory = new ArrayList<>();
    }

    private record Tooltip(List<Component> lines, double x, double y, String key) {}

    // scrollable tooltips: vertical offset (scaled px) applied to the tooltip of the hovered item
    private static int tooltipScroll = 0;
    private static String lastTooltipKey = null;
    private static final int TOOLTIP_SCROLL_STEP = 24;
    /** true while the hovered item's tooltip is taller than the screen (so the wheel scrolls it) */
    private static boolean hoveredTooltipScrollable = false;

    /**
     * Vanilla placement, except that a tooltip taller than the screen starts at the top (instead of
     * being pushed up so only its end shows), shifted by {@link #tooltipScroll} and kept from
     * scrolling past its ends.
     */
    private static final ClientTooltipPositioner SCROLLING_POSITIONER = (screenW, screenH, mouseX, mouseY, w, h) -> {
        Vector2ic base = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenW, screenH, mouseX, mouseY, w, h);
        // never let the top of the tooltip start above the screen: read from the top, scroll down
        int baseY = (h + 8 > screenH || base.y() < 4) ? 4 : base.y();
        int minOffset = Math.min(0, screenH - 4 - h - baseY); // bottom edge on screen
        int maxOffset = Math.max(0, 4 - baseY);               // top edge on screen
        tooltipScroll = Math.max(minOffset, Math.min(maxOffset, tooltipScroll));
        return new Vector2i(base.x(), baseY + tooltipScroll);
    };

    /** Everything startDraw() registered for one opened storage GUI. */
    static final class Session {
        String name;
        DrawData drawData;
        int stepCounter = 0;
    }

    // -------- shared draw helpers / globals --------

    private static double scrollBuffer = 0;
    private static double scroll = 0;
    /** Set when a page was just opened (/ec 3, a backpack): the next frame scrolls so that page is in view. */
    private static boolean jumpToCurrent = false;
    // scrollbar drag state
    private static boolean barDragging = false;
    private static boolean barPressedOutside = false;
    private static double barDragOffset = 0;
    private static ItemStack heldItem = null;
    private static boolean shouldBeAbleToClick = false;
    private static boolean shouldBeAbleToRightClick = false;
    private static final List<Tooltip> tooltips = new ArrayList<>();

    // global: hovering any *active* item in our GUI this frame
    private static boolean hoveringStorageItem = false;

    // global flag to debounce heavy updates
    private static boolean needsRefresh = false;

    // cache for NBT -> Item
    private static final Map<String, ItemStack> itemCache = new HashMap<>();

    private static Runnable activeUpdateDrawData = null;

    private static boolean keepMenuOpen = false;

    // anti-spam for page switch commands
    private static long lastPageCommandMs = 0;
    private static final long PAGE_COMMAND_COOLDOWN_MS = 1000;
    private static final long PENDING_PAGE_TIMEOUT_MS = 3000;

    // cache for NBT -> lower-case searchable text (name + tooltip + raw NBT)
    private static final Map<String, String> searchTextCache = new HashMap<>();

    public static final TextInput input = new TextInput(false, "Search storage...");

    /** Panel background (packs override assets/minecraft/textures/gui/demo_background.png). */
    private static final Identifier DEMO_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/demo_background.png");
    private static final int DEMO_PANEL_W = 248, DEMO_PANEL_H = 166, DEMO_BORDER = 4;
    /** Vanilla slot sprite (assets/minecraft/textures/gui/sprites/container/slot.png). */
    private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
    private static final int SEARCH_HIGHLIGHT = Utils.color(255, 255, 85, 120);

    private static Session session = null;

    private static final Pattern STORAGE_TITLE =
            Pattern.compile("^(Storage|Ender Chest \\((\\d+)/\\d+\\)|(?:.+)Backpack(?:.+)\\(Slot #(\\d+)\\))$");

    public static boolean isActive() {
        return session != null;
    }

    /**
     * scaled-gui units -> overlay units. The overlay uses the game's GUI scale, reduced step by
     * step while the layout would not fit the window at that scale.
     */
    private static double scaleFactor() {
        var window = Minecraft.getInstance().getWindow();
        int guiScale = window.getGuiScale();
        if (guiScale <= 0) guiScale = 1;
        int windowW = window.getGuiScaledWidth() * guiScale;
        int windowH = window.getGuiScaledHeight() * guiScale;
        int scale = guiScale;
        while (scale > 1 && (windowW / scale < MIN_LAYOUT_WIDTH || windowH / scale < MIN_LAYOUT_HEIGHT)) scale--;
        return guiScale / (double) scale;
    }

    private static ItemStack getCachedItemFromNBT(String nbt) {
        if (nbt == null) return null;
        if (!itemCache.containsKey(nbt)) {
            itemCache.put(nbt, Utils.getItemFromNBT(nbt));
        }
        return itemCache.get(nbt);
    }

    /**
     * Draws an item and handles tooltip / hover tracking.
     *
     * @param size         optional stack size indicator
     * @param isActiveArea whether this item belongs to the "active window"
     */
    private static void drawItem(GuiGraphicsExtractor ctx, ItemStack item, double x, double y, double mx, double my,
                                 Long size, boolean isActiveArea) {
        if (item == null || item.isEmpty()) return;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        ctx.item(item, ix, iy);
        Utils.drawStackSize(ctx, item, ix, iy, size);
        if (mx > x && mx < x + 18 && my > y && my < y + 18) {
            if (isActiveArea) {
                // only set this when hovering items in the active window
                hoveringStorageItem = true;
            }
            tooltips.add(new Tooltip(Utils.getLore(item), mx, my, ix + "," + iy + (isActiveArea ? "a" : "")));
        }
    }

    // scrollbar thumb
    private static final int KNOB_ON_TOP = Utils.color(200, 110, 255);

    // ── what a container is worth ─────────────────────────────────────
    // Pricing a page means reading every item's NBT, so an answer is kept for a
    // moment instead of being worked out again on every frame. Keyed by page so
    // the panel (the open one) and the figures (whichever you point at) share it.
    private static final long VALUE_REFRESH_MS = 250;
    private static final double VALUE_PANEL_W = 150;
    private static final Map<String, Valued> valueCache = new HashMap<>();

    private record Valued(String text, List<Component> lines, long at) {}

    private static Valued valueOf(String pageName, List<ItemStack> items) {
        long now = System.currentTimeMillis();
        Valued cached = valueCache.get(pageName);
        if (cached != null && now - cached.at() < VALUE_REFRESH_MS) return cached;
        double worth = ItemValue.INSTANCE.totalOf(items);
        Valued fresh = new Valued(
                worth > 0 ? ItemValue.INSTANCE.formatShort(worth) : null,
                worth > 0 ? ItemValue.INSTANCE.breakdown(items, pageName) : null,
                now);
        valueCache.put(pageName, fresh);
        return fresh;
    }

    /**
     * The open container's worth as a panel beside the pages. It sits outside
     * the grid on the side you picked, and is nudged back on screen when there
     * is no room for it there.
     */
    private static void drawValuePanel(GuiGraphicsExtractor ctx, List<Component> lines,
                                       double x, double w, double y, double pagesBottom, double screenWidth) {
        // Outside the grid on the side you picked; on the other side when that
        // one has no room, and not at all when neither has — the figure beside
        // the page name still tells you the total, so nothing is lost.
        boolean left = Cfg.valuePanelSide() == 1;
        double gridLeft = x - w + 3;
        double gridRight = x + 2 * w + 16;
        double px = left ? gridLeft - VALUE_PANEL_W - 6 : gridRight + 6;
        if (px < 2 || px + VALUE_PANEL_W > screenWidth - 2) {
            px = left ? gridRight + 6 : gridLeft - VALUE_PANEL_W - 6;
        }
        if (px < 2 || px + VALUE_PANEL_W > screenWidth - 2) return;
        double ph = Math.min(pagesBottom - y + 3, 10 + lines.size() * 10);

        Utils.drawNineSlice(ctx, DEMO_BACKGROUND, px, y - 3, VALUE_PANEL_W, ph,
                DEMO_PANEL_W, DEMO_PANEL_H, DEMO_BORDER, 256, 256, Utils.alphaTint(Cfg.alpha()));

        var font = Minecraft.getInstance().font;
        ctx.enableScissor((int) px, (int) (y - 3), (int) Math.ceil(px + VALUE_PANEL_W), (int) Math.ceil(y - 3 + ph));
        double ly = y + 2;
        for (Component line : lines) {
            if (ly + 9 > y - 3 + ph) break;
            ctx.text(font, line, (int) (px + 5), (int) ly, Utils.WHITE, true);
            ly += 10;
        }
        ctx.disableScissor();
    }

    private static boolean isClick(boolean lmbDown, boolean rmbDown) {
        return (lmbDown && shouldBeAbleToClick) || (rmbDown && shouldBeAbleToRightClick);
    }

    private static boolean isShiftDown() {
        return Utils.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT);
    }

    // -------- main draw --------

    private static void drawStorage(GuiGraphicsExtractor ctx, Session ses, double mx, double my,
                                    double screenWidth, double screenHeight) {
        DrawData items = ses.drawData;
        String name = ses.name;

        scroll = scroll + (scrollBuffer / 2);
        scrollBuffer /= 1.5;

        // reset hover flag each frame in our custom GUI
        hoveringStorageItem = false;

        int rows = (int) Math.floor(screenHeight / 130);
        double w = 175;
        double h = 108;
        double x = screenWidth / 2 - w / 2;
        double y = 18;

        // the inventory panel (82px) sits directly under the page rows; drop page rows until it
        // fits on screen instead of letting it run off the bottom edge
        while (rows > 1 && y + rows * h + INVENTORY_OFFSET + 82 > screenHeight) rows--;

        // --- clamp scroll so it can't go forever ---
        int totalPages = items.enderchest.size() + items.backpacks.size();
        int totalRows = (int) Math.ceil(totalPages / 3.0); // 3 columns per row
        double rawMinScroll = (rows - totalRows) * h - 18;
        double minScroll = Math.min(0, rawMinScroll);

        // a page you opened directly (/ec 3, a backpack) is scrolled into view, the
        // rest still around it; only if its row is not already on screen
        if (jumpToCurrent) {
            jumpToCurrent = false;
            int idx = -1, cur = -1;
            for (List<DrawPage> type : List.of(items.enderchest, items.backpacks))
                for (DrawPage page : type) { idx++; if (name.equals(page.name)) cur = idx; }
            if (cur >= 0) {
                int rowIndex = cur / 3;
                double rowTop = rowIndex * h + scroll, rowBottom = rowTop + h;
                if (rowTop < 0 || rowBottom > rows * h) {
                    scroll = -(rowIndex * h) + Math.max(0, (rows - 1) / 2) * h;   // its row in the middle when there is room
                    scrollBuffer = 0;
                }
            }
        }

        if (scroll > 0) {
            scroll = 0;
            if (scrollBuffer > 0) scrollBuffer = 0;
        }

        if (scroll < minScroll) {
            scroll = minScroll;
            if (scrollBuffer < 0) scrollBuffer = 0;
        }

        double pagesBottom = y + rows * h;
        double invY = pagesBottom + INVENTORY_OFFSET; // top of the main inventory block
        boolean lmbDown = Utils.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean rmbDown = Utils.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        // live state every frame: the carried item, the open container's slots and the inventory
        heldItem = Utils.getCarriedItem();
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu liveMenu = mc.player != null ? mc.player.containerMenu : null;
        items.inventory = liveInventory(mc);

        // grey background
        Utils.drawRect(ctx, Utils.color(0, 0, 0, 150), 0, 0, screenWidth, screenHeight);

        // search bar
        double ix = screenWidth / 2 + 90;
        double iy = invY;
        input.draw(ctx, ix, iy, 80, 10, mx, my);
        if (lmbDown) {
            input.isActive = mx > ix - 2 && mx < ix + 82 && my > iy - 2 && my < iy + 12;
        }

        // scrollbar beside the pages, draggable; only when there is more than fits
        if (minScroll < 0) {
            double trackX = x + 2 * w + 12, trackY = y, trackH = pagesBottom - y;
            double visible = rows * h, total = totalRows * h + 18;
            double thumbH = Math.max(16, trackH * visible / total);
            double frac = -scroll / -minScroll;
            double thumbY = trackY + (trackH - thumbH) * frac;
            boolean overThumb = mx >= trackX - 2 && mx < trackX + 6 && my >= thumbY && my < thumbY + thumbH;
            boolean overTrack = mx >= trackX - 2 && mx < trackX + 6 && my >= trackY && my < trackY + trackH;
            if (lmbDown) {
                if (!barDragging && overThumb) { barDragging = true; barDragOffset = my - thumbY; }
                else if (!barDragging && overTrack && !barPressedOutside) { barDragging = true; barDragOffset = thumbH / 2; }
                if (!barDragging) barPressedOutside = true;
            } else { barDragging = false; barPressedOutside = false; }
            if (barDragging) {
                double f = (my - barDragOffset - trackY) / (trackH - thumbH);
                scroll = -Math.max(0, Math.min(1, f)) * -minScroll;
                scrollBuffer = 0;
                frac = -scroll / -minScroll;
                thumbY = trackY + (trackH - thumbH) * frac;
            }
            Utils.drawRect(ctx, Utils.color(30, 24, 40, 200), trackX, trackY, 4, trackH);
            Utils.drawRect(ctx, (barDragging || overThumb) ? KNOB_ON_TOP : Utils.color(138, 64, 200), trackX, thumbY, 4, thumbH);
        }

        // enderchest and backpack pages
        boolean searching = !input.text.isEmpty();
        int tint = Utils.alphaTint(Cfg.alpha());
        int index = -1;
        for (List<DrawPage> type : List.of(items.enderchest, items.backpacks)) {
            for (DrawPage page : type) {
                index++;
                boolean isCurrentPage = name.equals(page.name);
                int rowIndex = index / 3;
                int colIndex = index % 3;
                double rX = x - w + colIndex * w + 8;
                double rY = y + rowIndex * h + scroll + 18;
                double pageHeight = page.size * 2;
                String pageLabel = (name.equals("Storage") || !isCurrentPage) ? page.name : "&e&l" + page.name;
                if (rY > y && rY < pagesBottom) {
                    Utils.drawString(ctx, pageLabel, rX, rY - 12, true);
                }
                if (rY + pageHeight < y || rY > pagesBottom) {
                    continue;
                }

                // everything inside the page is clipped to the pages area
                ctx.enableScissor(0, (int) y, (int) Math.ceil(screenWidth), (int) pagesBottom);

                // page background + slots
                Utils.drawNineSlice(ctx, DEMO_BACKGROUND, rX - 5, rY - 3, 172, page.size * 2 + 6,
                        DEMO_PANEL_W, DEMO_PANEL_H, DEMO_BORDER, 256, 256, tint);
                for (int i = 0; i < page.size; i++) {
                    Utils.drawSlot(ctx, SLOT_SPRITE, rX + (i % 9) * 18, rY + (i / 9) * 18, tint);
                }
                // draw items (highlighted while a search term is active); the open page is read
                // straight from the container so placing/removing items shows instantly
                List<ItemStack> pageItems = isCurrentPage && liveMenu != null
                        ? liveItems(liveMenu, searching) : page.items;
                for (int i = 0; i < pageItems.size(); i++) {
                    ItemStack item = pageItems.get(i);
                    int rIndex = i / 9;
                    int cIndex = i % 9;
                    double rrX = rX + cIndex * 18;
                    double rrY = rY + rIndex * 18;
                    if (rrY < y || rrY + 18 > pagesBottom) continue;
                    if (searching && item != null) {
                        Utils.drawRect(ctx, SEARCH_HIGHLIGHT, rrX, rrY, 16, 16);
                    }
                    // only active page items count as "active window"
                    drawItem(ctx, item, rrX, rrY, mx, my, null, isCurrentPage);
                }

                ctx.disableScissor();

                // check if mouse is over a page and check for click
                boolean mouseOverPage = mx > rX && mx < rX + 161 && my > rY && my < rY + Math.min(page.size * 2, pagesBottom - rY);

                // What the page is worth, beside its name: the one you have open,
                // and whichever one you point at. Hovering the figure breaks it down.
                if (Cfg.showValue() && (isCurrentPage || mouseOverPage) && rY > y && rY < pagesBottom) {
                    List<ItemStack> priced = isCurrentPage && liveMenu != null ? liveItems(liveMenu, false) : page.items;
                    Valued v = valueOf(page.name, priced);
                    String shown = v.text() != null ? "&a" + v.text()
                            : (ItemValue.INSTANCE.pricesReady() ? null : "&8...");
                    if (shown != null) {
                        double vx = rX + Utils.getStringWidth(pageLabel) + 5;
                        Utils.drawString(ctx, shown, vx, rY - 12, true);
                        if (v.lines() != null && mx >= vx && mx <= vx + Utils.getStringWidth(shown)
                                && my >= rY - 13 && my <= rY - 1) {
                            tooltips.add(new Tooltip(v.lines(), mx, my, "value:" + page.name));
                        }
                    }
                }
                if (mouseOverPage || isCurrentPage) {
                    // render outline
                    Utils.drawLine(ctx, Utils.YELLOW, rX - 2, Math.max(y, rY - 3), rX - 2, Math.min(pagesBottom, rY + page.size * 2 + 1), 2);
                    Utils.drawLine(ctx, Utils.YELLOW, rX + 162, Math.max(y, rY - 3), rX + 162, Math.min(pagesBottom, rY + page.size * 2 + 1), 2);
                    if (rY > y) Utils.drawLine(ctx, Utils.YELLOW, rX - 1, rY - 2, rX + 161, rY - 2, 2);
                    if (rY + page.size * 2 < pagesBottom) Utils.drawLine(ctx, Utils.YELLOW, rX - 1, rY + page.size * 2, rX + 161, rY + page.size * 2, 2);
                }
                if (!isCurrentPage && mouseOverPage && isClick(lmbDown, rmbDown)) {
                    // anti-spam: one page command per cooldown, and none while a page load is
                    // still pending (rapid /ec or /bp commands get you kicked from SkyBlock)
                    long now = System.currentTimeMillis();
                    if (keepMenuOpen && now - lastPageCommandMs > PENDING_PAGE_TIMEOUT_MS) {
                        keepMenuOpen = false; // the previous command never opened a GUI
                    }
                    if (!keepMenuOpen && now - lastPageCommandMs >= PAGE_COMMAND_COOLDOWN_MS) {
                        lastPageCommandMs = now;
                        keepMenuOpen = true;
                        Utils.command(page.command);
                        Utils.playButtonSound();
                    }
                    shouldBeAbleToClick = false;
                    shouldBeAbleToRightClick = false;
                }

                if (!mouseOverPage) {
                    continue;
                }

                int hX = (int) Math.floor((mx - rX) / 18);
                int hY = (int) Math.floor((my - rY) / 18);
                int hI = hY * 9 + hX;
                Utils.drawRect(ctx, Utils.color(255, 255, 255, 150), rX + hX * 18, rY + hY * 18, 16,
                        Math.min(16, pagesBottom - (rY + hY * 18) - 1));
                if (isCurrentPage && isClick(lmbDown, rmbDown)) {
                    int button = lmbDown ? GLFW.GLFW_MOUSE_BUTTON_LEFT : GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                    Utils.clickSlot(9 + hI, button, isShiftDown());
                    heldItem = Utils.getCarriedItem();
                    shouldBeAbleToClick = false;
                    shouldBeAbleToRightClick = false;
                    // mark that we need a refresh (debounced in step trigger)
                    updateDrawData();
                }
            }
        }

        // inventory (panel starts 6px above the first slot row so the border is visible, like a
        // normal inventory, and ends 82px below invY as in the original layout)

        // the open container's worth, beside the pages
        if (Cfg.valuePanelSide() > 0 && liveMenu != null && !name.equals("Storage")) {
            Valued open = valueOf(name, liveItems(liveMenu, false));
            if (open.lines() != null) {
                drawValuePanel(ctx, open.lines(), x, w, y, pagesBottom, screenWidth);
            }
        }

        Utils.drawNineSlice(ctx, DEMO_BACKGROUND, x, invY - 6, w + 1, 88,
                DEMO_PANEL_W, DEMO_PANEL_H, DEMO_BORDER, 256, 256, tint);
        for (int i = 0; i < 36; i++) {
            double sY = invY + (i / 9) * 18 + 1;
            if (i > 26) sY += 4;
            Utils.drawSlot(ctx, SLOT_SPRITE, x + (i % 9) * 18 + 8, sY, tint);
        }
        boolean inventoryItemHovered = false;
        for (int i = 0; i < items.inventory.size(); i++) {
            ItemStack item = items.inventory.get(i);
            int rIndex = i / 9;
            int cIndex = i % 9;
            double rrX = x + cIndex * 18 + 8;
            double rrY = invY + rIndex * 18 + 1;
            if (i > 26) {
                rrY += 4;
            }
            // inventory counts as "active window" only when not in Storage
            boolean invActive = !name.equals("Storage");
            drawItem(ctx, item, rrX, rrY, mx, my, null, invActive);
            if (my < invY) continue;
            if (!inventoryItemHovered && mx > rrX && mx < rrX + 18 && my > rrY && my < rrY + 18) {
                Utils.drawRect(ctx, Utils.color(255, 255, 255, 150), rrX, rrY, 16, 16);
                if (!name.equals("Storage") && isClick(lmbDown, rmbDown)) {
                    int button = lmbDown ? GLFW.GLFW_MOUSE_BUTTON_LEFT : GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                    Utils.clickSlot(containerSize() - 36 + i, button, isShiftDown());
                    heldItem = Utils.getCarriedItem();
                    shouldBeAbleToClick = false;
                    shouldBeAbleToRightClick = false;
                    // mark that we need a refresh (debounced)
                    updateDrawData();
                }
                inventoryItemHovered = true;
            }
        }

        if (heldItem != null) {
            ctx.nextStratum();
            // dragged item shouldn't block scroll, so isActiveArea=false
            drawItem(ctx, heldItem, mx - 8, my - 8, mx, my, null, false);
        }

        if (!lmbDown) shouldBeAbleToClick = true;
        if (!rmbDown) shouldBeAbleToRightClick = true;
    }

    /** Items of the open container's storage slots (9 .. size-36), search-filtered. */
    private static List<ItemStack> liveItems(AbstractContainerMenu menu, boolean searching) {
        int size = menu.slots.size();
        List<ItemStack> result = new ArrayList<>(Math.max(0, size - 45));
        String term = input.text.toLowerCase();
        for (int i = 9; i < size - 36; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack == null || stack.isEmpty()) {
                result.add(null);
            } else if (searching && !Utils.liveSearchText(stack).contains(term)) {
                result.add(null);
            } else {
                result.add(stack);
            }
        }
        return result;
    }

    /** inventory rows 1-3 (slots 9..35) followed by the hotbar (0..8), read live. */
    private static List<ItemStack> liveInventory(Minecraft mc) {
        List<ItemStack> inventory = new ArrayList<>(36);
        for (int i = 9; i < 36; i++) inventory.add(inventoryItem(mc, i));
        for (int i = 0; i < 9; i++) inventory.add(inventoryItem(mc, i));
        return inventory;
    }

    private static int containerSize() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null) return 0;
        return mc.player.containerMenu.slots.size();
    }

    /** Called from the render mixin instead of the vanilla container screen. */
    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Session ses = session;
        if (ses == null) return;

        double f = scaleFactor();
        double screenWidth = ctx.guiWidth() * f;
        double screenHeight = ctx.guiHeight() * f;

        ctx.pose().pushMatrix();
        ctx.pose().scale((float) (1 / f), (float) (1 / f));
        try {
            drawStorage(ctx, ses, mouseX * f, mouseY * f, screenWidth, screenHeight);
        } catch (Exception e) {
            FamilyStorage.LOGGER.error("drawStorage failed", e);
        }
        ctx.pose().popMatrix();

        // postGuiRender: tooltips on top of everything (scrollable while hovering the same item)
        if (heldItem == null && !tooltips.isEmpty()) {
            Tooltip tooltip = tooltips.get(0);
            if (!tooltip.key().equals(lastTooltipKey)) {
                tooltipScroll = 0;
                lastTooltipKey = tooltip.key();
            }
            Minecraft mc = Minecraft.getInstance();
            List<FormattedCharSequence> lines = new ArrayList<>(tooltip.lines().size());
            for (Component line : tooltip.lines()) lines.add(line.getVisualOrderText());
            // vanilla tooltip height: line heights + 2px after the title + 3px padding top/bottom
            int tooltipHeight = lines.size() * mc.font.lineHeight + (lines.size() > 1 ? 2 : 0) + 6;
            hoveredTooltipScrollable = tooltipHeight + 8 > ctx.guiHeight();
            ctx.setTooltipForNextFrame(mc.font, lines, SCROLLING_POSITIONER,
                    (int) Math.round(tooltip.x() / f), (int) Math.round(tooltip.y() / f), false);
        } else {
            tooltipScroll = 0;
            lastTooltipKey = null;
            hoveredTooltipScrollable = false;
        }
        tooltips.clear();
    }

    // -------- startDraw --------

    private static List<DrawPage> filter(TreeMap<Integer, StorageData.PageData> pages, String searchTerm, String currentName) {
        String term = (searchTerm == null ? "" : searchTerm).toLowerCase();
        List<DrawPage> result = new ArrayList<>();
        for (StorageData.PageData page : pages.values()) {
            if (page == null || page.items == null || page.items.isEmpty()) continue;
            boolean isCurrent = currentName != null && currentName.equals(page.name);
            DrawPage dp = new DrawPage();
            dp.name = page.name;
            dp.index = page.index;
            dp.size = page.size == null ? 0 : page.size;
            dp.command = page.command;
            boolean anyMatch = false;
            for (String item : page.items.values()) {
                if (item == null) {
                    dp.items.add(null);
                    continue;
                }
                if (!term.isEmpty() && !searchText(item).contains(term)) {
                    dp.items.add(null);
                    continue;
                }
                dp.items.add(getCachedItemFromNBT(item));
                anyMatch = true;
            }
            // while searching, pages without a matching item are hidden so the matches stack
            // from the top – except the page that is open right now, which must never vanish
            // from under the mouse (its stored contents may also still be stale)
            if (!term.isEmpty() && !anyMatch && !isCurrent) continue;
            result.add(dp);
        }
        return result;
    }

    /**
     * Lower-case text the search box matches against: the item's display name and lore lines
     * (not the raw item data, which is full of internal ids that would match things like "hyp").
     */
    private static String searchText(String nbt) {
        String cached = searchTextCache.get(nbt);
        if (cached != null) return cached;
        ItemStack stack = getCachedItemFromNBT(nbt);
        String text = Utils.liveSearchText(stack);
        searchTextCache.put(nbt, text);
        return text;
    }

    private static DrawData getDrawData(String currentName) {
        StorageData.ProfileData st = FamilyStorage.getStorage();
        DrawData data = new DrawData();
        data.enderchest = filter(st.enderchest, input.text, currentName);
        data.backpacks = filter(st.backpacks, input.text, currentName);
        data.inventory = liveInventory(Minecraft.getInstance());
        return data;
    }

    private static ItemStack inventoryItem(Minecraft mc, int slot) {
        if (mc.player == null) return null;
        ItemStack stack = mc.player.getInventory().getItem(slot);
        return stack == null || stack.isEmpty() ? null : stack;
    }

    // direct refresh function for when we want immediate update
    private static void refreshNow(Session ses) {
        ses.drawData = getDrawData(ses.name);
    }

    // debounced update used by drawStorage click handlers
    private static void updateDrawData() {
        needsRefresh = true;
    }

    private static boolean containsIndex(List<DrawPage> pages, int i) {
        for (DrawPage p : pages) if (p.index != null && p.index == i) return true;
        return false;
    }

    private static TreeMap<Integer, String> emptyPlaceholderRow() {
        TreeMap<Integer, String> items = new TreeMap<>();
        for (int i = 9; i <= 17; i++) items.put(i, null);
        return items;
    }

    private static void startDraw() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        String name = screen instanceof AbstractContainerScreen<?> acs ? acs.getTitle().getString() : "";
        Matcher match = STORAGE_TITLE.matcher(name);
        if (!match.matches()) {
            input.text = "";
            scroll = 0;
            return;
        }
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
        StorageData storageData = FamilyStorage.storage.data;

        // Switched off in the config: leave the vanilla screen alone.
        if (!Cfg.enabled()) return;

        // load saved scroll position for this profile (if any)
        if (storageData.currentProfile != null && storageData.profiles.containsKey(storageData.currentProfile)) {
            scroll = storageData.profiles.get(storageData.currentProfile).scroll;
        } else {
            scroll = 0;
        }

        heldItem = null;

        Session ses = new Session();
        ses.name = match.group(1);
        ses.drawData = getDrawData(ses.name);
        jumpToCurrent = true;

        // expose to the global guiOpened hook so when setItems() runs there,
        // the overlay knows it should refresh too
        activeUpdateDrawData = StorageOverlay::updateDrawData;

        if (ses.name.equals("Storage") && !keepMenuOpen) {
            AbstractContainerMenu menu = container.getMenu();
            StorageData.ProfileData st = FamilyStorage.getStorage();
            for (int index = 9; index < 18; index++) {
                String slotName = index < menu.slots.size() ? Utils.removeFormatting(Utils.getName(menu.getSlot(index).getItem())) : null;
                if (slotName == null || !slotName.startsWith("Locked")) {
                    int i = index - 9;
                    if (!containsIndex(ses.drawData.enderchest, i)) {
                        DrawPage dp = new DrawPage();
                        dp.name = "&cClick to load page " + (i + 1);
                        dp.size = 45;
                        dp.command = "ec " + (i + 1);
                        ses.drawData.enderchest.add(dp);
                        StorageData.PageData page = st.enderchest.get(i + 1);
                        if (page == null) {
                            page = new StorageData.PageData();
                            page.command = "ec " + (i + 1);
                            st.enderchest.put(i + 1, page);
                        }
                        page.name = "&cClick to load page " + (i + 1);
                        page.size = 9;
                        page.items = emptyPlaceholderRow();
                    }
                }
            }
            for (int index = 27; index < 45; index++) {
                String slotName = index < menu.slots.size() ? Utils.removeFormatting(Utils.getName(menu.getSlot(index).getItem())) : null;
                if (slotName != null && slotName.startsWith("Backpack")) {
                    int i = index - 27;
                    if (!containsIndex(ses.drawData.backpacks, i)) {
                        DrawPage dp = new DrawPage();
                        dp.name = "&cClick to load backpack " + (i + 1);
                        dp.size = 45;
                        dp.command = "bp " + (i + 1);
                        ses.drawData.backpacks.add(dp);
                        StorageData.PageData page = st.backpacks.get(i + 1);
                        if (page == null) {
                            page = new StorageData.PageData();
                            page.command = "bp " + (i + 1);
                            st.backpacks.put(i + 1, page);
                        }
                        page.name = "&cClick to load backpack " + (i + 1);
                        page.size = 9;
                        page.items = emptyPlaceholderRow();
                    }
                }
            }
            FamilyStorage.storage.save();
            // immediate update after structural change
            refreshNow(ses);
        }

        input.clearGuiKeyListeners();
        input.onGuiKey(text -> {
            StorageData.ProfileData st = FamilyStorage.getStorage();
            ses.drawData.enderchest = filter(st.enderchest, text, ses.name);
            ses.drawData.backpacks = filter(st.backpacks, text, ses.name);
            // jump back to the top so the (re)ordered results are in view
            scroll = 0;
            scrollBuffer = 0;
        });

        shouldBeAbleToClick = true;
        session = ses;
    }

    // -------- triggers --------

    /**
     * "step" trigger at 3 fps: runs the debounced refresh of the saved pages. The open page, the
     * inventory and the held item are read live every frame, and the open page is persisted when
     * the GUI closes, so no disk write happens here.
     */
    public static void tick() {
        Session ses = session;
        if (ses == null) return;
        ses.stepCounter++;
        if (ses.stepCounter % 7 != 0) return;

        if (needsRefresh) {
            refreshNow(ses);
            needsRefresh = false;
        }
    }

    /**
     * "scrolled" trigger. While hovering an item of the active window (the open page, or the
     * inventory outside the Storage menu) the wheel belongs to that item: it scrolls the tooltip
     * when the tooltip is taller than the screen and never scrolls the pages (the JS module's
     * {@code if (hoveringStorageItem) return;}). A too-tall tooltip on any other item is scrolled
     * as well. Otherwise the wheel scrolls the pages.
     */
    public static void onScrolled(double vertical) {
        if (session == null) return;
        if (vertical == 0) return;
        double direction = vertical > 0 ? 1 : -1;
        if (hoveringStorageItem || hoveredTooltipScrollable) {
            // the positioner clamps the offset, so a tooltip that fits on screen simply stays put
            tooltipScroll += (int) (direction * TOOLTIP_SCROLL_STEP);
            return;
        }
        scrollBuffer += direction * 27;
    }

    /** "guiClosed" trigger (fired for the previous screen before the new one is shown). */
    public static void onGuiClosed(Screen closed) {
        input.onGuiClosed();

        // persist the page that is being closed (the old screen is still the current one here).
        // Always, overlay or not: the open-time snapshots skip a page that looks empty, so
        // an empty page only ever gets recorded here, and a page opened without the overlay
        // used to be skipped entirely and never show up.
        if (FamilyStorage.active) FamilyStorage.setItems();

        Session ses = session;
        if (ses == null) return;

        if (keepMenuOpen) {
            // page switch in progress: keep the session (and its scroll position) alive
            rememberScroll(false);
            Scheduler.scheduleTask(1, () -> {
                Session current = session;
                if (current != null && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> acs) {
                    current.name = acs.getTitle().getString();
                    jumpToCurrent = true;
                }
            });
            return;
        }

        endSession();
    }

    /** Stores the scroll position in the current profile (optionally writing the file). */
    private static void rememberScroll(boolean save) {
        StorageData storageData = FamilyStorage.storage.data;
        if (storageData.currentProfile != null && storageData.profiles.containsKey(storageData.currentProfile)) {
            storageData.profiles.get(storageData.currentProfile).scroll = scroll;
            if (save) FamilyStorage.storage.save();
        }
    }

    /** Equivalent of the guiClosed unregister block: the overlay is fully closed. */
    private static void endSession() {
        // save scroll position for this profile when GUI fully closes
        rememberScroll(true);
        activeUpdateDrawData = null;
        session = null;
    }

    /** "guiOpened" trigger (fired after the new screen, possibly null, is set). */
    public static void onGuiOpened(Screen opened) {
        if (!FamilyStorage.active) return;
        // keeps pages in sync even when our overlay is closed, and tells the overlay to refresh
        // if it's open. Two snapshots (2 and 20 ticks) because the contents can arrive after the
        // screen opens; a page that still looks empty is skipped, the close snapshot is final.
        Runnable snapshot = () -> {
            FamilyStorage.setItems(true);
            Runnable update = activeUpdateDrawData;
            if (update != null) {
                update.run();
            }
        };
        Scheduler.scheduleTask(2, snapshot);
        Scheduler.scheduleTask(20, snapshot);

        if (keepMenuOpen) {
            if (opened == null) {
                // the server closed the old container before opening the requested page; keep
                // waiting for the new screen, but give up (and close the overlay) if none comes
                Scheduler.scheduleTask(40, () -> {
                    if (keepMenuOpen && Minecraft.getInstance().gui.screen() == null) {
                        keepMenuOpen = false;
                        if (session != null) endSession();
                    }
                });
                return;
            }
            keepMenuOpen = false;
            Session current = session;
            if (current == null) {
                Scheduler.scheduleTask(1, StorageOverlay::startDraw);
            } else if (opened instanceof AbstractContainerScreen<?> acs) {
                // the requested page is now the active one
                current.name = acs.getTitle().getString();
                shouldBeAbleToClick = false;
                shouldBeAbleToRightClick = false;
            }
            return;
        }
        Scheduler.scheduleTask(1, StorageOverlay::startDraw);
    }
}
