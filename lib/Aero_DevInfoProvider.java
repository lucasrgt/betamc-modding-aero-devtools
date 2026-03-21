package aero.devtools;

/**
 * Interface for mods to provide debug info to the F10 overlay.
 * Implement and register via Aero_DevOverlay.register().
 *
 * Example:
 *   Aero_DevOverlay.register(new Aero_DevInfoProvider() {
 *       public String getLabel() { return "BE"; }
 *       public String[] getInfoLines() {
 *           return new String[] { "Nodes: " + nodeCount, "Storage: " + used + "/" + total };
 *       }
 *   });
 */
public interface Aero_DevInfoProvider {

    /** Short mod identifier shown as section header (e.g. "BE", "RTN", "IC2HM") */
    String getLabel();

    /** Lines to display under this mod's section. Called every frame when overlay is visible. */
    String[] getInfoLines();
}
