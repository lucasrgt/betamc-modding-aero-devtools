import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import java.util.*;
import java.io.*;

/**
 * HotSwapAgent — connects to JDWP port and redefines all changed classes.
 * Usage: java -cp tools.jar:. HotSwapAgent <port> <classdir>
 *
 * Scans classdir for .class files and sends redefined classes to the
 * running JVM via JDWP. Uses DCEVM for structural redefinition support.
 */
public class HotSwapAgent {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java HotSwapAgent <port> <classdir>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        File classDir = new File(args[1]);

        VirtualMachine vm = null;
        try {
            // Connect to JDWP
            VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
            AttachingConnector connector = null;
            for (Connector c : vmm.attachingConnectors()) {
                if (c.name().equals("com.sun.jdi.SocketAttach")) {
                    connector = (AttachingConnector) c;
                    break;
                }
            }
            if (connector == null) {
                System.err.println("SocketAttach connector not found");
                System.exit(1);
            }

            Map<String, Connector.Argument> arguments = connector.defaultArguments();
            arguments.get("hostname").setValue("localhost");
            arguments.get("port").setValue(String.valueOf(port));

            System.out.print("Connecting to localhost:" + port + "... ");
            try {
                vm = connector.attach(arguments);
            } catch (Exception e) {
                System.err.println("FAILED: " + e.getMessage());
                System.err.println("Is the game running with debug.sh?");
                System.exit(1);
                return;
            }
            System.out.println("OK (" + vm.name() + ")");

            // Find all .class files
            File[] classFiles = classDir.listFiles(new FileFilter() {
                public boolean accept(File f) {
                    return f.getName().endsWith(".class");
                }
            });

            if (classFiles == null || classFiles.length == 0) {
                System.out.println("No .class files found in " + classDir);
                return;
            }

            // All files in this dir are pre-filtered (only changed classes)
            Map<ReferenceType, byte[]> redefinitions = new HashMap<ReferenceType, byte[]>();

            for (File cf : classFiles) {
                String className = cf.getName().replace(".class", "");

                // Find class in VM — try default package first (reobfuscated), then net.minecraft.src
                List<ReferenceType> types = vm.classesByName(className);
                if (types.isEmpty()) {
                    types = vm.classesByName("net.minecraft.src." + className);
                }
                if (types.isEmpty()) {
                    continue;
                }

                // Read class bytes
                byte[] bytes = new byte[(int) cf.length()];
                FileInputStream fis = new FileInputStream(cf);
                fis.read(bytes);
                fis.close();

                redefinitions.put(types.get(0), bytes);
            }

            if (redefinitions.isEmpty()) {
                System.out.println("No changed classes to swap");
                return;
            }

            // Redefine classes one by one (skip failures gracefully)
            int swapped = 0;
            int failed = 0;
            for (Map.Entry<ReferenceType, byte[]> entry : redefinitions.entrySet()) {
                Map<ReferenceType, byte[]> single = new HashMap<ReferenceType, byte[]>();
                single.put(entry.getKey(), entry.getValue());
                try {
                    vm.redefineClasses(single);
                    swapped++;
                } catch (Throwable e) {
                    System.err.println("  Skip " + entry.getKey().name() + ": " + e.getMessage());
                    failed++;
                }
            }
            System.out.println("Swapped " + swapped + " classes" + (failed > 0 ? " (" + failed + " skipped)" : "") + "!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } finally {
            // Dispose cleanly — DCEVM re-opens the listener after disconnect
            if (vm != null) {
                try { vm.dispose(); } catch (Exception ignored) {}
            }
        }
    }
}
