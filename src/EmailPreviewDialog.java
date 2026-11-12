import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class EmailPreviewDialog extends JDialog {

    public EmailPreviewDialog(Window owner, Email email, TemporaryContactBook contacts, Runnable onProcess) {
        super(owner, "Email preview", ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        Card card = new Card(new BorderLayout(0, 14), 20);
        String name = email.getSenderName() != null ? email.getSenderName() : email.getSenderAddress();
        String role = contacts.getRole(email.getSenderAddress());

        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        head.add(new Avatar(Theme.initials(name), Theme.ACCENT, 42), BorderLayout.WEST);
        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        who.add(Theme.label(name, Theme.font(Font.BOLD, 14), Theme.TEXT));
        who.add(Theme.caption(email.getSenderAddress() + "   ·   " + email.getDate()
                              + "   ·   thread #" + email.getThreadID()));
        head.add(who, BorderLayout.CENTER);
        head.add(role != null ? new Badge("Known contact: " + role, Theme.SUCCESS_SOFT, Theme.SUCCESS)
                              : new Badge("Unknown sender", Theme.NEUTRAL_SOFT, Theme.TEXT_MUTED), BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(Theme.label(email.getSubject(), Theme.font(Font.BOLD, 17), Theme.TEXT), BorderLayout.SOUTH);
        card.add(top, BorderLayout.NORTH);

        JTextArea body = Theme.area(14);
        body.setText(email.getBody());
        body.setEditable(false);
        body.setFont(Theme.font(Font.PLAIN, 13.5f));
        body.setCaretPosition(0);
        card.add(Theme.scroll(body, false), BorderLayout.CENTER);
        root.add(card, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        PillButton edit = new PillButton("Back to editing", PillButton.Kind.SECONDARY);
        PillButton process = new PillButton("Process this email", PillButton.Kind.PRIMARY);
        edit.addActionListener(e -> dispose());
        process.addActionListener(e -> { dispose(); onProcess.run(); });
        actions.add(edit);
        actions.add(process);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(640, 520);
        setLocationRelativeTo(owner);
    }
}
