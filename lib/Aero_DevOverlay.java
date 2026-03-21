package aero.devtools;

import net.minecraft.src.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ScaledResolution;
import net.minecraft.src.Tessellator;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

/**
 * Dev toolbar overlay — centered at top of screen. Toggled with F10.
 * Minecraft-styled buttons with 3D bevel. Clickable actions.
 */
public class Aero_DevOverlay {

    private static boolean visible = false;
    private static ArrayList<Aero_DevInfoProvider> providers = new ArrayList<Aero_DevInfoProvider>();

    // TPS tracking
    private static long[] tickTimes = new long[20];
    private static int tickIndex = 0;
    private static long lastTickTime = 0;

    // Toggle cooldown
    private static long lastToggleTime = 0;

    // Status
    private static String statusText = "";
    private static int statusColor = 0xFFAAAAAA;
    private static long statusExpiry = 0;

    // Buttons
    private static final String[] BTN_LABELS = {"\u00a7bF6 Swap", "\u00a76F7 Restart", "\u00a7aF9 Textures"};
    private static final int BTN_COUNT = 3;
    private static int[] btnX = new int[3];
    private static int[] btnY = new int[3];
    private static int[] btnW = new int[3];
    private static final int BTN_H = 16;

    // Task
    private static boolean taskRunning = false;
    private static String taskLabel = "";

    public static void register(Aero_DevInfoProvider provider) {
        if (!Aero_DevBootstrap.IS_DEV) return;
        providers.add(provider);
    }

    public static void toggle() {
        long now = System.currentTimeMillis();
        if (now - lastToggleTime < 200) return;
        lastToggleTime = now;
        visible = !visible;
    }

    public static boolean isVisible() { return visible; }

    public static void setStatus(String text, int color) {
        statusText = text;
        statusColor = color;
        statusExpiry = System.currentTimeMillis() + 5000;
    }

    public static void setTaskRunning(boolean running, String label) {
        taskRunning = running;
        taskLabel = label;
    }

    public static void recordTick() {
        long now = System.currentTimeMillis();
        if (lastTickTime > 0) {
            tickTimes[tickIndex] = now - lastTickTime;
            tickIndex = (tickIndex + 1) % tickTimes.length;
        }
        lastTickTime = now;
    }

    private static float calculateTPS() {
        long total = 0;
        int count = 0;
        for (int i = 0; i < tickTimes.length; i++) {
            if (tickTimes[i] > 0) { total += tickTimes[i]; count++; }
        }
        if (count == 0) return 20.0f;
        return Math.min(20.0f, 1000.0f / ((float) total / count));
    }

    /** Called by DevConsole on F6 — recompile code (needs IDE attached for live swap) */
    public static void doSwap(Minecraft mc) {
        if (!Aero_DevBootstrap.IS_DEV) {
            setStatus("Launch with debug.sh first.", 0xFFFF4444);
            return;
        }
        runScript(mc, "hotswap.sh", "Recompiling");
    }

    /** Called by DevConsole on F7 — create restart flag + shutdown gracefully.
     *  The debug.sh loop detects the flag and auto-rebuilds+relaunches. */
    public static void doRestart(Minecraft mc) {
        if (!Aero_DevBootstrap.IS_DEV) {
            setStatus("Launch with debug.sh first.", 0xFFFF4444);
            return;
        }
        setStatus("Restarting...", 0xFFDCDCAA);
        try {
            // Create restart flag — debug.sh loop checks this
            java.io.File base = Aero_DevBootstrap.resolveModBase();
            java.io.File flag = new java.io.File(base, "temp/.restart");
            flag.getParentFile().mkdirs();
            flag.createNewFile();

            // Shutdown gracefully — debug.sh loop will detect flag and rebuild
            Thread.sleep(300);
            mc.shutdown();
        } catch (Exception e) {
            setStatus("Restart failed: " + e.getMessage(), 0xFFFF4444);
        }
    }


    private static void runScript(Minecraft mc, String script, String label) {
        if (taskRunning) return;

        java.io.File base = Aero_DevBootstrap.resolveModBase();
        java.io.File workspace = Aero_DevBootstrap.resolveWorkspace();
        java.io.File scriptFile = new java.io.File(workspace, "libraries/devtools/scripts/" + script);
        if (!scriptFile.exists()) {
            // Fallback to mod-local scripts
            scriptFile = new java.io.File(base, "scripts/" + script);
        }
        if (!scriptFile.exists()) {
            setStatus("Script not found: " + script, 0xFFFF4444);
            return;
        }

        // Read config
        final String bashExe = Aero_DevBootstrap.bashPath.length() > 0
            ? Aero_DevBootstrap.bashPath : "C:\\Program Files\\Git\\bin\\bash.exe";
        final String jdkPath = Aero_DevBootstrap.jdkPath;
        final int port = Aero_DevBootstrap.jdwpPort;
        final boolean hasDcevm = Aero_DevBootstrap.dcevm;
        final boolean isSwap = label.equals("Recompiling");

        setTaskRunning(true, label);
        setStatus(label + "...", 0xFFDCDCAA);

        final java.io.File fBase = base;
        final java.io.File fScript = scriptFile;
        final String fLabel = label;
        new Thread(new Runnable() {
            public void run() {
                try {
                    long start = System.currentTimeMillis();
                    System.out.println("[DevTools] Bash: " + bashExe);
                    System.out.println("[DevTools] Script: " + fScript.getAbsolutePath());
                    System.out.println("[DevTools] CWD: " + fBase.getAbsolutePath());

                    // Set JAVA_HOME and PATH for the script
                    ProcessBuilder pb = new ProcessBuilder(bashExe, fScript.getAbsolutePath());
                    pb.directory(fBase);
                    pb.redirectErrorStream(true);
                    java.util.Map<String, String> env = pb.environment();
                    if (jdkPath.length() > 0) {
                        env.put("JAVA_HOME", jdkPath);
                        env.put("PATH", jdkPath + "/bin" + java.io.File.pathSeparator + env.get("PATH"));
                    }

                    Process proc = pb.start();
                    java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(proc.getInputStream()));
                    String line;
                    String lastLine = "";
                    while ((line = br.readLine()) != null) {
                        System.out.println("[DevTools] " + line);
                        lastLine = line;
                    }
                    int exit = proc.waitFor();
                    long elapsed = System.currentTimeMillis() - start;

                    if (exit != 0) {
                        setStatus(fLabel + " FAILED: " + lastLine, 0xFFFF4444);
                        return;
                    }

                    String time = (elapsed / 1000) + "." + ((elapsed % 1000) / 100) + "s";

                    if (isSwap && hasDcevm) {
                        // Run HotSwapAgent to push classes via JDWP
                        setStatus("Pushing classes via DCEVM...", 0xFFDCDCAA);
                        java.io.File classDir = new java.io.File(fBase, "mcp/minecraft/bin/net/minecraft/src");
                        java.io.File toolsJar = new java.io.File(jdkPath, "lib/tools.jar");
                        java.io.File workspace = Aero_DevBootstrap.resolveWorkspace();
                        java.io.File devtoolsDir = new java.io.File(workspace, "libraries/devtools/tools");
                        java.io.File agentClass = new java.io.File(devtoolsDir, "HotSwapAgent.class");
                        java.io.File agentSrc = new java.io.File(devtoolsDir, "HotSwapAgent.java");

                        // Compile agent if needed
                        if (!agentClass.exists() || agentSrc.lastModified() > agentClass.lastModified()) {
                            String javac = jdkPath + "/bin/javac";
                            ProcessBuilder pb2 = new ProcessBuilder(javac, "-cp", toolsJar.getAbsolutePath(),
                                "-d", devtoolsDir.getAbsolutePath(), agentSrc.getAbsolutePath());
                            pb2.redirectErrorStream(true);
                            Process p2 = pb2.start();
                            p2.waitFor();
                        }

                        // Run agent
                        String javaExe = jdkPath + "/bin/java";
                        String cp = toolsJar.getAbsolutePath() + java.io.File.pathSeparator +
                                    devtoolsDir.getAbsolutePath();
                        ProcessBuilder pb3 = new ProcessBuilder(javaExe, "-cp", cp,
                            "HotSwapAgent", String.valueOf(port), classDir.getAbsolutePath());
                        pb3.directory(fBase);
                        pb3.redirectErrorStream(true);
                        Process p3 = pb3.start();
                        java.io.BufferedReader br3 = new java.io.BufferedReader(
                                new java.io.InputStreamReader(p3.getInputStream()));
                        String swapResult = "";
                        while ((line = br3.readLine()) != null) {
                            System.out.println("[DevTools] " + line);
                            swapResult = line;
                        }
                        int swapExit = p3.waitFor();
                        long totalElapsed = System.currentTimeMillis() - start;
                        String totalTime = (totalElapsed / 1000) + "." + ((totalElapsed % 1000) / 100) + "s";

                        if (swapExit == 0 && swapResult.contains("OK")) {
                            setStatus("Live swap OK (" + totalTime + ")", 0xFF4EC9B0);
                        } else {
                            setStatus("Recompiled (" + time + ") - swap failed: " + swapResult, 0xFFFFAA00);
                        }
                    } else if (isSwap) {
                        setStatus("Recompiled (" + time + ") - attach IDE to :" + port + " for live swap", 0xFF4EC9B0);
                    } else {
                        setStatus(fLabel + " OK (" + time + ")", 0xFF4EC9B0);
                    }
                } catch (Exception e) {
                    setStatus(fLabel + " error: " + e.getMessage(), 0xFFFF4444);
                } finally {
                    setTaskRunning(false, "");
                }
            }
        }).start();
    }

    /** Render the toolbar. Called from GuiIngame proxy during render frame. */
    public static void render(Minecraft mc) {
        if (!visible) return;

        FontRenderer font = mc.fontRenderer;
        if (font == null) return;

        ScaledResolution sr = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
        int screenW = sr.getScaledWidth();

        // Metrics
        float tps = calculateTPS();
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        long maxMB = rt.maxMemory() / 1048576;
        int pct = (int)(usedMB * 100 / maxMB);
        int texCount = Aero_DevTextureReload.getTrackedCount();

        String tpsColor = tps >= 19.0f ? "\u00a7a" : (tps >= 15.0f ? "\u00a7e" : "\u00a7c");
        String memColor = pct < 70 ? "\u00a7a" : (pct < 90 ? "\u00a7e" : "\u00a7c");

        // Debug status
        String debugStr;
        String debugDot;
        if (Aero_DevBootstrap.IS_DEV) {
            debugStr = "\u00a7aDev :" + Aero_DevBootstrap.jdwpPort;
            debugDot = "\u00a7a\u25cf ";
        } else {
            debugStr = "\u00a78No Debug";
            debugDot = "\u00a78\u25cf ";
        }

        String metricsStr = debugDot + debugStr
                + "   " + tpsColor + "TPS: " + formatFloat(tps, 1)
                + "   " + memColor + "Mem: " + usedMB + "/" + maxMB + "MB"
                + "   \u00a77Tex: " + texCount;

        // Calculate button widths
        int btnPad = 8;
        int btnGap = 4;
        int btnTotalW = 0;
        for (int i = 0; i < BTN_COUNT; i++) {
            btnW[i] = font.getStringWidth(stripColor(BTN_LABELS[i])) + btnPad * 2;
            btnTotalW += btnW[i] + (i < BTN_COUNT - 1 ? btnGap : 0);
        }

        int metricsW = font.getStringWidth(stripColor(metricsStr));
        // Include status text width in panel sizing
        int statusW = 0;
        boolean hasStatus = statusText.length() > 0 && System.currentTimeMillis() < statusExpiry;
        if (hasStatus) statusW = font.getStringWidth(statusText);
        if (taskRunning) statusW = Math.max(statusW, font.getStringWidth(taskLabel + "..."));
        int contentW = Math.max(Math.max(metricsW, btnTotalW), statusW) + 12;
        int pad = 8;
        int panelW = contentW + pad * 2;
        int panelX = (screenW - panelW) / 2;
        int panelY = 2;

        // Panel height: topPad + metrics + gap + buttons + bottomPad
        int panelH = 5 + 10 + 5 + BTN_H + 5;
        if (hasStatus || taskRunning) panelH += 12;

        // --- Render --- Already in correct 2D context (called from GuiIngame.renderGameOverlay)
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // Panel background — Minecraft style with beveled edges
        drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC000000);
        // Top border highlight
        drawRect(panelX, panelY, panelX + panelW, panelY + 1, 0xFF555555);
        // Bottom border shadow
        drawRect(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF222222);
        // Left border
        drawRect(panelX, panelY, panelX + 1, panelY + panelH, 0xFF444444);
        // Right border
        drawRect(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF222222);

        int cy = panelY + 5;

        // Metrics line (includes debug status) — centered
        int metricsX = panelX + (panelW - metricsW) / 2;
        font.drawStringWithShadow(metricsStr, metricsX, cy, 0xFFFFFF);
        cy += 15;

        // Buttons — Minecraft style (beveled rectangles)
        int bx = panelX + (panelW - btnTotalW) / 2;
        for (int i = 0; i < BTN_COUNT; i++) {
            btnX[i] = bx;
            btnY[i] = cy;

            // F6(0) + F7(1) need dev mode; F9(2) always works
            boolean disabled = taskRunning;
            if (i < 2 && !Aero_DevBootstrap.IS_DEV) disabled = true;
            drawMcButton(bx, cy, btnW[i], BTN_H, disabled);

            // Button text centered
            String label = BTN_LABELS[i];
            int tw = font.getStringWidth(stripColor(label));
            int tx = bx + (btnW[i] - tw) / 2;
            int textColor = disabled ? 0xFF666666 : 0xFFFFFFFF;
            font.drawStringWithShadow(disabled ? stripColor(label) : label, tx, cy + 4, textColor);

            bx += btnW[i] + btnGap;
        }
        cy += BTN_H + 3;

        // Status / task
        if (taskRunning) {
            long dots = (System.currentTimeMillis() / 400) % 4;
            String dotStr = "";
            for (int d = 0; d < (int)dots; d++) dotStr += ".";
            font.drawStringWithShadow("\u00a7e" + taskLabel + dotStr,
                    panelX + pad, cy, 0xFFFFFF);
            cy += 11;
        } else if (hasStatus) {
            font.drawStringWithShadow(statusText, panelX + pad, cy, statusColor);
            cy += 11;
        }

        // Restore GL state
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** Draw a Minecraft-style 3D beveled button. */
    private static void drawMcButton(int x, int y, int w, int h, boolean disabled) {
        int bg = disabled ? 0xFF2A2A2A : 0xFF3A3A3A;
        int highlight = disabled ? 0xFF3A3A3A : 0xFF606060;
        int shadow = disabled ? 0xFF1A1A1A : 0xFF1E1E1E;
        int innerLight = disabled ? 0xFF333333 : 0xFF4A4A4A;

        // Fill
        drawRect(x, y, x + w, y + h, bg);
        // Top edge — bright
        drawRect(x, y, x + w, y + 1, highlight);
        // Left edge — bright
        drawRect(x, y, x + 1, y + h, highlight);
        // Bottom edge — dark
        drawRect(x, y + h - 1, x + w, y + h, shadow);
        // Right edge — dark
        drawRect(x + w - 1, y, x + w, y + h, shadow);
        // Inner highlight (1px inside, top+left)
        drawRect(x + 1, y + 1, x + w - 1, y + 2, innerLight);
        drawRect(x + 1, y + 1, x + 2, y + h - 1, innerLight);
    }

    private static String stripColor(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\u00a7' && i + 1 < s.length()) i++;
            else sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static void drawRect(int x1, int y1, int x2, int y2, int color) {
        float a = (float)(color >> 24 & 0xFF) / 255.0f;
        float r = (float)(color >> 16 & 0xFF) / 255.0f;
        float g = (float)(color >> 8 & 0xFF) / 255.0f;
        float b = (float)(color & 0xFF) / 255.0f;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertex(x1, y2, 0.0);
        tess.addVertex(x2, y2, 0.0);
        tess.addVertex(x2, y1, 0.0);
        tess.addVertex(x1, y1, 0.0);
        tess.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static String formatFloat(float val, int decimals) {
        int factor = 1;
        for (int i = 0; i < decimals; i++) factor *= 10;
        int rounded = Math.round(val * factor);
        return (rounded / factor) + "." + (rounded % factor);
    }
}
