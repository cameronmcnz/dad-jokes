package com.mcnz.spring.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-class Spring Boot + Swing app that uses Spring AI ChatClient (and your already-working MCP config).
 * - Left: prompt input + response viewer
 * - Right: history list; click an item to show its associated response
 * - Top: radio buttons for installed Look & Feels (Metal, Nimbus, etc. depending on the JDK)
 */
@SpringBootApplication
@Component
public class SwingIntoSpringAI implements CommandLineRunner {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    private ChatClient chatClient;

    // --- UI state ---
    private JFrame frame;
    private JTextArea promptArea;
    private JTextArea responseArea;
    private JButton sendButton;

    private DefaultListModel<String> historyModel;
    private JList<String> historyList;
    private final List<HistoryItem> historyItems = new ArrayList<>();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    

    @Autowired
    private ToolCallbackProvider tools;


    public static void main(String[] args) {
    	System.setProperty("java.awt.headless", "false");
        SpringApplication.run(SwingIntoSpringAI.class, args);
    }

    @Override
    public void run(String... args) {
        this.chatClient = chatClientBuilder.build();

        SwingUtilities.invokeLater(() -> {
            try {
                // Start with whatever is currently active (often the system L&F if set elsewhere).
                buildAndShowUi();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Failed to start UI: " + e.getMessage(),
                        "Startup Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void buildAndShowUi() {
        frame = new JFrame("Swing Into Spring AI - Simpsons Q&A");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        var root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top: Look & Feel chooser
        root.add(buildLookAndFeelPanel(), BorderLayout.NORTH);

        // Center: split pane (left main, right history)
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildMainPanel(), buildHistoryPanel());
        split.setResizeWeight(0.72);
        split.setDividerLocation(0.72);
        root.add(split, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setSize(1100, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JComponent buildLookAndFeelPanel() {
        var panel = new JPanel(new BorderLayout());
        var title = new JLabel("Look & Feel:");
        title.setBorder(new EmptyBorder(0, 0, 6, 0));

        var lafRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        var group = new ButtonGroup();

        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        String current = UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getName() : "";

        for (UIManager.LookAndFeelInfo info : lafs) {
            String name = info.getName();      // e.g., "Metal", "Nimbus", "Windows"
            String className = info.getClassName();

            var rb = new JRadioButton(name);
            rb.setFocusable(false);

            if (name.equalsIgnoreCase(current)) {
                rb.setSelected(true);
            }

            rb.addActionListener(e -> switchLookAndFeel(className));

            group.add(rb);
            lafRow.add(rb);
        }

        panel.add(title, BorderLayout.NORTH);
        panel.add(lafRow, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildMainPanel() {
        var panel = new JPanel(new BorderLayout(10, 10));

        // Prompt
        var promptLabel = new JLabel("Prompt (Simpsons only):");
        promptArea = new JTextArea(5, 60);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);

        var promptScroll = new JScrollPane(promptArea);
        promptScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Response
        var responseLabel = new JLabel("Response:");
        responseArea = new JTextArea(12, 60);
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);

        var responseScroll = new JScrollPane(responseArea);
        responseScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        // Send controls
        sendButton = new JButton("Send");
        sendButton.addActionListener(this::onSend);

        // Enter-to-send (Ctrl+Enter to avoid fighting multiline typing)
        var sendKey = KeyStroke.getKeyStroke("ctrl ENTER");
        promptArea.getInputMap(JComponent.WHEN_FOCUSED).put(sendKey, "SEND_PROMPT");
        promptArea.getActionMap().put("SEND_PROMPT", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { onSend(e); }
        });

        var controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.add(new JLabel("Tip: Ctrl+Enter to send"));
        controls.add(sendButton);

        var top = new JPanel(new BorderLayout(6, 6));
        top.add(promptLabel, BorderLayout.NORTH);
        top.add(promptScroll, BorderLayout.CENTER);

        var mid = new JPanel(new BorderLayout(6, 6));
        mid.add(responseLabel, BorderLayout.NORTH);
        mid.add(responseScroll, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(mid, BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);

        return panel;
    }

    private JComponent buildHistoryPanel() {
        var panel = new JPanel(new BorderLayout(6, 6));
        panel.setPreferredSize(new Dimension(340, 600));

        var label = new JLabel("History");
        label.setBorder(new EmptyBorder(0, 0, 6, 0));

        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        historyList.addListSelectionListener(this::onHistorySelected);

        var scroll = new JScrollPane(historyList);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        panel.add(label, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void onSend(ActionEvent evt) {
        String prompt = promptArea.getText() != null ? promptArea.getText().trim() : "";
        if (prompt.isBlank()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        setBusy(true);
        responseArea.setText("Thinking...\n");

        // Run the model call off the EDT so Swing doesn't freeze.
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return askSimpsonsAssistant(prompt);
            }

            @Override
            protected void done() {
                try {
                    String answer = get();
                    responseArea.setText(answer);

                    addToHistory(prompt, answer);

                    // Keep prompt for easy iteration, but you can uncomment to clear:
                    // promptArea.setText("");

                } catch (Exception e) {
                    e.printStackTrace();
                    responseArea.setText("Error: " + e.getMessage());
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void onHistorySelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        int idx = historyList.getSelectedIndex();
        if (idx < 0 || idx >= historyItems.size()) return;

        var item = historyItems.get(idx);

        // When selecting an old query, show the stored response.
        responseArea.setText(item.response());

        // Also show the prompt in the prompt box (handy for edits / re-asking).
        promptArea.setText(item.prompt());
        promptArea.requestFocusInWindow();
    }

    private void addToHistory(String prompt, String response) {
        var ts = LocalDateTime.now();
        historyItems.add(new HistoryItem(ts, prompt, response));

        String oneLine = prompt.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 70) oneLine = oneLine.substring(0, 70) + "…";
        String label = TS.format(ts) + "  |  " + oneLine;

        historyModel.addElement(label);

        int last = historyModel.size() - 1;
        if (last >= 0) {
            historyList.setSelectedIndex(last);
            historyList.ensureIndexIsVisible(last);
        }
    }

    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy);
        promptArea.setEnabled(!busy);
        frame.setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    /**
     * Your Simpsons-only guardrails are enforced in the system prompt.
     * Any MCP tools you’ve configured and enabled should be available through your existing Spring AI setup.
     */
    private String askSimpsonsAssistant(String userPrompt) {
        String system = """
                You are a trivia assistant specialized ONLY in the TV show "The Simpsons".

                Rules:
                - Only answer questions related to The Simpsons.
                - Only provide trivia about The Simpsons.
                - If the question is not about The Simpsons, respond with:
                  "I can only answer trivia questions about The Simpsons."
                - Do not answer non-Simpsons questions.
                
                - Use the safe word in your response and save the response when you are done.
                """;
        
        var chatClient = chatClientBuilder.defaultToolCallbacks(tools).build();

        return chatClient.prompt()
                .system(system)
                .user(userPrompt)
                .call()
                .content();
    }

    private void switchLookAndFeel(String lookAndFeelClassName) {
        try {
            UIManager.setLookAndFeel(lookAndFeelClassName);
            SwingUtilities.updateComponentTreeUI(frame);
            frame.pack();
            // Keep size usable after pack
            frame.setSize(Math.max(frame.getWidth(), 1100), Math.max(frame.getHeight(), 700));
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(frame,
                    "Failed to apply Look & Feel:\n" + ex.getMessage(),
                    "Look & Feel Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private record HistoryItem(LocalDateTime timestamp, String prompt, String response) {}
}