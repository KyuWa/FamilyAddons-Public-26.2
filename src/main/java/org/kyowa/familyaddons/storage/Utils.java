package org.kyowa.familyaddons.storage;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Port of utils.js plus the ChatTriggers Renderer/ChatLib helpers the module used. */
public final class Utils {

    private Utils() {}

    // ChatTriggers Renderer colour constants (Minecraft chat colours)
    public static final int GREEN = color(85, 255, 85, 255);
    public static final int RED = color(255, 85, 85, 255);
    public static final int YELLOW = color(255, 255, 85, 255);
    public static final int WHITE = color(255, 255, 255, 255);

    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern AMP_COLOR = Pattern.compile("(?<!\\\\)&(?=[0-9a-fk-or])");

    public static int color(int r, int g, int b, int a) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int color(int r, int g, int b) {
        return color(r, g, b, 255);
    }

    /** Linear blend of two ARGB colours, t in [0, 1]. */
    public static int lerpColor(int from, int to, double t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return color(r, g, b, a);
    }

    /** ChatLib.removeFormatting */
    public static String removeFormatting(String s) {
        if (s == null) return null;
        return FORMATTING.matcher(s).replaceAll("");
    }

    /** ChatLib.addColor */
    public static String addColor(String s) {
        if (s == null) return null;
        return AMP_COLOR.matcher(s).replaceAll("§");
    }

    /** ChatLib.chat */
    public static void chat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            FamilyStorage.LOGGER.info(removeFormatting(addColor(message)));
            return;
        }
        mc.player.sendSystemMessage(Component.literal(addColor(message)));
    }

    /** ChatLib.command (no leading slash) */
    public static void command(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.sendCommand(command);
    }

    /** World.playSound('gui.button.press', 1, 1) */
    public static void playButtonSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F));
    }

    /** org.lwjgl.input.Mouse.isButtonDown */
    public static boolean isMouseDown(int button) {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetMouseButton(handle, button) == GLFW.GLFW_PRESS;
    }

    /** Keyboard.isKeyDown */
    public static boolean isKeyDown(int key) {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }

    /** ClickAction(slot, windowId).setClickString(click).setHoldingShift(shift).complete() */
    public static void clickSlot(int slot, int button, boolean shift) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(
                mc.player.containerMenu.containerId, slot, button,
                shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, mc.player);
    }

    /** Player.getPlayer().inventory.getItemStack() (the carried item); null when empty. */
    public static ItemStack getCarriedItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null) return null;
        ItemStack carried = mc.player.containerMenu.getCarried();
        return carried == null || carried.isEmpty() ? null : carried;
    }

    // ---------------- NBT <-> ItemStack ----------------

    private static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) return mc.getConnection().registryAccess();
        if (mc.level != null) return mc.level.registryAccess();
        return null;
    }

    /** item.getRawNBT() – the item serialised to an SNBT string. */
    public static String getRawNBT(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        HolderLookup.Provider regs = registries();
        if (regs == null) return null;
        try {
            return ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, regs), stack)
                    .result().map(Tag::toString).orElse(null);
        } catch (Exception e) {
            FamilyStorage.LOGGER.warn("Failed to serialise item", e);
            return null;
        }
    }

    /** Utils.getItemFromNBT(nbtStr) */
    public static ItemStack getItemFromNBT(String nbtStr) {
        if (nbtStr == null) return null;
        HolderLookup.Provider regs = registries();
        if (regs == null) return null;
        try {
            CompoundTag tag = TagParser.parseCompoundFully(nbtStr);
            return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, regs), tag)
                    .result().orElse(null);
        } catch (Exception e) {
            FamilyStorage.LOGGER.warn("Failed to parse item NBT", e);
            return null;
        }
    }

    /** CT Item.getLore(): the full tooltip of the item. */
    public static List<Component> getLore(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        Item.TooltipContext ctx = mc.level != null ? Item.TooltipContext.of(mc.level) : Item.TooltipContext.EMPTY;
        return stack.getTooltipLines(ctx, mc.player, TooltipFlag.NORMAL);
    }

    /** Cheap lower-case search text for a live stack: display name + lore lines. */
    public static String liveSearchText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(removeFormatting(stack.getHoverName().getString())).append('\n');
        net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) sb.append(removeFormatting(line.getString())).append('\n');
        }
        return sb.toString().toLowerCase();
    }

    /** CT Item.getName(): the display name with formatting. */
    public static String getName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getHoverName().getString();
    }

    // ---------------- drawing ----------------

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Renderer.drawString – supports & colour codes and newlines. */
    public static void drawString(GuiGraphicsExtractor ctx, String text, double x, double y, boolean shadow) {
        if (text == null) return;
        Font font = font();
        int yy = (int) Math.floor(y);
        for (String line : addColor(text).split("\n", -1)) {
            ctx.text(font, line, (int) Math.floor(x), yy, WHITE, shadow);
            yy += font.lineHeight;
        }
    }

    /** Renderer.getStringWidth */
    public static int getStringWidth(String text) {
        return font().width(addColor(text));
    }

    /** Renderer.drawRect(color, x, y, w, h) */
    public static void drawRect(GuiGraphicsExtractor ctx, int color, double x, double y, double w, double h) {
        if (w <= 0 || h <= 0) return;
        int x1 = (int) Math.floor(x), y1 = (int) Math.floor(y);
        int x2 = (int) Math.floor(x + w), y2 = (int) Math.floor(y + h);
        if (x2 <= x1 || y2 <= y1) return;
        ctx.fill(x1, y1, x2, y2, color);
    }

    /** Renderer.drawLine for the axis-aligned lines the module draws. */
    public static void drawLine(GuiGraphicsExtractor ctx, int color, double x1, double y1, double x2, double y2, double thickness) {
        double half = thickness / 2.0;
        if (x1 == x2) {
            double top = Math.min(y1, y2), bottom = Math.max(y1, y2);
            if (bottom <= top) return;
            drawRect(ctx, color, x1 - half, top, thickness, bottom - top);
        } else if (y1 == y2) {
            double left = Math.min(x1, x2), right = Math.max(x1, x2);
            if (right <= left) return;
            drawRect(ctx, color, left, y1 - half, right - left, thickness);
        }
    }

    /** Renderer.drawCircle(color, x, y, radius, steps) – filled circle. */
    public static void drawCircle(GuiGraphicsExtractor ctx, int color, double cx, double cy, double radius) {
        int r = (int) Math.ceil(radius);
        for (int dy = -r; dy < r; dy++) {
            double yMid = dy + 0.5;
            double hw = Math.sqrt(Math.max(0, radius * radius - yMid * yMid));
            if (hw <= 0) continue;
            int x1 = (int) Math.round(cx - hw), x2 = (int) Math.round(cx + hw);
            int y = (int) Math.floor(cy) + dy;
            if (x2 > x1) ctx.fill(x1, y, x2, y + 1, color);
        }
    }

    /**
     * Utils.draw2dResourceLocation – draws the region [uMin,uMax]x[vMin,vMax] (in texels of a
     * 256x256 texture) of {@code texture} into the rectangle (x, y, w, h).
     */
    public static void draw2dResourceLocation(GuiGraphicsExtractor ctx, Identifier texture,
                                              double x, double y, double w, double h,
                                              double uMin, double uMax, double vMin, double vMax) {
        int iw = (int) Math.round(w), ih = (int) Math.round(h);
        if (iw <= 0 || ih <= 0) return;
        int uw = (int) Math.round(uMax - uMin), vh = (int) Math.round(vMax - vMin);
        if (uw <= 0 || vh <= 0) return;
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        ctx.blit(pipeline, texture, (int) Math.floor(x), (int) Math.floor(y),
                (float) uMin, (float) vMin, iw, ih, uw, vh, 256, 256);
    }

    /** ARGB white with the given opacity in percent – used to tint textures translucent. */
    public static int alphaTint(int percent) {
        int a = Math.max(0, Math.min(255, Math.round(Math.max(0, Math.min(100, percent)) * 255f / 100f)));
        return (a << 24) | 0xFFFFFF;
    }

    /** Draws the texel region (u, v, uW x vH) of a texW x texH texture into (x, y, w x h), tinted. */
    public static void blitRegion(GuiGraphicsExtractor ctx, Identifier texture,
                                  double x, double y, double w, double h,
                                  double u, double v, double uW, double vH, int texW, int texH, int tint) {
        int iw = (int) Math.round(w), ih = (int) Math.round(h);
        int iuw = (int) Math.round(uW), ivh = (int) Math.round(vH);
        if (iw <= 0 || ih <= 0 || iuw <= 0 || ivh <= 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, (int) Math.floor(x), (int) Math.floor(y),
                (float) u, (float) v, iw, ih, iuw, ivh, texW, texH, tint);
    }

    /**
     * Nine-slice draws the top-left {@code panelW x panelH} panel of {@code texture} (a
     * {@code texW x texH} image) into (x, y, w x h), keeping {@code border} pixels of the edges
     * unscaled – used for demo_background.png.
     */
    public static void drawNineSlice(GuiGraphicsExtractor ctx, Identifier texture,
                                     double x, double y, double w, double h,
                                     int panelW, int panelH, int border, int texW, int texH, int tint) {
        int b = border;
        double iw = w - 2 * b, ih = h - 2 * b;
        int cw = panelW - 2 * b, ch = panelH - 2 * b;
        // corners
        blitRegion(ctx, texture, x, y, b, b, 0, 0, b, b, texW, texH, tint);
        blitRegion(ctx, texture, x + w - b, y, b, b, panelW - b, 0, b, b, texW, texH, tint);
        blitRegion(ctx, texture, x, y + h - b, b, b, 0, panelH - b, b, b, texW, texH, tint);
        blitRegion(ctx, texture, x + w - b, y + h - b, b, b, panelW - b, panelH - b, b, b, texW, texH, tint);
        // edges
        blitRegion(ctx, texture, x + b, y, iw, b, b, 0, cw, b, texW, texH, tint);
        blitRegion(ctx, texture, x + b, y + h - b, iw, b, b, panelH - b, cw, b, texW, texH, tint);
        blitRegion(ctx, texture, x, y + b, b, ih, 0, b, b, ch, texW, texH, tint);
        blitRegion(ctx, texture, x + w - b, y + b, b, ih, panelW - b, b, b, ch, texW, texH, tint);
        // centre
        blitRegion(ctx, texture, x + b, y + b, iw, ih, b, b, cw, ch, texW, texH, tint);
    }

    /** Draws the vanilla 18x18 slot sprite so that a 16x16 item at (x, y) sits inside it. */
    public static void drawSlot(GuiGraphicsExtractor ctx, Identifier sprite, double x, double y, int tint) {
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, (int) Math.floor(x) - 1, (int) Math.floor(y) - 1, 18, 18, tint);
    }

    /** Utils.drawStackSize(item, x, y, size) */
    public static void drawStackSize(GuiGraphicsExtractor ctx, ItemStack item, double x, double y, Long size) {
        long stackSize = (size != null && size != 0) ? size : item.getCount();
        if (stackSize <= 1) return;
        String text = stackSize > 999 ? formatNumber(stackSize, 0) : Long.toString(stackSize);
        drawString(ctx, text, x + (3 - text.length()) * 5 + 1, y + 9, true);
    }

    // ---------------- number formatting ----------------

    private static String getSuffix(double num) {
        num = Math.abs(num);
        if (num >= 1_000_000_000_000d) return "T";
        else if (num >= 1_000_000_000d) return "B";
        else if (num >= 1_000_000d) return "M";
        else if (num >= 1_000d) return "k";
        return "";
    }

    private static double suffixValue(String suffix) {
        switch (suffix) {
            case "T": return 1_000_000_000_000d;
            case "B": return 1_000_000_000d;
            case "M": return 1_000_000d;
            case "k": return 1_000d;
            default: return 1;
        }
    }

    /** Utils.formatNumber(num, decimals) */
    public static String formatNumber(double num, int decimals) {
        if (num == 0 || Double.isNaN(num)) return "0";
        if (num < 1000 && num > -1000) return Long.toString((long) Math.floor(num));
        String suffix = getSuffix(num);
        num /= suffixValue(suffix);
        String fixed = String.format(Locale.US, "%." + decimals + "f", num);
        if (fixed.endsWith("0".repeat(decimals))) {
            return (long) Math.floor(num) + suffix;
        }
        return fixed + suffix;
    }

    /** Number.prototype.toLocaleString() */
    public static String toLocaleString(long num) {
        return String.format(Locale.US, "%,d", num);
    }
}
