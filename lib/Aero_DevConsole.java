package aero.devtools;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

/**
 * Dev console — orchestrates all dev tools via keyboard.
 * F4 = refresh IDE connection, F6 = hot swap, F7 = hot restart,
 * F9 = reload textures, F10 = toggle toolbar.
 */
public class Aero_DevConsole {

    private static boolean f6Pressed = false;
    private static boolean f7Pressed = false;
    private static boolean f9Pressed = false;
    private static boolean f10Pressed = false;
    private static boolean proxyInstalled = false;

    private static long lastTickNanos = 0;
    private static final long FRAME_THRESHOLD_NS = 5000000;

    public static void onTick(Minecraft mc) {
        if (mc.currentScreen != null) return;

        long now = System.nanoTime();
        if (now - lastTickNanos < FRAME_THRESHOLD_NS) return;
        lastTickNanos = now;

        Aero_DevOverlay.recordTick();

        // F6 — Hot Swap
        boolean f6Down = Keyboard.isKeyDown(Keyboard.KEY_F6);
        if (f6Down && !f6Pressed) {
            Aero_DevOverlay.doSwap(mc);
        }
        f6Pressed = f6Down;

        // F7 — Hot Restart
        boolean f7Down = Keyboard.isKeyDown(Keyboard.KEY_F7);
        if (f7Down && !f7Pressed) {
            Aero_DevOverlay.doRestart(mc);
        }
        f7Pressed = f7Down;

        // F9 — Reload textures
        boolean f9Down = Keyboard.isKeyDown(Keyboard.KEY_F9);
        if (f9Down && !f9Pressed) {
            int count = Aero_DevTextureReload.reloadAll(mc);
            Aero_DevOverlay.setStatus(count + " textures reloaded!", 0xFF4EC9B0);
        }
        f9Pressed = f9Down;

        // F10 — Toggle toolbar
        boolean f10Down = Keyboard.isKeyDown(Keyboard.KEY_F10);
        if (f10Down && !f10Pressed) {
            Aero_DevOverlay.toggle();
        }
        f10Pressed = f10Down;

        // Install GUI proxy
        if (!proxyInstalled && mc.ingameGUI != null) {
            if (!(mc.ingameGUI instanceof Aero_DevGuiIngameProxy)) {
                mc.ingameGUI = new Aero_DevGuiIngameProxy(mc);
            }
            proxyInstalled = true;
        }
    }
}
