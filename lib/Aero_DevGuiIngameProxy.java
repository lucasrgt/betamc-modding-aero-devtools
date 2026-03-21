package aero.devtools;

import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiIngame;

/**
 * Proxy for GuiIngame that renders the dev overlay after the normal HUD.
 * This ensures the overlay renders in the correct GL context (during frame render,
 * not during game tick) which prevents flickering.
 *
 * Installed by Aero_DevBootstrap.init() — replaces mc.ingameGUI with this proxy.
 */
public class Aero_DevGuiIngameProxy extends GuiIngame {

    private Minecraft mc;

    public Aero_DevGuiIngameProxy(Minecraft mc) {
        super(mc);
        this.mc = mc;
    }

    @Override
    public void renderGameOverlay(float partialTick, boolean hasScreen, int mouseX, int mouseY) {
        // Render normal HUD first
        super.renderGameOverlay(partialTick, hasScreen, mouseX, mouseY);

        // Then render dev overlay on top (correct GL context, no flicker)
        Aero_DevOverlay.render(mc);
    }
}
