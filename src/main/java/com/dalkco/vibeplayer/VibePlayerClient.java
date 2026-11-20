package com.dalkco.vibeplayer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class VibePlayerClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("vibeplayer");

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("vibeplayer", "vibeplayer"));

    private static final int BTN_W = 18;
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 2;

    private static KeyMapping toggleKey;
    private static KeyMapping nextKey;
    private static KeyMapping prevKey;
    private static KeyMapping openKey;
    /** screen to open on the next tick (after chat has closed) */
    private static Screen pending;

    @Override
    public void onInitializeClient() {
        VibeConfig.load();
        MediaBridge.get().start();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MediaBridge.get().stop());

        toggleKey = key("toggle");
        nextKey = key("next");
        prevKey = key("prev");
        openKey = key("open");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CoverArt.tick(client);
            MediaBridge bridge = MediaBridge.get();
            while (toggleKey.consumeClick()) {
                bridge.togglePlay();
            }
            while (nextKey.consumeClick()) {
                bridge.next();
            }
            while (prevKey.consumeClick()) {
                bridge.previous();
            }
            while (openKey.consumeClick()) {
                pending = new PlayerScreen(null);
            }
            if (pending != null && client.gui.screen() == null) {
                Screen s = pending;
                pending = null;
                client.gui.setScreen(s);
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("vibeplayer", "now_playing"), PlayerHud::renderHud);
        registerChatControls();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("vibe")
                        .executes(ctx -> open(new PlayerScreen(null)))
                        .then(ClientCommands.literal("config").executes(ctx -> open(new VibeConfigScreen(null))))
                        .then(ClientCommands.literal("toggle").executes(ctx -> run(MediaBridge.get()::togglePlay)))
                        .then(ClientCommands.literal("play").executes(ctx -> run(MediaBridge.get()::play)))
                        .then(ClientCommands.literal("pause").executes(ctx -> run(MediaBridge.get()::pause)))
                        .then(ClientCommands.literal("next").executes(ctx -> run(MediaBridge.get()::next)))
                        .then(ClientCommands.literal("prev").executes(ctx -> run(MediaBridge.get()::previous)))));
    }

    private static KeyMapping key(String name) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vibeplayer." + name,
                InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
    }

    private static int open(Screen screen) {
        pending = screen;
        return 1;
    }

    private static int run(Runnable action) {
        action.run();
        return 1;
    }

    /** While chat is open: the card is drawn on top, with ⏮ ⏯ ⏭ ☰ ⚙ next to it; click the bar to seek, drag to move. */
    private static void registerChatControls() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) {
                return;
            }
            MediaBridge bridge = MediaBridge.get();
            List<IconButton> buttons = List.of(
                    new IconButton(0, 0, BTN_W, BTN_H, Component.translatable("vibeplayer.control.prev"),
                            Draw::prev, bridge::previous, false),
                    new IconButton(0, 0, BTN_W, BTN_H, Component.translatable("vibeplayer.control.toggle"),
                            (g, x, y, bw, bh, color) -> {
                                if (MediaBridge.get().state().playing()) {
                                    Draw.pause(g, x, y, bw, bh, color);
                                } else {
                                    Draw.play(g, x, y, bw, bh, color);
                                }
                            }, bridge::togglePlay, false),
                    new IconButton(0, 0, BTN_W, BTN_H, Component.translatable("vibeplayer.control.next"),
                            Draw::next, bridge::next, false),
                    new IconButton(0, 0, BTN_W, BTN_H, Component.translatable("vibeplayer.control.player"),
                            (g, x, y, bw, bh, color) -> Draw.glyph(g, client.font, "☰", x, y, bw, bh, color),
                            () -> Minecraft.getInstance().gui.setScreen(new PlayerScreen(null)), false),
                    new IconButton(0, 0, BTN_W, BTN_H, Component.translatable("vibeplayer.control.settings"),
                            (g, x, y, bw, bh, color) -> Draw.glyph(g, client.font, "⚙", x, y, bw, bh, color),
                            () -> Minecraft.getInstance().gui.setScreen(new VibeConfigScreen(null)), false));
            for (IconButton b : buttons) {
                b.visible = false;
                Screens.getWidgets(screen).add(b);
            }
            ScreenEvents.beforeExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
                PlayerHud.Layout l = PlayerHud.layout();
                MediaState st = MediaBridge.get().state();
                for (IconButton b : buttons) {
                    b.visible = l != null;
                }
                if (l == null) {
                    return;
                }
                buttons.get(0).active = st.active() && st.canPrev();
                buttons.get(1).active = st.active();
                buttons.get(2).active = st.active() && st.canNext();
                int rowW = buttons.size() * (BTN_W + BTN_GAP) - BTN_GAP;
                PlayerHud.Bounds card = l.card();
                int x = Math.max(2, Math.min(s.width - rowW - 2, (card.x1() + card.x2()) / 2 - rowW / 2));
                boolean below = (card.y1() + card.y2()) / 2 < s.height / 2;
                int y = below ? card.y2() + 3 : card.y1() - 3 - BTN_H;
                for (IconButton b : buttons) {
                    b.setX(x);
                    b.setY(y);
                    x += BTN_W + BTN_GAP;
                }
            });
            ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) ->
                    PlayerHud.renderOverChat(g, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                    !(event.button() == 0 && PlayerHud.click(event.x(), event.y())));
            ScreenEvents.remove(screen).register(s -> PlayerHud.stopDrag());
        });
    }
}
