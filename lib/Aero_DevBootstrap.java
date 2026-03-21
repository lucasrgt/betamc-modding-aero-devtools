package aero.devtools;

/**
 * Core dev tools bootstrap. Detects dev environment via -Daero.dev=true.
 * All dev features gate on IS_DEV. Excluded from release builds via transpile.
 */
public class Aero_DevBootstrap {

    /** True when running with -Daero.dev=true. All dev features gate on this. */
    public static boolean IS_DEV = "true".equals(System.getProperty("aero.dev"));

    private static boolean initialized = false;

    // Config from devtools.json
    public static String modPath = "";
    public static String jdkPath = "";
    public static String bashPath = "";
    public static int jdwpPort = 5006;
    public static boolean dcevm = false;

    /**
     * Initialize dev tools. Call from any mod's constructor.
     * Safe to call multiple times — only initializes once.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;
        loadConfig();
        if (IS_DEV) {
            System.out.println("[DevTools] Development mode active");
            System.out.println("[DevTools] F6 = Hot Swap | F7 = Restart | F9 = Textures | F10 = Overlay");
            System.out.println("[DevTools] JDK: " + jdkPath);
            System.out.println("[DevTools] Bash: " + bashPath);
            System.out.println("[DevTools] DCEVM: " + dcevm);
            System.out.println("[DevTools] JDWP port: " + jdwpPort);
        }
    }

    private static void loadConfig() {
        java.io.File workspace = resolveWorkspace();
        java.io.File config = new java.io.File(workspace, "devtools.json");
        if (!config.exists()) {
            System.out.println("[DevTools] No devtools.json found — run tools/aero-dev/setup.bat");
            return;
        }
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(config));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String json = sb.toString();
            modPath = extractJson(json, "modPath");
            jdkPath = extractJson(json, "jdkPath");
            bashPath = extractJson(json, "bashPath");
            String port = extractJson(json, "jdwpPort");
            if (port.length() > 0) jdwpPort = Integer.parseInt(port);
            dcevm = "true".equals(extractJson(json, "dcevm"));
        } catch (Exception e) {
            System.out.println("[DevTools] Error loading devtools.json: " + e.getMessage());
        }
    }

    private static String extractJson(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(":", i);
        if (colon < 0) return "";
        int vs = colon + 1;
        while (vs < json.length() && (json.charAt(vs) == ' ' || json.charAt(vs) == '\t')) vs++;
        if (vs >= json.length()) return "";
        if (json.charAt(vs) == '"') {
            int end = json.indexOf("\"", vs + 1);
            return end > 0 ? json.substring(vs + 1, end) : "";
        } else {
            int end = vs;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ' ') end++;
            return json.substring(vs, end);
        }
    }

    /** Resolve workspace root from tmpdir (mod/tests/data/tmp → mod → workspace) */
    public static java.io.File resolveModBase() {
        java.io.File workspace = resolveWorkspace();
        if (modPath.length() > 0) {
            return new java.io.File(workspace, modPath);
        }
        java.io.File tmpDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        return tmpDir.getParentFile().getParentFile().getParentFile();
    }

    /** Resolve workspace root (up from mod's tests/data/tmp) */
    public static java.io.File resolveWorkspace() {
        java.io.File tmpDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        // tmpdir = {mod}/tests/data/tmp → workspace = {mod}/../../..  wait no
        // tmpdir = {workspace}/{modPath}/tests/data/tmp
        // Go up 5 levels: tmp → data → tests → mod → authoral → mods → workspace... too many
        // Actually just go up from mod base to workspace
        java.io.File modBase = tmpDir.getParentFile().getParentFile().getParentFile();
        // modBase = mods/authoral/beta-energistics, workspace = 3 levels up
        return modBase.getParentFile().getParentFile().getParentFile();
    }
}
