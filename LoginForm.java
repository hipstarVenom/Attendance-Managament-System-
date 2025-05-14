import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.Arrays;
import com.formdev.flatlaf.FlatLightLaf;

public class LoginForm extends JFrame {
    private static final String DB_URL = "jdbc:sqlite:college.db";

    private JTextField emailField;
    private JPasswordField passwordField;
    private JComboBox<String> userTypeComboBox;
    private JButton loginButton;
    private JLabel loadingLabel;
    private boolean isPasswordVisible = false;

    public LoginForm() {
        setTitle("College Attendance Portal - Login");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Set background color of the outer area to white
        getContentPane().setBackground(Color.WHITE);

        JPanel backgroundPanel = new JPanel(new GridBagLayout());
        backgroundPanel.setBackground(Color.WHITE);

        // Main panel with blue background
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(new Color(66, 133, 244)); // Blue background for the form
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 160, 160), 2, true),
            BorderFactory.createEmptyBorder(30, 40, 30, 40)
        ));
        mainPanel.setPreferredSize(new Dimension(400, 520));

        // Logo
        JLabel logoLabel = new JLabel();
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        try {
            ImageIcon icon = new ImageIcon("college_logo.png");
            Image img = icon.getImage().getScaledInstance(80, 80, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(img));
        } catch (Exception ex) {
            logoLabel.setText("🎓");
            logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        }

        // Title
        JLabel titleLabel = new JLabel("College Attendance Portal");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 30, 0));

        // Email
        JLabel emailLabel = new JLabel("Email:");
        emailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        emailField = new JTextField(20);
        styleField(emailField);

        // Password
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        passwordField = new JPasswordField(20);
        styleField(passwordField);

        // Add toggle visibility icon
        JPanel passwordPanel = new JPanel(new BorderLayout());
        passwordPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        passwordPanel.setBackground(Color.WHITE);
        passwordPanel.add(passwordField, BorderLayout.CENTER);

        JLabel toggleIcon = new JLabel();
        toggleIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleIcon.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        setIcon(toggleIcon, isPasswordVisible);
        toggleIcon.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                isPasswordVisible = !isPasswordVisible;
                passwordField.setEchoChar(isPasswordVisible ? (char) 0 : '\u2022');
                setIcon(toggleIcon, isPasswordVisible);
            }
        });
        passwordPanel.add(toggleIcon, BorderLayout.EAST);

        // User Type
        JLabel userTypeLabel = new JLabel("User Type:");
        userTypeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        userTypeComboBox = new JComboBox<>(new String[]{"Student", "Teacher", "Admin"});
        styleField(userTypeComboBox);

        // Login Button
        loginButton = new JButton("Login");
        styleButton(loginButton);
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Loading Label
        loadingLabel = new JLabel(" ");
        loadingLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        loadingLabel.setForeground(Color.WHITE);
        loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Assemble
        mainPanel.add(logoLabel);
        mainPanel.add(titleLabel);
        mainPanel.add(emailLabel);
        mainPanel.add(emailField);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(passwordLabel);
        mainPanel.add(passwordPanel); // updated line with toggle icon
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(userTypeLabel);
        mainPanel.add(userTypeComboBox);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(loginButton);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(loadingLabel);

        backgroundPanel.add(mainPanel);
        add(backgroundPanel);

        loginButton.addActionListener(e -> performLogin());

        setVisible(true);
    }

    private void setIcon(JLabel label, boolean visible) {
        String iconPath = "eye-icon.png"; // Replace with "eye.png" or "eye_closed.png" for toggle
        if (visible) iconPath = "eye-icon-visible.png"; // optional: use a second image if available
        ImageIcon icon = new ImageIcon(iconPath);
        Image img = icon.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH);
        label.setIcon(new ImageIcon(img));
    }

    private void styleField(JComponent field) {
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        field.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        field.setBorder(BorderFactory.createLineBorder(new Color(160, 160, 160))); // Added border to the field
    }

    private void styleButton(JButton btn) {
        btn.setBackground(new Color(52, 103, 190)); // Darker blue for button
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(66, 133, 244)); // Lighter blue on hover
            }

            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(52, 103, 190)); // Restore original button color
            }
        });
    }

    private void performLogin() {
        setLoading(true);
        Timer timer = new Timer(800, e -> {
            String email = emailField.getText().trim();
            char[] pwdChars = passwordField.getPassword();
            String pwd = new String(pwdChars).trim();
            Arrays.fill(pwdChars, '\0');

            String userType = (String) userTypeComboBox.getSelectedItem();

            if (validateLogin(email, pwd, userType)) {
                switch (userType) {
                    case "Admin" -> new AdminDashboard();
                    case "Teacher" -> new TeacherDashboard(email);
                    case "Student" -> new StudentDashboard(email);
                }
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Invalid credentials for " + userType,
                        "Login Failed",
                        JOptionPane.ERROR_MESSAGE);
                setLoading(false);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private boolean validateLogin(String email, String pwd, String userType) {
        if ("Admin".equals(userType)) {
            return "admin".equals(email) && "4321".equals(pwd);
        }

        String sql = switch (userType) {
            case "Teacher" -> "SELECT 1 FROM teachers WHERE email = ? AND password = ?";
            case "Student" -> "SELECT 1 FROM students WHERE email = ? AND password = ?";
            default -> null;
        };

        return sql != null && queryCredential(sql, email, pwd);
    }

    private boolean queryCredential(String sql, String email, String pwd) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, pwd);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Database error. Please try again.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loadingLabel.setForeground(Color.WHITE);
        loadingLabel.setText(loading ? "Logging in..." : " ");
    }

    public static void initialize() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }
        new LoginForm();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginForm::initialize);
    }
}
