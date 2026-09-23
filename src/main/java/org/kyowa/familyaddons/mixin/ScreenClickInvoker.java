package org.kyowa.familyaddons.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the handler the chat screen itself runs when a chat component is clicked,
 * so a click event lifted off a message (Hideyho's [Sure]) fires exactly as clicking
 * the word would, whatever kind of event the server attached.
 */
@Mixin(Screen.class)
public interface ScreenClickInvoker {

    @Invoker("defaultHandleGameClickEvent")
    static void familyaddons$handleGameClick(ClickEvent event, Minecraft minecraft, Screen screen) {
        throw new AssertionError();
    }
}
