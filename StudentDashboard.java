import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.io.*;
import javax.swing.table.TableModel;


public class StudentDashboard extends JFrame {
    private static final String DB_URL = "jdbc:sqlite:college.db";


    // DB & student state
    private Connection conn;
    private int studentId;
    private String studentName;

    // Main UI tabs
    private JTabbedPane tabs;
    public StudentDashboard(String email) {
        super("Student Dashboard");
        initDB(email);
        initUI();
    }


   

    private void initDB(String email) {
        try {
            conn = DriverManager.getConnection(DB_URL);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, name FROM students WHERE email = ?")) {
                ps.setString(1, email);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    studentId = rs.getInt("id");
                    studentName = rs.getString("name");
                }
            }
        } catch (SQLException ex) {
            showError("Database error: " + ex.getMessage());
        }
    }

    private void initUI() {
        // Apply FlatLaf
        try { UIManager.setLookAndFeel(new FlatLightLaf()); }
        catch (Exception ignored) {}

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(10,10,10,10));

        tabs.addTab("Details", makeDetailsPanel());
        tabs.addTab("Timetable", makeTimetablePanel());
        tabs.addTab("Attendance", makeAttendancePanel());
        tabs.addTab("Report", makeReportPanel());
        tabs.addTab("Logout", makeLogOut());

        getContentPane().add(tabs, BorderLayout.CENTER);
        setVisible(true);
    }

    private JPanel makeLogOut() {
        JPanel logoutPanel = new JPanel();
        logoutPanel.setLayout(new BorderLayout());
        logoutPanel.setBackground(new Color(240, 240, 240));

        JButton logoutButton = new JButton("Logout");
        styleButton(logoutButton);

        logoutPanel.add(logoutButton, BorderLayout.CENTER);

        logoutButton.addActionListener(e -> {
            dispose();  // Close current window
            new LoginForm();  // Open the login form again (assuming this class exists)
        });

        return logoutPanel;
    }

    private void styleButton(JButton button) {
        button.setBackground(new Color(33, 150, 243));
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 16));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(30, 136, 229));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(33, 150, 243));
            }
        });
    }

    
    private JPanel makeDetailsPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); // Use vertical BoxLayout
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
    
        // Add a welcome label at the top
        JLabel lbl = new JLabel("Welcome, " + studentName, SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 28f));
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT); // Center-align the label
        p.add(lbl);
    
        // Add spacing
        p.add(Box.createRigidArea(new Dimension(0, 20)));
    
        // Fetch attendance data
        double overallPercentage = 0;
        Map<String, Double> subjectPercentages = new HashMap<>();
        String sql = "SELECT s.name, COUNT(a.date) AS total_classes, " +
                     "SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS attended_classes " +
                     "FROM attendance a " +
                     "JOIN subjects s ON a.subject_id = s.id " +
                     "WHERE a.student_id = ? " +
                     "GROUP BY s.id";
    
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            int totalClasses = 0, attendedClasses = 0;
            while (rs.next()) {
                String subject = rs.getString("name");
                int subjectTotal = rs.getInt("total_classes");
                int subjectAttended = rs.getInt("attended_classes");
                double percentage = (subjectTotal > 0) ? (subjectAttended * 100.0) / subjectTotal : 0;
                subjectPercentages.put(subject, percentage);
    
                totalClasses += subjectTotal;
                attendedClasses += subjectAttended;
            }
            overallPercentage = (totalClasses > 0) ? (attendedClasses * 100.0) / totalClasses : 0;
        } catch (SQLException ex) {
            showError("Could not load attendance: " + ex.getMessage());
        }
    
        // Add overall attendance circle
        AttendanceCirclePanel overallCircle = new AttendanceCirclePanel("Overall", overallPercentage);
        overallCircle.setAlignmentX(Component.CENTER_ALIGNMENT); // Center-align the circle
        p.add(overallCircle);
    
        // Add spacing
        p.add(Box.createRigidArea(new Dimension(0, 20)));
    
        // Add subject-wise attendance circles
        for (Map.Entry<String, Double> entry : subjectPercentages.entrySet()) {
            AttendanceCirclePanel subjectCircle = new AttendanceCirclePanel(entry.getKey(), entry.getValue());
            subjectCircle.setAlignmentX(Component.CENTER_ALIGNMENT); // Center-align the circle
            p.add(subjectCircle);
    
            // Add spacing between circles
            p.add(Box.createRigidArea(new Dimension(0, 20)));
        }
    
        return p;
    }



       // Custom JPanel to draw attendance circles
    // This class creates a circular progress indicator for attendance percentage
    private class AttendanceCirclePanel extends JPanel {
        private final String label;
        private final double percentage;
    
        public AttendanceCirclePanel(String label, double percentage) {
            this.label = label;
            this.percentage = percentage;
            setPreferredSize(new Dimension(150, 150)); // Set size for the circle
        }
    
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    
            // Determine color based on percentage
            Color color;
            if (percentage >= 75) {
                color = new Color(76, 175, 80); // Green for >= 75%
            } else if (percentage >= 50) {
                color = new Color(255, 193, 7); // Yellow for >= 50%
            } else {
                color = new Color(244, 67, 54); // Red for < 50%
            }
    
            // Draw the background ring
            int diameter = Math.min(getWidth(), getHeight()) - 20;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            g2d.setColor(new Color(200, 200, 200)); // Light gray for the full circle
            g2d.setStroke(new BasicStroke(10)); // Set the thickness of the ring
            g2d.drawArc(x, y, diameter, diameter, 0, 360);
    
            // Draw the progress ring
            g2d.setColor(color);
            g2d.drawArc(x, y, diameter, diameter, 90, -(int) (360 * (percentage / 100))); // Start from the top (90 degrees)
    
            // Draw the percentage text
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Segoe UI", Font.BOLD, 16));
            String text = String.format("%.1f%%", percentage);
            FontMetrics fm = g2d.getFontMetrics();
            int textX = (getWidth() - fm.stringWidth(text)) / 2;
            int textY = (getHeight() + fm.getAscent()) / 2 - 10;
            g2d.drawString(text, textX, textY);
    
            // Draw the label
            g2d.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            int labelX = (getWidth() - fm.stringWidth(label)) / 2;
            g2d.drawString(label, labelX, textY + 20);
        }
    }
    private JPanel makeAttendancePanel() {
    JPanel p = new JPanel(new BorderLayout(10, 10));
    p.setBorder(new EmptyBorder(10, 10, 10, 10));
    p.add(new JLabel("View your attendance", SwingConstants.CENTER), BorderLayout.NORTH);

    // Define columns for the attendance table
    String[] columns = {"Subject", "Total Classes", "Classes Attended", "Attendance Percentage"};
    List<Object[]> rows = new ArrayList<>();
    
    // SQL query to get subject-wise attendance information
    String sql = "SELECT s.name, COUNT(a.date) AS total_classes, " +
                 "SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS attended_classes " +
                 "FROM attendance a " +
                 "JOIN subjects s ON a.subject_id = s.id " +
                 "WHERE a.student_id = ? " +
                 "GROUP BY s.id";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, studentId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String subject = rs.getString("name");
            int totalClasses = rs.getInt("total_classes");
            int attendedClasses = rs.getInt("attended_classes");
            double percentage = (totalClasses > 0) ? (attendedClasses * 100.0) / totalClasses : 0;
            rows.add(new Object[]{
                subject, 
                totalClasses, 
                attendedClasses, 
                String.format("%.2f", percentage) + "%"   // Format to two decimal places
            });
        }
    } catch (SQLException ex) {
        showError("Could not load attendance: " + ex.getMessage());
    }

    // Calculate overall attendance percentage
    int totalClasses = 0;
    int attendedClasses = 0;
    for (Object[] row : rows) {
        totalClasses += (int) row[1];
        attendedClasses += (int) row[2];
    }

    double overallPercentage = (totalClasses > 0) ? (attendedClasses * 100.0) / totalClasses : 0;
    rows.add(new Object[]{"Overall", totalClasses, attendedClasses, String.format("%.2f", overallPercentage) + "%"});

    // Convert rows list to Object array
    Object[][] data = new Object[rows.size()][4];
    for (int i = 0; i < rows.size(); i++) {
        data[i] = rows.get(i);
    }

    // Create JTable to display the data
    JTable table = new JTable(data, columns);
    table.setRowHeight(24);
    JScrollPane scrollPane = new JScrollPane(table);

    p.add(scrollPane, BorderLayout.CENTER);
    return p;
}

private JPanel makeTimetablePanel() {
    String[] columns = {"Day", "8:00 - 9:00", "9:00 - 10:00", "10:00 - 11:00", "11:00 - 12:00", "12:00 - 1:00", "1:00 - 2:00", "2:00 - 3:00"};
    List<Object[]> rows = new ArrayList<>();
    String sql = "SELECT t.day, t.hour, s.name " +
                 "FROM timetable t " +
                 "JOIN subjects s ON t.subject_id = s.id " +
                 "WHERE t.class_id IN (SELECT class_id FROM student_classes WHERE student_id = ?) " +
                 "ORDER BY CASE t.day " +
                 "    WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 " +
                 "    WHEN 'Wednesday' THEN 3 WHEN 'Thursday' THEN 4 ELSE 5 END, t.hour";

    // Map to store subjects for each day and period
    Map<String, String[]> timetableData = new HashMap<>();
    
    // Initialize timetable data with empty subjects
    for (String day : new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"}) {
        timetableData.put(day, new String[7]);  // 7 periods (8 AM to 3 PM)
    }

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, studentId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String day = rs.getString("day");
            int hour = rs.getInt("hour") - 1;  // Adjusting index for 0-based array
            String subject = rs.getString("name");

            // Assign the subject to the corresponding day and hour
            if (hour >= 0 && hour < 7) {
                timetableData.get(day)[hour] = subject;
            }
        }
    } catch (SQLException ex) {
        showError("Could not load timetable: " + ex.getMessage());
    }

    // Populate rows with timetable data for each day
    for (String day : new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"}) {
        Object[] row = new Object[columns.length];
        row[0] = day;

        String[] subjects = timetableData.get(day);
        for (int i = 0; i < 7; i++) {
            row[i + 1] = subjects[i] != null ? subjects[i] : "-";  // If no subject, show "-"
        }

        rows.add(row);
    }

    Object[][] data = new Object[rows.size()][columns.length];
    for (int i = 0; i < rows.size(); i++) {
        data[i] = rows.get(i);
    }

    JTable table = new JTable(data, columns);
    table.setRowHeight(24);

    JPanel p = new JPanel(new BorderLayout(10, 10));
    p.setBorder(new EmptyBorder(10, 10, 10, 10));
    p.add(new JScrollPane(table), BorderLayout.CENTER);
    p.add(new JLabel("Your Timetable", SwingConstants.CENTER), BorderLayout.SOUTH);
    return p;
}

 private JPanel makeReportPanel() {
    JPanel p = new JPanel(new BorderLayout(10, 10));
    p.setBorder(new EmptyBorder(10, 10, 10, 10));

    JLabel title = new JLabel("Subject Attendance Report", SwingConstants.CENTER);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
    p.add(title, BorderLayout.NORTH);

    // Controls: subject, from/to, gen, export
    JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
    JComboBox<Subject> subjectBox = new JComboBox<>(fetchTeacherSubjects().toArray(new Subject[0]));
    JTextField fromDate = new JTextField(8);
    JTextField toDate = new JTextField(8);
    JButton gen = new JButton("Generate");
    JButton exportCSV = new JButton("Export CSV");

    ctrl.add(new JLabel("Subject:"));
    ctrl.add(subjectBox);
    ctrl.add(new JLabel("From:"));
    ctrl.add(fromDate);
    ctrl.add(new JLabel("To:"));
    ctrl.add(toDate);
    ctrl.add(gen);
    ctrl.add(exportCSV);

    p.add(ctrl, BorderLayout.WEST);

    JTable reportTable = new JTable();
    p.add(new JScrollPane(reportTable), BorderLayout.CENTER);

    gen.addActionListener(e -> {
        Subject subj = (Subject) subjectBox.getSelectedItem();
        String f = fromDate.getText().trim();
        String t = toDate.getText().trim();

        // build query with two placeholders now
        StringBuilder sb = new StringBuilder(
            "SELECT date, hour, status FROM attendance " +
            "WHERE subject_id = ? AND student_id = ?"
        );
        boolean hasDateRange = f.matches("\\d{4}-\\d{2}-\\d{2}") && t.matches("\\d{4}-\\d{2}-\\d{2}");
        if (hasDateRange) {
            sb.append(" AND date BETWEEN ? AND ?");
        }
        sb.append(" ORDER BY date, hour");

        List<Object[]> data = new ArrayList<>();
        int total = 0, present = 0;

        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            ps.setInt(1, subj.id);
            ps.setInt(2, studentId);            // ← dynamically use your studentId field

            if (hasDateRange) {
                ps.setString(3, f);
                ps.setString(4, t);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                total++;
                String status = rs.getString("status");
                if ("Present".equalsIgnoreCase(status)) present++;
                data.add(new Object[]{
                    rs.getString("date"),
                    rs.getInt("hour"),
                    status
                });
            }
        } catch (SQLException ex) {
            showError("Report error: " + ex.getMessage());
            return;
        }

        if (data.isEmpty()) {
            JOptionPane.showMessageDialog(p,
                "No attendance data found for the selected subject and date range.",
                "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // populate table
        String[] cols = {"Date", "Hour", "Status"};
        Object[][] tableData = new Object[data.size()][3];
        for (int i = 0; i < data.size(); i++) {
            tableData[i] = data.get(i);
        }
        reportTable.setModel(new DefaultTableModel(tableData, cols));

        double pct = total > 0 ? (present * 100.0 / total) : 0;
        JOptionPane.showMessageDialog(this,
            String.format("Attendance in %s: %.1f%% (%d/%d)",
                subj.name, pct, present, total),
            "Attendance %", JOptionPane.INFORMATION_MESSAGE);
    });

    exportCSV.addActionListener(e -> {
        TableModel model = reportTable.getModel();
        if (model.getRowCount() == 0) {
            showError("No data to export.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(p) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            try (PrintWriter pw = new PrintWriter(file)) {
                // header
                for (int i = 0; i < model.getColumnCount(); i++) {
                    pw.print(model.getColumnName(i));
                    if (i < model.getColumnCount() - 1) pw.print(",");
                }
                pw.println();
                // rows
                for (int r = 0; r < model.getRowCount(); r++) {
                    for (int c = 0; c < model.getColumnCount(); c++) {
                        pw.print(model.getValueAt(r, c));
                        if (c < model.getColumnCount() - 1) pw.print(",");
                    }
                    pw.println();
                }
                JOptionPane.showMessageDialog(p,
                    "CSV exported to:\n" + file.getAbsolutePath());
            } catch (IOException ex) {
                showError("CSV export failed: " + ex.getMessage());
            }
        }
    });

    return p;
}


private List<Subject> fetchTeacherSubjects() {
    List<Subject> subjects = new ArrayList<>();
    // Modify the SQL query to fetch distinct subjects
    String sql = "SELECT DISTINCT s.id, s.name FROM subjects s " +
                 "JOIN timetable t ON s.id = t.subject_id " +
                 "JOIN student_classes sc ON t.class_id = sc.class_id " +
                 "WHERE sc.student_id = ?"; // Use the studentId to find enrolled subjects
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, studentId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            subjects.add(new Subject(rs.getInt(1), rs.getString(2)));
        }
    } catch (SQLException ex) {
        showError("Error fetching subjects: " + ex.getMessage());
    }
    return subjects;
}




    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static class Student {
        final int id; final String name;
        Student(int i, String n){ id=i; name=n; }
        public String toString(){ return name; }
    }
    private static class Subject {
        final int id; final String name;
        Subject(int i, String n){ id=i; name=n; }
        public String toString(){ return name; }
    }

}
