package aero.devtools;

import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;
import net.minecraft.src.TextureFX;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared texture hot-reload system. Generalizes the F9 mechanism from BE.
 *
 * Usage in mod constructor:
 *   Aero_DevTextureReload.registerMod("betaenergistics");
 *   int idx = Aero_DevTextureReload.override("betaenergistics", "/terrain.png", "/blocks/be_cable.png");
 *   blockCable.blockIndexInTexture = idx;
 *
 * Or keep ModLoader.addOverride and just track:
 *   int idx = ModLoader.addOverride("/terrain.png", "/blocks/be_cable.png");
 *   Aero_DevTextureReload.track("betaenergistics", "/terrain.png", "/blocks/be_cable.png", idx);
 */
public class Aero_DevTextureReload {

    // modId -> asset source directory relative to mod base
    private static Map<String, String> assetSources = new HashMap<String, String>();

    // overlayPath -> [textureIndex, atlasId, modIdIndex]
    private static Map<String, int[]> trackedTextures = new HashMap<String, int[]>();

    // ordered list of registered mod IDs
    private static ArrayList<String> modIds = new ArrayList<String>();

    // Cached reflection constructor for ModTextureStatic(int, int, BufferedImage)
    private static Constructor<?> modTextureStaticCtor;
    static {
        // ModTextureStatic lives in default package in the obfuscated jar,
        // but in net.minecraft.src in the MCP dev environment
        String[] candidates = { "net.minecraft.src.ModTextureStatic", "ModTextureStatic" };
        for (String name : candidates) {
            try {
                Class<?> cls = Class.forName(name);
                modTextureStaticCtor = cls.getConstructor(int.class, int.class, BufferedImage.class);
                break;
            } catch (Exception ignored) {}
        }
    }

    /**
     * Register a mod's asset source directory.
     * @param modId unique mod identifier matching src directory name (e.g. "betaenergistics")
     * @param assetPath relative path from mod base to assets dir
     */
    public static void registerMod(String modId, String assetPath) {
        // Always track — overlay and F9 work in all modes
        if (!modIds.contains(modId)) {
            modIds.add(modId);
        }
        assetSources.put(modId, assetPath);
    }

    /**
     * Register with default asset path convention: src/{modId}/assets
     */
    public static void registerMod(String modId) {
        registerMod(modId, "src/" + modId + "/assets");
    }

    /**
     * Register a texture override AND track it for hot reload.
     * Wraps ModLoader.addOverride + track in one call.
     */
    public static int override(String modId, String atlasPath, String overlayPath) {
        int idx = ModLoader.addOverride(atlasPath, overlayPath);
        track(modId, atlasPath, overlayPath, idx);
        return idx;
    }

    /**
     * Track an existing texture override for hot reload.
     * Use when you already called ModLoader.addOverride() yourself.
     */
    public static void track(String modId, String atlasPath, String overlayPath, int index) {
        // Always track — overlay and F9 work in all modes
        int atlasId = atlasPath.equals("/terrain.png") ? 0 : 1;
        int modIndex = modIds.indexOf(modId);
        if (modIndex < 0) {
            modIds.add(modId);
            modIndex = modIds.size() - 1;
        }
        trackedTextures.put(overlayPath, new int[]{index, atlasId, modIndex});
    }

    /**
     * Reload all tracked textures from disk. Called by Aero_DevConsole on F9.
     * @return number of textures successfully reloaded
     */
    public static int reloadAll(Minecraft mc) {
        File modBase = Aero_DevBootstrap.resolveModBase();
        int count = 0;

        for (Map.Entry<String, int[]> entry : trackedTextures.entrySet()) {
            String overlayPath = entry.getKey();
            int[] info = entry.getValue();
            int texIndex = info[0];
            int atlasId = info[1];
            int modIndex = info[2];

            String modId = modIds.get(modIndex);
            String assetDir = assetSources.get(modId);
            if (assetDir == null) continue;

            File assetFile = new File(modBase, assetDir + overlayPath);
            if (!assetFile.exists()) continue;

            try {
                BufferedImage img = javax.imageio.ImageIO.read(assetFile);
                if (img != null && modTextureStaticCtor != null) {
                    TextureFX tex = (TextureFX) modTextureStaticCtor.newInstance(texIndex, atlasId, img);
                    mc.renderEngine.registerTextureFX(tex);
                    count++;
                }
            } catch (Exception e) {
                System.err.println("[DevTools] Failed to reload " + overlayPath + ": " + e.getMessage());
            }
        }

        return count;
    }

    /** Get total number of tracked textures across all mods. */
    public static int getTrackedCount() {
        return trackedTextures.size();
    }

    /** Get number of registered mods. */
    public static int getModCount() {
        return modIds.size();
    }
}
