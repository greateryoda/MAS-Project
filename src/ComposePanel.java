import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Random;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ComposePanel extends Card {
    private static final String CHOOSE = "Load a demonstration email...";

    private final JComboBox<Object> demos = new JComboBox<>();
    private final JTextField address = Theme.field();
    private final JTextField name = Theme.field();
    private final JTextField subject = Theme.field();
    private final JTextField thread = Theme.field();
    private final JTextArea body = Theme.area(9);
    private final JLabel error = Theme.label(" ", Theme.font(Font.PLAIN, 12), Theme.DANGER);
    private final PillButton preview = new PillButton("Preview", PillButton.Kind.SECONDARY);
    private final PillButton clear = new PillButton("Clear", PillButton.Kind.GHOST);
    private final PillButton process = new PillButton("Process email", PillButton.Kind.PRIMARY);
    private final Random random = new Random();

    public ComposePanel(List<DemoEmail> demoEmails) {
        super(new BorderLayout(0, 12), 16);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Theme.heading("New email"));
        header.add(Box.createVerticalStrut(2));
        header.add(Theme.caption("Manual entry of a received email"));
        add(header, BorderLayout.NORTH);

        demos.addItem(CHOOSE);
        demoEmails.forEach(demos::addItem);
        demos.setFont(Theme.font(Font.PLAIN, 13));
        demos.setBackground(Theme.SURFACE);
        demos.addActionListener(e -> {
            if (demos.getSelectedItem() instanceof DemoEmail d) fill(d);
        });

        thread.setText(String.valueOf(newThreadId()));
        PillButton newThread = new PillButton("New", PillButton.Kind.GHOST);
        newThread.setToolTipText("Start a new discussion thread");
        newThread.addActionListener(e -> thread.setText(String.valueOf(newThreadId())));
        JPanel threadRow = new JPanel(new BorderLayout(6, 0));
        threadRow.setOpaque(false);
        threadRow.add(thread, BorderLayout.CENTER);
        threadRow.add(newThread, BorderLayout.EAST);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        addRow(form, demos);
        addRow(form, Theme.fieldGroup("Sender address *", address));
        addRow(form, Theme.fieldGroup("Sender name", name));
        addRow(form, Theme.fieldGroup("Subject *", subject));
        addRow(form, Theme.fieldGroup("Thread ID (same ID = same conversation)", threadRow));
        JComponent bodyGroup = Theme.fieldGroup("Message *", Theme.scroll(body, true));
        bodyGroup.setAlignmentX(0f);
        form.add(bodyGroup);
        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
        buttons.setOpaque(false);
        buttons.add(preview);
        buttons.add(clear);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);
        south.add(process, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        clear.addActionListener(e -> clear());
    }

    public PillButton getPreviewButton() { return preview; }
    public PillButton getProcessButton() { return process; }

    public Email buildEmail() {
        String a = address.getText().trim();
        if (!a.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) return fail("Enter a valid sender address.");
        if (subject.getText().isBlank()) return fail("The subject is required.");
        if (body.getText().isBlank()) return fail("The message is required.");
        int threadId;
        try {
            threadId = Integer.parseInt(thread.getText().trim());
            if (threadId < 0) return fail("The thread ID must be a positive number.");
        } catch (NumberFormatException e) {
            return fail("The thread ID must be a number.");
        }
        error.setText(" ");
        Email email = new Email(a, subject.getText().trim(), body.getText().trim(), DemoEmail.today(), threadId);
        if (!name.getText().isBlank()) email.setSenderName(name.getText().trim());
        return email;
    }

    public void setBusy(boolean busy) {
        for (JComponent c : new JComponent[] {demos, address, name, subject, thread, body, preview, clear, process}) {
            c.setEnabled(!busy);
        }
        process.setText(busy ? "Processing..." : "Process email");
    }

    public void prepareFollowUp(Email previous) {
        demos.setSelectedIndex(0);
        address.setText(previous.getSenderAddress());
        name.setText(previous.getSenderName() == null ? "" : previous.getSenderName());
        String s = previous.getSubject();
        subject.setText(s.toLowerCase().startsWith("re:") ? s : "Re: " + s);
        thread.setText(String.valueOf(previous.getThreadID()));
        body.setText("");
        body.requestFocusInWindow();
    }

    private void fill(DemoEmail d) {
        address.setText(d.address());
        name.setText(d.name());
        subject.setText(d.subject());
        thread.setText(String.valueOf(d.threadId()));
        body.setText(d.body());
        body.setCaretPosition(0);
        error.setText(" ");
    }

    private void clear() {
        demos.setSelectedIndex(0);
        for (JTextField f : new JTextField[] {address, name, subject}) f.setText("");
        thread.setText(String.valueOf(newThreadId()));
        body.setText("");
        error.setText(" ");
    }

    private Email fail(String message) {
        error.setText(message);
        return null;
    }

    private int newThreadId() {
        return 2000 + random.nextInt(8000);
    }

    private void addRow(JPanel form, JComponent c) {
        c.setAlignmentX(0f);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        form.add(c);
        form.add(Box.createVerticalStrut(10));
    }
}
