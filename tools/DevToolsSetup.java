import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Properties;

/**
 * Aero DevTools Setup — GUI for configuring dev environment.
 * Generates devtools.json in the workspace root.
 * Run: java DevToolsSetup [workspace_root]
 */
public class DevToolsSetup extends JFrame {
    private JTextField modPathField;
    private JTextField jdkPathField;
    private JTextField bashPathField;
    private JTextField portField;
    private JCheckBox dcevmCheck;
    private File workspaceRoot;

    public DevToolsSetup(File root) {
        super("Aero DevTools Setup");
        this.workspaceRoot = root;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        // Dark theme
        Color bg = new Color(30, 30, 35);
        Color fieldBg = new Color(45, 45, 55);
        Color text = new Color(220, 220, 230);
        Color accent = new Color(78, 201, 176);
        Color btnBg = new Color(60, 60, 70);

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBackground(bg);
        main.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Title
        JLabel title = new JLabel("Aero DevTools Setup");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(accent);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(title);
        main.add(Box.createVerticalStrut(5));

        JLabel subtitle = new JLabel("Configure your development environment");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(150, 150, 160));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(subtitle);
        main.add(Box.createVerticalStrut(20));

        // Fields
        modPathField = addPathField(main, "Mod Path", "mods/authoral/beta-energistics", bg, fieldBg, text, false);
        jdkPathField = addPathField(main, "JDK Path", detectJdk(), bg, fieldBg, text, true);
        bashPathField = addPathField(main, "Bash Path", detectBash(), bg, fieldBg, text, true);

        // Port
        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        portPanel.setBackground(bg);
        portPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        portPanel.setMaximumSize(new Dimension(450, 50));
        JLabel portLabel = new JLabel("JDWP Port");
        portLabel.setForeground(text);
        portLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        portLabel.setPreferredSize(new Dimension(100, 25));
        portField = new JTextField("5006", 6);
        portField.setBackground(fieldBg);
        portField.setForeground(text);
        portField.setCaretColor(text);
        portField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 70, 80)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        portPanel.add(portLabel);
        portPanel.add(portField);
        main.add(portPanel);
        main.add(Box.createVerticalStrut(12));

        // DCEVM checkbox
        dcevmCheck = new JCheckBox("DCEVM installed (enables full class hot-reload)");
        dcevmCheck.setBackground(bg);
        dcevmCheck.setForeground(text);
        dcevmCheck.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dcevmCheck.setSelected(detectDcevm());
        dcevmCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(dcevmCheck);
        main.add(Box.createVerticalStrut(6));

        // DCEVM status
        String dcevmStatus = detectDcevm() ? "DCEVM detected in JDK" : "DCEVM not found — only method body swap available";
        JLabel dcevmLabel = new JLabel(dcevmStatus);
        dcevmLabel.setForeground(detectDcevm() ? accent : new Color(200, 150, 50));
        dcevmLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        dcevmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        main.add(dcevmLabel);
        main.add(Box.createVerticalStrut(20));

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setBackground(bg);
        btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPanel.setMaximumSize(new Dimension(450, 40));

        JButton detectBtn = createButton("Auto Detect", btnBg, text);
        detectBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { autoDetect(); }
        });

        JButton saveBtn = createButton("Save & Close", accent, new Color(20, 20, 25));
        saveBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { saveAndClose(); }
        });

        btnPanel.add(detectBtn);
        btnPanel.add(saveBtn);
        main.add(btnPanel);

        setContentPane(main);
        pack();
        setSize(500, 380);
        setLocationRelativeTo(null);

        // Load existing config
        loadExisting();
    }

    private JTextField addPathField(JPanel parent, String label, String defaultVal,
                                     Color bg, Color fieldBg, Color text, boolean isAbsolute) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBackground(bg);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(450, 50));

        JLabel lbl = new JLabel(label);
        lbl.setForeground(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setPreferredSize(new Dimension(100, 25));

        JTextField field = new JTextField(defaultVal);
        field.setBackground(fieldBg);
        field.setForeground(text);
        field.setCaretColor(text);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 70, 80)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JButton browse = new JButton("...");
        browse.setBackground(new Color(60, 60, 70));
        browse.setForeground(text);
        browse.setPreferredSize(new Dimension(35, 25));
        browse.setFocusPainted(false);
        browse.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 80)));
        final JTextField fField = field;
        final boolean fAbs = isAbsolute;
        browse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser();
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (fAbs) {
                    fc.setCurrentDirectory(new File(fField.getText()));
                } else {
                    fc.setCurrentDirectory(workspaceRoot);
                }
                if (fc.showOpenDialog(DevToolsSetup.this) == JFileChooser.APPROVE_OPTION) {
                    File sel = fc.getSelectedFile();
                    if (fAbs) {
                        fField.setText(sel.getAbsolutePath().replace("\\", "/"));
                    } else {
                        // Make relative to workspace
                        String rel = workspaceRoot.toURI().relativize(sel.toURI()).getPath();
                        if (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
                        fField.setText(rel);
                    }
                }
            }
        });

        panel.add(lbl, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        panel.add(browse, BorderLayout.EAST);
        parent.add(panel);
        parent.add(Box.createVerticalStrut(10));
        return field;
    }

    private JButton createButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 90)),
            BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        return btn;
    }

    private String detectJdk() {
        // Check common JDK 8 locations
        String[] paths = {
            "C:/Program Files/Java/jdk1.8.0_181",
            "C:/Program Files/Java/jdk1.8.0_202",
            "C:/Program Files/Java/jdk-8",
            System.getenv("JAVA_HOME")
        };
        for (String p : paths) {
            if (p != null && new File(p, "bin/java.exe").exists()) return p.replace("\\", "/");
        }
        return "C:/Program Files/Java/jdk1.8.0_181";
    }

    private String detectBash() {
        String[] paths = {
            "C:/Program Files/Git/bin/bash.exe",
            "C:/Program Files (x86)/Git/bin/bash.exe",
            "C:/Git/bin/bash.exe"
        };
        for (String p : paths) {
            if (new File(p).exists()) return p.replace("\\", "/");
        }
        return "C:/Program Files/Git/bin/bash.exe";
    }

    private boolean detectDcevm() {
        String jdk = jdkPathField != null ? jdkPathField.getText() : detectJdk();
        File dcevm = new File(jdk, "jre/bin/dcevm/jvm.dll");
        if (dcevm.exists()) return true;
        // Try running with -XXaltjvm=dcevm
        try {
            Process p = new ProcessBuilder(jdk + "/bin/java", "-XXaltjvm=dcevm", "-version")
                .redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("Dynamic Code Evolution")) return true;
            }
        } catch (Exception e) {}
        return false;
    }

    private void autoDetect() {
        jdkPathField.setText(detectJdk());
        bashPathField.setText(detectBash());
        dcevmCheck.setSelected(detectDcevm());
        // Scan for mods
        File modsDir = new File(workspaceRoot, "mods/authoral");
        if (modsDir.exists()) {
            File[] mods = modsDir.listFiles();
            if (mods != null) {
                for (File m : mods) {
                    if (new File(m, "scripts").exists() && new File(m, "src").exists()) {
                        String rel = workspaceRoot.toURI().relativize(m.toURI()).getPath();
                        if (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
                        modPathField.setText(rel);
                        break;
                    }
                }
            }
        }
    }

    private void loadExisting() {
        File config = new File(workspaceRoot, "devtools.json");
        if (!config.exists()) return;
        try {
            BufferedReader br = new BufferedReader(new FileReader(config));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String json = sb.toString();
            modPathField.setText(extractJson(json, "modPath"));
            jdkPathField.setText(extractJson(json, "jdkPath"));
            bashPathField.setText(extractJson(json, "bashPath"));
            portField.setText(extractJson(json, "jdwpPort"));
            dcevmCheck.setSelected("true".equals(extractJson(json, "dcevm")));
        } catch (Exception e) {}
    }

    private String extractJson(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(":", i);
        if (colon < 0) return "";
        // Find value start
        int vs = colon + 1;
        while (vs < json.length() && (json.charAt(vs) == ' ' || json.charAt(vs) == '\t')) vs++;
        if (vs >= json.length()) return "";
        if (json.charAt(vs) == '"') {
            int end = json.indexOf("\"", vs + 1);
            return end > 0 ? json.substring(vs + 1, end) : "";
        } else {
            // number or boolean
            int end = vs;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ' ') end++;
            return json.substring(vs, end);
        }
    }

    private void saveAndClose() {
        File config = new File(workspaceRoot, "devtools.json");
        try {
            PrintWriter pw = new PrintWriter(new FileWriter(config));
            pw.println("{");
            pw.println("  \"modPath\": \"" + modPathField.getText().replace("\\", "/") + "\",");
            pw.println("  \"jdkPath\": \"" + jdkPathField.getText().replace("\\", "/") + "\",");
            pw.println("  \"bashPath\": \"" + bashPathField.getText().replace("\\", "/") + "\",");
            pw.println("  \"jdwpPort\": " + portField.getText() + ",");
            pw.println("  \"dcevm\": " + dcevmCheck.isSelected());
            pw.println("}");
            pw.close();
            JOptionPane.showMessageDialog(this, "Configuration saved to devtools.json",
                "Saved", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error saving: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        // Dark Swing look
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        File root;
        if (args.length > 0) {
            root = new File(args[0]);
        } else {
            // Try to find workspace root
            root = new File(System.getProperty("user.dir"));
            // Walk up to find CLAUDE.md
            File check = root;
            for (int i = 0; i < 5; i++) {
                if (new File(check, "CLAUDE.md").exists() && new File(check, "mods").exists()) {
                    root = check;
                    break;
                }
                check = check.getParentFile();
                if (check == null) break;
            }
        }

        final File fRoot = root;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new DevToolsSetup(fRoot).setVisible(true);
            }
        });
    }
}
