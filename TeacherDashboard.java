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


public class TeacherDashboard extends JFrame {
    private static final String DB_URL = "jdbc:sqlite:college.db";

    // DB & teacher state
    private Connection conn;
    private int teacherId;
    private String teacherName;

    // Attendance‐marking state
    private int selClass, selSubject, selHour;
    private String selDate;
    private Map<Integer, JComboBox<String>> attendanceMap;
    private JButton saveAttendanceBtn;

    // Main UI tabs
    private JTabbedPane tabs;

    public TeacherDashboard(String email) {
        super("Teacher Dashboard");
        initDB(email);
        initUI();
    }

    private void initDB(String email) {
        try {
            conn = DriverManager.getConnection(DB_URL);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, name FROM teachers WHERE email = ?")) {
                ps.setString(1, email);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    teacherId   = rs.getInt("id");
                    teacherName = rs.getString("name");
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

        tabs.addTab("Details",    makeDetailsPanel());
        tabs.addTab("Timetable",  makeTimetablePanel());
        tabs.addTab("Classes",    makeClassListPanel());
        tabs.addTab("Attendance", makeAttendancePanel());
        tabs.addTab("Report",     makeReportPanel());
        tabs.addTab("Logout",makeLogOut());
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


    
    private JPanel makeDetailsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(20,20,20,20));
        JLabel lbl = new JLabel("Welcome, " + teacherName, SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 28f));
        p.add(lbl, BorderLayout.CENTER);
        return p;
    }

    private JPanel makeTimetablePanel() {
        String[] cols = {"Day","Hour","Class","Subject"};
        List<Object[]> rows = new ArrayList<>();
        String sql =
          "SELECT t.day, t.hour, c.id, c.name, s.id, s.name " +
          "FROM timetable t " +
          " JOIN classes c ON t.class_id = c.id" +
          " JOIN subjects s ON t.subject_id = s.id" +
          " WHERE t.teacher_id = ?" +
          " ORDER BY CASE t.day " +
          "    WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 " +
          "    WHEN 'Wednesday' THEN 3 WHEN 'Thursday' THEN 4 ELSE 5 END, t.hour";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                rows.add(new Object[]{
                    rs.getString(1), rs.getInt(2),
                    rs.getInt(3),   rs.getString(4),
                    rs.getInt(5),   rs.getString(6)
                });
        } catch (SQLException ex) {
            showError("Could not load timetable: " + ex.getMessage());
        }

        Object[][] data = new Object[rows.size()][4];
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            data[i] = new Object[]{ r[0], r[1], r[3], r[5] };
        }

        JTable table = new JTable(data, cols);
        table.setRowHeight(24);
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int r = table.getSelectedRow();
                if (r >= 0) {
                    Object[] rec = rows.get(r);
                    selClass   = (int)rec[2];
                    selSubject = (int)rec[4];
                    selHour    = (int)rec[1];
                    prepareAttendanceForm(
                        (String)rec[3], (String)rec[5],
                        (String)rec[0], selHour
                    );
                }
            }
        });

        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(new EmptyBorder(10,10,10,10));
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(new JLabel("Click a row to mark attendance", SwingConstants.CENTER),
              BorderLayout.SOUTH);
        return p;
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

    private JPanel makeClassListPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        p.setBorder(new EmptyBorder(10,10,10,10));
        String sql =
          "SELECT DISTINCT c.id, c.name " +
          "FROM classes c JOIN timetable t ON c.id = t.class_id " +
          "WHERE t.teacher_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int cid     = rs.getInt(1);
                String name = rs.getString(2);
                JButton btn = new JButton(name);
                btn.addActionListener(e -> showClassStudents(cid, name));
                p.add(btn);
            }
        } catch (SQLException ex) {
            showError("Could not load classes: " + ex.getMessage());
        }
        return p;
    }

    private void showClassStudents(int classId, String className) {
        String sql =
          "SELECT st.name, st.email " +
          "FROM students st JOIN student_classes sc ON st.id = sc.student_id " +
          "WHERE sc.class_id = ?";
        List<Object[]> lst = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                lst.add(new Object[]{ rs.getString(1), rs.getString(2) });
        } catch (SQLException ex) {
            showError("Could not load students: " + ex.getMessage());
            return;
        }
        Object[][] data = lst.toArray(new Object[0][]);
        JTable tbl = new JTable(data, new String[]{"Name","Email"});
        tbl.setRowHeight(22);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createTitledBorder(className + " Students"));
        wrap.add(new JScrollPane(tbl), BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, wrap,
            "Students in " + className, JOptionPane.PLAIN_MESSAGE);
    }

    private JPanel makeAttendancePanel() {
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(new EmptyBorder(10,10,10,10));
        p.add(new JLabel("Select a timetable row first", SwingConstants.CENTER),
              BorderLayout.NORTH);
        p.add(new JScrollPane(new JPanel()), BorderLayout.CENTER);
        return p;
    }

    private void prepareAttendanceForm(String className,
                                       String subjName,
                                       String day,
                                       int hour) {
        JPanel attPanel = (JPanel)tabs.getComponentAt(3);
        attPanel.removeAll();
        attPanel.setLayout(new BorderLayout(10,10));

        // Date field
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,10,5));
        top.add(new JLabel("Date (YYYY-MM-DD):"));
        JTextField dateField = new JTextField(10);
        top.add(dateField);
        attPanel.add(top, BorderLayout.NORTH);

        // Header
        JLabel lbl = new JLabel(
            String.format("Mark Attendance: %s / %s — %s Hour %d",
                className, subjName, day, hour),
            SwingConstants.CENTER
        );
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 18f));
        attPanel.add(lbl, BorderLayout.CENTER);

        // Student rows
        attendanceMap = new LinkedHashMap<>();
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(10,10,10,10));
        String sql =
          "SELECT st.id, st.name " +
          "FROM students st JOIN student_classes sc ON st.id = sc.student_id " +
          "WHERE sc.class_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selClass);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int sid   = rs.getInt(1);
                String nm = rs.getString(2);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT,20,5));
                row.add(new JLabel(nm));
                JComboBox<String> cb = new JComboBox<>(new String[]{"Present","Absent"});
                row.add(cb);
                attendanceMap.put(sid, cb);
                form.add(row);
            }
        } catch (SQLException ex) {
            showError("Could not load students: " + ex.getMessage());
        }
        attPanel.add(new JScrollPane(form), BorderLayout.CENTER);

        // Save button
        saveAttendanceBtn = new JButton("Save Attendance");
        saveAttendanceBtn.setEnabled(false);
        saveAttendanceBtn.addActionListener(e -> saveAttendance(hour));
        JPanel bottom = new JPanel();
        bottom.add(saveAttendanceBtn);
        attPanel.add(bottom, BorderLayout.SOUTH);

        // Enable Save only when date is valid
        dateField.getDocument().addDocumentListener(new DocumentListener() {
            void update() {
                String d = dateField.getText().trim();
                boolean ok = d.matches("\\d{4}-\\d{2}-\\d{2}");
                saveAttendanceBtn.setEnabled(ok);
                if (ok) selDate = d;
            }
            public void insertUpdate(DocumentEvent e) { update(); }
            public void removeUpdate(DocumentEvent e) { update(); }
            public void changedUpdate(DocumentEvent e) { update(); }
        });

        tabs.setSelectedIndex(3);
        attPanel.revalidate();
        attPanel.repaint();
    }

    private void saveAttendance(int hour) {
        if (selDate == null || selDate.isBlank()) return;
        String sql =
          "INSERT OR REPLACE INTO attendance " +
          "(student_id, subject_id, date, hour, status) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (var e : attendanceMap.entrySet()) {
                ps.setInt(1, e.getKey());
                ps.setInt(2, selSubject);
                ps.setString(3, selDate);
                ps.setInt(4, hour);
                ps.setString(5, (String)e.getValue().getSelectedItem());
                ps.addBatch();
            }
            ps.executeBatch();
            JOptionPane.showMessageDialog(this,
                "Attendance saved for " + selDate);
        } catch (SQLException ex) {
            showError("Error saving attendance: " + ex.getMessage());
        }
    }

private JPanel makeReportPanel() {
    JPanel p = new JPanel(new BorderLayout(10, 10));
    p.setBorder(new EmptyBorder(10, 10, 10, 10));

    JLabel title = new JLabel("Student/Subject Attendance Report", SwingConstants.CENTER);
    title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
    p.add(title, BorderLayout.NORTH);

    // Controls: student, subject, from/to, gen, export
    JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
    JComboBox<Student> studentBox = new JComboBox<>(
        fetchStudentsInClass().toArray(new Student[0])
    );
    JComboBox<Subject> subjectBox = new JComboBox<>(
        fetchTeacherSubjects().toArray(new Subject[0])
    );
    JTextField fromDate = new JTextField(8);
    JTextField toDate = new JTextField(8);
    JButton gen = new JButton("Generate");
    JButton exportCSV = new JButton("Export CSV");

    ctrl.add(new JLabel("Student:")); ctrl.add(studentBox);
    ctrl.add(new JLabel("Subject:")); ctrl.add(subjectBox);
    ctrl.add(new JLabel("From:")); ctrl.add(fromDate);
    ctrl.add(new JLabel("To:")); ctrl.add(toDate);
    ctrl.add(gen);
    ctrl.add(exportCSV);

    p.add(ctrl, BorderLayout.WEST);

    JTable reportTable = new JTable();
    p.add(new JScrollPane(reportTable), BorderLayout.CENTER);

    gen.addActionListener(e -> {
        Student stu = (Student) studentBox.getSelectedItem();
        Subject subj = (Subject) subjectBox.getSelectedItem();
        String f = fromDate.getText().trim();
        String t = toDate.getText().trim();

        StringBuilder sb = new StringBuilder(
            "SELECT date, hour, status FROM attendance " +
            "WHERE student_id=? AND subject_id=?"
        );
        if (f.matches("\\d{4}-\\d{2}-\\d{2}") && t.matches("\\d{4}-\\d{2}-\\d{2}")) {
            sb.append(" AND date BETWEEN ? AND ?");
        }
        sb.append(" ORDER BY date, hour");

        List<Object[]> data = new ArrayList<>();
        int total = 0, present = 0;

        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            ps.setInt(1, stu.id);
            ps.setInt(2, subj.id);
            if (sb.indexOf("BETWEEN") > 0) {
                ps.setString(3, f);
                ps.setString(4, t);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String date = rs.getString("date");
                int hr = rs.getInt("hour");
                String st = rs.getString("status");
                data.add(new Object[]{date, hr, st});
                total++;
                if ("Present".equals(st)) present++;
            }
        } catch (SQLException ex) {
            showError("Report error: " + ex.getMessage());
        }

        reportTable.setModel(new DefaultTableModel(
            data.toArray(new Object[0][]),
            new String[]{"Date", "Hour", "Status"}
        ));

        double pct = total > 0 ? (present * 100.0 / total) : 0;
        JOptionPane.showMessageDialog(p,
            String.format("%s’s attendance in %s: %.1f%% (%d/%d)",
                stu.name, subj.name, pct, present, total),
            "Attendance %", JOptionPane.INFORMATION_MESSAGE);
    });

    exportCSV.addActionListener(e -> {
        JFileChooser fileChooser = new JFileChooser();
        int option = fileChooser.showSaveDialog(p);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }

            try (PrintWriter pw = new PrintWriter(file)) {
                TableModel model = reportTable.getModel();
                // Header
                for (int i = 0; i < model.getColumnCount(); i++) {
                    pw.print(model.getColumnName(i));
                    if (i < model.getColumnCount() - 1) pw.print(",");
                }
                pw.println();

                // Rows
                for (int row = 0; row < model.getRowCount(); row++) {
                    for (int col = 0; col < model.getColumnCount(); col++) {
                        Object value = model.getValueAt(row, col);
                        pw.print(value != null ? value.toString() : "");
                        if (col < model.getColumnCount() - 1) pw.print(",");
                    }
                    pw.println();
                }

                JOptionPane.showMessageDialog(p, "CSV exported successfully!");
            } catch (IOException ex) {
                showError("CSV export failed: " + ex.getMessage());
            }
        }
    });

    return p;
}


  private List<Student> fetchStudentsInClass() {
    List<Student> list = new ArrayList<>();
    String sql =
      "SELECT DISTINCT st.id, st.name " +
      "FROM students st " +
      "JOIN student_classes sc ON st.id = sc.student_id " +
      "JOIN timetable t ON sc.class_id = t.class_id " +
      "WHERE t.teacher_id = ? " +
      "ORDER BY st.name";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, teacherId);  // Use your actual teacherId here
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            list.add(new Student(rs.getInt("id"), rs.getString("name")));
        }
    } catch (SQLException ex) {
        showError("Could not load students: " + ex.getMessage());
    }
    return list;
}




    private List<Subject> fetchTeacherSubjects() {
        List<Subject> list = new ArrayList<>();
        String sql =
          "SELECT s.id, s.name " +
          "FROM subjects s JOIN timetable t ON s.id = t.subject_id " +
          "WHERE t.teacher_id = ? GROUP BY s.id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                list.add(new Subject(rs.getInt("id"), rs.getString("name")));
        } catch (SQLException ex) {
            showError("Could not load subjects: " + ex.getMessage());
        }
        return list;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg,
            "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Simple holder types for JComboBox
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
            new TeacherDashboard("ravi@college.com")
        );
    }
}
