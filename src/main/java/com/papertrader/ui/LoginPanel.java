package com.papertrader.ui;

import com.papertrader.exception.AuthException;
import com.papertrader.service.AuthService;
import com.papertrader.ui.three.Coin3DView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

/** First screen: a spinning 3D coin on the left, login / register form on the right. */
public class LoginPanel extends JPanel {

    private final AuthService authService;
    private final Runnable onLoginSuccess;

    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JLabel messageLabel = new JLabel(" ");

    /**
     * @param onLoginSuccess what to do after a successful login (MainFrame passes "show the app").
     *                       Passing behaviour in like this keeps LoginPanel independent of MainFrame (low coupling).
     */
    public LoginPanel(AuthService authService, Runnable onLoginSuccess) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
        setLayout(new GridLayout(1, 2));
        setBackground(Theme.BACKGROUND);
        add(buildBrandSide());
        add(buildFormSide());
    }

    private JPanel buildBrandSide() {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(Theme.BACKGROUND);
        side.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel("PaperTrader 3D");
        title.setFont(Theme.TITLE.deriveFont(34f));
        title.setForeground(Theme.TEXT);
        JLabel tagline = new JLabel("Practise trading stocks, crypto and ETFs with virtual money.");
        tagline.setFont(Theme.BODY);
        tagline.setForeground(Theme.MUTED);

        JPanel text = new JPanel(new BorderLayout(0, 6));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(tagline, BorderLayout.SOUTH);

        side.add(text, BorderLayout.NORTH);
        side.add(new Coin3DView(), BorderLayout.CENTER);
        return side;
    }

    private JPanel buildFormSide() {
        JPanel outer = new JPanel(new GridBagLayout());   // GridBagLayout centres the card
        outer.setBackground(Theme.SIDEBAR);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(32, 32, 32, 32)));
        card.setPreferredSize(new Dimension(380, 400));

        JLabel heading = new JLabel("Welcome back");
        heading.setFont(Theme.TITLE);
        heading.setForeground(Theme.TEXT);
        JLabel hint = new JLabel("New here? Pick a username and press Create account.");
        hint.setFont(Theme.SMALL);
        hint.setForeground(Theme.MUTED);

        usernameField.putClientProperty("JTextField.placeholderText", "Username");
        passwordField.putClientProperty("JTextField.placeholderText", "Password");
        JButton loginButton = new JButton("Log in");
        loginButton.putClientProperty("JButton.buttonType", "default");
        loginButton.setBackground(Theme.ACCENT);
        loginButton.setForeground(java.awt.Color.WHITE);
        JButton registerButton = new JButton("Create account");
        messageLabel.setFont(Theme.SMALL);
        JLabel demo = new JLabel("Demo account:  demo / demo");
        demo.setFont(Theme.SMALL);
        demo.setForeground(Theme.MUTED);

        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> register());
        passwordField.addActionListener(e -> login());   // Enter key logs in

        for (Component c : new Component[]{heading, hint, usernameField, passwordField, loginButton,
                registerButton, messageLabel, demo}) {
            ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            if (c instanceof JTextField || c instanceof JButton) {
                c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            }
        }
        card.add(heading);
        card.add(Box.createVerticalStrut(4));
        card.add(hint);
        card.add(Box.createVerticalStrut(24));
        card.add(usernameField);
        card.add(Box.createVerticalStrut(12));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(20));
        card.add(loginButton);
        card.add(Box.createVerticalStrut(10));
        card.add(registerButton);
        card.add(Box.createVerticalStrut(14));
        card.add(messageLabel);
        card.add(Box.createVerticalGlue());
        card.add(demo);

        outer.add(card);
        return outer;
    }

    private void login() {
        try {
            authService.login(usernameField.getText().trim(), new String(passwordField.getPassword()));
            passwordField.setText("");
            showMessage(" ", false);
            onLoginSuccess.run();
        } catch (AuthException e) {
            showMessage(e.getMessage(), true);
        }
    }

    private void register() {
        try {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());
            authService.register(username, password);
            authService.login(username, password);
            passwordField.setText("");
            onLoginSuccess.run();
        } catch (AuthException e) {
            showMessage(e.getMessage(), true);
        }
    }

    private void showMessage(String text, boolean isError) {
        messageLabel.setText(text);
        messageLabel.setForeground(isError ? Theme.RED : Theme.GREEN);
    }
}
