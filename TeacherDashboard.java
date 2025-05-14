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
import java.time.LocalDate;

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
    String email;
    public TeacherDashboard(String email) {
        super("Teacher Dashboard");
        initDB(email);
        initUI();
        this.email = email;
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
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ignored) {}
    
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    
        // Main container with tabs
        JPanel mainPanel = new JPanel(new BorderLayout());
        tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(10, 10, 10, 10));
    
        // Add tabs
        tabs.addTab("Details", makeDetailsPanel());
        tabs.addTab("Timetable", makeTimetablePanel());
        tabs.addTab("Classes", makeClassListPanel());
        tabs.addTab("Attendance", makeAttendancePanel());
        tabs.addTab("Full Attendance Report", makeFullAttendanceReportPanel());
        tabs.addTab("Low Attendance Report", makeLowAttendanceReportPanel());
        tabs.addTab("Report",makeReportPanel());
        tabs.addTab("Reload", makeReloadTab());
        tabs.addTab("Logout", makeLogOut());
    
        getContentPane().add(tabs, BorderLayout.CENTER);
        setVisible(true);
    }
    
    private void processAttendanceFromFile(File file) {
        String fileName = file.getName();
        if (!fileName.matches("\\d{4}-\\d{2}-\\d{2}-[A-Z]+\\.txt")) {
            showError("Invalid file name format. Expected: YYYY-MM-DD-SECTION.txt");
            return;
        }
    
        // Extract date and section from the file name
        String[] parts = fileName.split("-");
        String date = parts[0] + "-" + parts[1] + "-" + parts[2]; // YYYY-MM-DD
        String section = parts[3].replace(".txt", ""); // SECTION (e.g., "C")
    
        // Adjust the hour value if it starts from 0
        int adjustedHour = selHour + 1;
    
        // Validate the adjusted hour value
        if (adjustedHour < 1 || adjustedHour > 7) {
            showError("Invalid hour value: " + adjustedHour + ". Hour must be between 1 and 7.");
            return;
        }
    
        // Read absent student IDs from the file
        Set<Integer> absentStudentIds = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    absentStudentIds.add(Integer.parseInt(line.trim()));
                } catch (NumberFormatException ex) {
                    showError("Invalid student ID in file: " + line);
                }
            }
        } catch (IOException ex) {
            showError("Error reading file: " + ex.getMessage());
            return;
        }
    
        // Fetch all students in the class
        List<Integer> studentIds = fetchStudentIdsForSection(section);
        if (studentIds.isEmpty()) {
            showError("No students found for section: " + section);
            return;
        }
    
        // Mark attendance
        String sql = "INSERT OR REPLACE INTO attendance (student_id, subject_id, date, hour, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int studentId : studentIds) {
                ps.setInt(1, studentId);
                ps.setInt(2, selSubject); // Use the selected subject ID
                ps.setString(3, date);
                ps.setInt(4, adjustedHour); // Use the adjusted hour
                ps.setString(5, absentStudentIds.contains(studentId) ? "Absent" : "Present");
                ps.addBatch();
            }
            ps.executeBatch();
            JOptionPane.showMessageDialog(this, "Attendance processed successfully for " + date + " - " + section);
        } catch (SQLException ex) {
            showError("Error saving attendance: " + ex.getMessage());
        }
    }

  private List<Integer> fetchStudentIdsForSection(String section) {
        List<Integer> studentIds = new ArrayList<>();
        String sql = """
            SELECT st.id
            FROM students st
            JOIN student_classes sc ON st.id = sc.student_id
            JOIN classes c ON sc.class_id = c.id
            WHERE c.name = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Prepend "Class " to the section name to match the database format
            String processedSection = "Class " + section.trim();
            ps.setString(1, processedSection);
            System.out.println("Processed section name: " + processedSection);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                studentIds.add(rs.getInt("id"));
            }
        } catch (SQLException ex) {
            showError("Error fetching students for section: " + ex.getMessage());
        }
        
        return studentIds;
    }
      private JPanel makeFullAttendanceReportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
    
        JLabel title = new JLabel("Full Attendance Report", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        panel.add(title, BorderLayout.NORTH);
    
        // Controls: class and subject selectors
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JComboBox<ClassItem> classBox = new JComboBox<>(
            fetchTeacherClasses().toArray(new ClassItem[0])
        );
        JComboBox<Subject> subjectBox = new JComboBox<>(
            fetchTeacherSubjects().toArray(new Subject[0])
        );
        controls.add(new JLabel("Class:"));
        controls.add(classBox);
        controls.add(new JLabel("Subject:"));
        controls.add(subjectBox);
        panel.add(controls, BorderLayout.NORTH);
    
        // Table to display full attendance report
        JTable reportTable = new JTable();
        panel.add(new JScrollPane(reportTable), BorderLayout.CENTER);
    
        // Action listener for class and subject selection
        ActionListener updateReport = e -> {
            ClassItem selectedClass = (ClassItem) classBox.getSelectedItem();
            Subject selectedSubject = (Subject) subjectBox.getSelectedItem();
            if (selectedClass != null && selectedSubject != null) {
                List<Object[]> data = fetchFullAttendanceReport(selectedClass.id, selectedSubject.id);
                reportTable.setModel(new DefaultTableModel(
                    data.toArray(new Object[0][]),
                    new String[]{"Student Name", "Total Classes", "Present", "Absent", "Attendance %"}
                ));
            }
        };
        classBox.addActionListener(updateReport);
        subjectBox.addActionListener(updateReport);
    
        // Trigger initial population
        if (classBox.getItemCount() > 0 && subjectBox.getItemCount() > 0) {
            classBox.setSelectedIndex(0);
            subjectBox.setSelectedIndex(0);
        }
    
        return panel;
    }
    


    private List<Object[]> fetchFullAttendanceReport(int classId, int subjectId) {
        List<Object[]> fullAttendanceReport = new ArrayList<>();
        String sql = """
            SELECT st.name,
                   COUNT(a.status) AS total_classes,
                   SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS present_count,
                   SUM(CASE WHEN a.status = 'Absent' THEN 1 ELSE 0 END) AS absent_count,
                   ROUND((SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) * 100.0) / COUNT(a.status), 2) AS attendance_percentage
            FROM students st
            JOIN student_classes sc ON st.id = sc.student_id
            JOIN attendance a ON st.id = a.student_id
            WHERE sc.class_id = ? AND a.subject_id = ?
            GROUP BY st.id
            ORDER BY st.name
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classId);
            ps.setInt(2, subjectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                fullAttendanceReport.add(new Object[]{
                    rs.getString("name"),
                    rs.getInt("total_classes"),
                    rs.getInt("present_count"),
                    rs.getInt("absent_count"),
                    rs.getDouble("attendance_percentage")
                });
            }
        } catch (SQLException ex) {
            showError("Could not fetch full attendance report: " + ex.getMessage());
        }
        return fullAttendanceReport;
    }

    private JPanel makeReloadTab() {
        JPanel reloadPanel = new JPanel(new BorderLayout());
        reloadPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    
        JLabel reloadLabel = new JLabel("Click to reload the Teacher Dashboard", SwingConstants.CENTER);
        reloadLabel.setFont(reloadLabel.getFont().deriveFont(Font.BOLD, 18f));
        reloadPanel.add(reloadLabel, BorderLayout.CENTER);
    
        JButton reloadButton = new JButton("Reload");
        styleButton(reloadButton);
        reloadButton.addActionListener(e -> {
            // Dispose of the current dashboard and create a new one
            dispose();
            new TeacherDashboard(email); // Replace with the actual teacher's email
        });
        reloadPanel.add(reloadButton, BorderLayout.SOUTH);
    
        return reloadPanel;
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
    private JPanel makeLowAttendanceReportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
    
        JLabel title = new JLabel("Low Attendance Report (Below 75%)", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        panel.add(title, BorderLayout.NORTH);
    
        // Controls: class and subject selectors
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JComboBox<ClassItem> classBox = new JComboBox<>(
            fetchTeacherClasses().toArray(new ClassItem[0])
        );
        JComboBox<Subject> subjectBox = new JComboBox<>(
            fetchTeacherSubjects().toArray(new Subject[0])
        );
        controls.add(new JLabel("Class:"));
        controls.add(classBox);
        controls.add(new JLabel("Subject:"));
        controls.add(subjectBox);
        panel.add(controls, BorderLayout.NORTH);
    
        // Table to display low attendance students
        JTable reportTable = new JTable();
        panel.add(new JScrollPane(reportTable), BorderLayout.CENTER);
    
        // Action listener for class and subject selection
        ActionListener updateReport = e -> {
            ClassItem selectedClass = (ClassItem) classBox.getSelectedItem();
            Subject selectedSubject = (Subject) subjectBox.getSelectedItem();
            if (selectedClass != null && selectedSubject != null) {
                List<Object[]> data = fetchLowAttendanceStudents(selectedClass.id, selectedSubject.id);
                reportTable.setModel(new DefaultTableModel(
                    data.toArray(new Object[0][]),
                    new String[]{"Student Name", "Attendance %"}
                ));
            }
        };
        classBox.addActionListener(updateReport);
        subjectBox.addActionListener(updateReport);
    
        // Trigger initial population
        if (classBox.getItemCount() > 0 && subjectBox.getItemCount() > 0) {
            classBox.setSelectedIndex(0);
            subjectBox.setSelectedIndex(0);
        }
    
        return panel;
    }
    
    private List<Object[]> fetchLowAttendanceStudents(int classId, int subjectId) {
        List<Object[]> lowAttendanceStudents = new ArrayList<>();
        String sql = """
            SELECT st.name, 
                   ROUND((SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) * 100.0) / COUNT(a.status), 2) AS attendance_percentage
            FROM students st
            JOIN student_classes sc ON st.id = sc.student_id
            JOIN attendance a ON st.id = a.student_id
            WHERE sc.class_id = ? AND a.subject_id = ?
            GROUP BY st.id
            HAVING attendance_percentage < 75
            ORDER BY attendance_percentage ASC
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classId);
            ps.setInt(2, subjectId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                lowAttendanceStudents.add(new Object[]{
                    rs.getString("name"),
                    rs.getDouble("attendance_percentage")
                });
            }
        } catch (SQLException ex) {
            showError("Could not fetch low attendance students: " + ex.getMessage());
        }
        return lowAttendanceStudents;
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
        String[] columns = {
            "Day", "8:00 - 9:00", "9:00 - 10:00", "10:00 - 11:00",
            "11:00 - 12:00", "12:00 - 1:00", "1:00 - 2:00", "2:00 - 3:00"
        };
    
        List<Object[]> rows = new ArrayList<>();
        Map<String, Object[]> slotDetails = new HashMap<>(); // Key = "Day|Hour"
    
        // Initialize empty timetable for each day
        Map<String, String[]> timetableData = new HashMap<>();
        for (String day : new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"}) {
            timetableData.put(day, new String[7]); // 7 periods
        }
    
        String sql =
            "SELECT t.day, t.hour, c.id, c.name, s.id, s.name " +
            "FROM timetable t " +
            "JOIN classes c ON t.class_id = c.id " +
            "JOIN subjects s ON t.subject_id = s.id " +
            "WHERE t.teacher_id = ? " +
            "ORDER BY CASE t.day " +
            "    WHEN 'Monday' THEN 1 WHEN 'Tuesday' THEN 2 " +
            "    WHEN 'Wednesday' THEN 3 WHEN 'Thursday' THEN 4 ELSE 5 END, t.hour";
    
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String day = rs.getString("day");
                int hour = rs.getInt("hour") - 1; // Convert to 0-based index
                int classId = rs.getInt(3);
                String className = rs.getString(4);
                int subjectId = rs.getInt(5);
                String subject = rs.getString(6);
    
                if (hour >= 0 && hour < 7) {
                    timetableData.get(day)[hour] = className + " - " + subject;
                    slotDetails.put(day + "|" + hour, new Object[]{classId, subjectId, subject, className, hour});
                }
            }
        } catch (SQLException ex) {
            showError("Could not load timetable: " + ex.getMessage());
        }
    
        // Build the table data
        for (String day : new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"}) {
            Object[] row = new Object[columns.length];
            row[0] = day;
    
            String[] subjects = timetableData.get(day);
            for (int i = 0; i < 7; i++) {
                row[i + 1] = subjects[i] != null ? subjects[i] : "-";
            }
    
            rows.add(row);
        }
    
        Object[][] data = new Object[rows.size()][columns.length];
        for (int i = 0; i < rows.size(); i++) {
            data[i] = rows.get(i);
        }
    
        JTable table = new JTable(data, columns);
        table.setRowHeight(24);
    
        // Mouse listener to handle clicks on table cells
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = table.getSelectedRow();
                int col = table.getSelectedColumn();
    
                if (row >= 0 && col >= 1) { // Skip "Day" column
                    String day = (String) table.getValueAt(row, 0);
                    int hour = col - 1; // Convert to 0-based hour index
    
                    Object[] slot = slotDetails.get(day + "|" + hour);
                    if (slot != null) {
                        selClass = (int) slot[0];
                        selSubject = (int) slot[1];
                        selHour = (int) slot[4];
    
                        prepareAttendanceForm(
                            (String) slot[3], // class name
                            (String) slot[2], // subject name
                            day, selHour + 1 // hour back to 1-based
                        );
                    }
                }
            }
        });
    
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        p.add(new JLabel("Click a cell to mark attendance", SwingConstants.CENTER),
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
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
    
        JLabel title = new JLabel("Mark Attendance", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        panel.add(title, BorderLayout.NORTH);
    
        
        return panel;
    }
    private void prepareAttendanceForm(String className, String subjName, String day, int hour) {
        JPanel attPanel = (JPanel) tabs.getComponentAt(3);
        attPanel.removeAll();
        attPanel.setLayout(new BorderLayout(10, 10));
    
        // Date field
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.add(new JLabel("Date (YYYY-MM-DD):"));
    
        // Set current date as default
        String currentDate = LocalDate.now().toString();
        JTextField dateField = new JTextField(currentDate, 10);
    
        // Add "+" and "-" buttons for date adjustment
        JButton incrementDateBtn = new JButton("+");
        JButton decrementDateBtn = new JButton("-");

        JButton importButton = new JButton("Import Attendance from File");
        styleButton(importButton);
        importButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                processAttendanceFromFile(file);
            }
        });
    
        
    
        // Add action listeners for the buttons
        incrementDateBtn.addActionListener(e -> {
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            dateField.setText(date.plusDays(1).toString());
        });
    
        decrementDateBtn.addActionListener(e -> {
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            dateField.setText(date.minusDays(1).toString());
        });
    
        top.add(dateField);
        top.add(decrementDateBtn);
        top.add(incrementDateBtn);
        attPanel.add(top, BorderLayout.NORTH);
        top.add(importButton, BorderLayout.CENTER);
    
        // Header
        JLabel lbl = new JLabel(
            String.format("Mark Attendance: %s / %s — %s Hour %d", className, subjName, day, hour),
            SwingConstants.CENTER
        );
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 18f));
        attPanel.add(lbl, BorderLayout.CENTER);
    
        // Student rows
        attendanceMap = new LinkedHashMap<>();
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        String sql =
            "SELECT st.id, st.name " +
            "FROM students st JOIN student_classes sc ON st.id = sc.student_id " +
            "WHERE sc.class_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selClass);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int sid = rs.getInt(1);
                String nm = rs.getString(2);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
                row.add(new JLabel(nm));
                JComboBox<String> cb = new JComboBox<>(new String[]{"Present", "Absent"});
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
    
            public void insertUpdate(DocumentEvent e) {
                update();
            }
    
            public void removeUpdate(DocumentEvent e) {
                update();
            }
    
            public void changedUpdate(DocumentEvent e) {
                update();
            }
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

        JLabel title = new JLabel("Class / Student Attendance Report", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        p.add(title, BorderLayout.NORTH);

        // Controls: class, student, subject, from/to, gen, export
        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        // 1) Class selector
        JComboBox<ClassItem> classBox = new JComboBox<>(
            fetchTeacherClasses().toArray(new ClassItem[0])
        );

        // 2) Student selector (populated when class changes)
        JComboBox<Student> studentBox = new JComboBox<>();

        // 3) Subject selector
        JComboBox<Subject> subjectBox = new JComboBox<>(
            fetchTeacherSubjects().toArray(new Subject[0])
        );

        JTextField fromDate = new JTextField(8);
        JTextField toDate   = new JTextField(8);
        JButton gen        = new JButton("Generate");
        JButton exportCSV  = new JButton("Export CSV");

        ctrl.add(new JLabel("Class:"));   ctrl.add(classBox);
        ctrl.add(new JLabel("Student:")); ctrl.add(studentBox);
        ctrl.add(new JLabel("Subject:")); ctrl.add(subjectBox);
        ctrl.add(new JLabel("From:"));    ctrl.add(fromDate);
        ctrl.add(new JLabel("To:"));      ctrl.add(toDate);
        ctrl.add(gen);                    ctrl.add(exportCSV);

        p.add(ctrl, BorderLayout.WEST);

        JTable reportTable = new JTable();
        p.add(new JScrollPane(reportTable), BorderLayout.CENTER);

        // When class changes, refill students
        classBox.addActionListener(e -> {
            ClassItem cls = (ClassItem) classBox.getSelectedItem();
            if (cls != null) {
                List<Student> studs = fetchStudentsForClass(cls.id);
                studentBox.setModel(new DefaultComboBoxModel<>(studs.toArray(new Student[0])));
            }
        });
        // Trigger initial population
        if (classBox.getItemCount()>0) classBox.setSelectedIndex(0);

        gen.addActionListener(e -> {
            Student stu   = (Student) studentBox.getSelectedItem();
            Subject subj  = (Subject) subjectBox.getSelectedItem();
            String f      = fromDate.getText().trim();
            String t      = toDate.getText().trim();

            if (stu==null || subj==null) {
                showError("Please select both Class → Student and Subject");
                return;
            }

            StringBuilder sb = new StringBuilder(
                "SELECT date, hour, status FROM attendance " +
                "WHERE student_id=? AND subject_id=?"
            );
            if (f.matches("\\d{4}-\\d{2}-\\d{2}") && t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                sb.append(" AND date BETWEEN ? AND ?");
            }
            sb.append(" ORDER BY date, hour");

            List<Object[]> data = new ArrayList<>();
            int total=0, present=0;
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                ps.setInt(1, stu.id);
                ps.setInt(2, subj.id);
                if (sb.indexOf("BETWEEN")>0) {
                    ps.setString(3, f);
                    ps.setString(4, t);
                }
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    data.add(new Object[]{
                        rs.getString("date"),
                        rs.getInt("hour"),
                        rs.getString("status")
                    });
                    total++;
                    if ("Present".equals(rs.getString("status"))) present++;
                }
            } catch (SQLException ex) {
                showError("Report error: " + ex.getMessage());
            }

            reportTable.setModel(new DefaultTableModel(
                data.toArray(new Object[0][]),
                new String[]{"Date","Hour","Status"}
            ));

            double pct = total>0 ? (present*100.0/total) : 0;
            JOptionPane.showMessageDialog(p,
                String.format("%s’s attendance in %s: %.1f%% (%d/%d)",
                    stu.name, subj.name, pct, present, total),
                "Attendance %", JOptionPane.INFORMATION_MESSAGE);
        });

        exportCSV.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(p)==JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                if (!file.getName().endsWith(".csv"))
                    file = new File(file.getAbsolutePath()+".csv");
                try (PrintWriter pw = new PrintWriter(file)) {
                    TableModel m = reportTable.getModel();
                    // header
                    for (int i=0; i<m.getColumnCount(); i++) {
                        pw.print(m.getColumnName(i));
                        if (i<m.getColumnCount()-1) pw.print(",");
                    }
                    pw.println();
                    // rows
                    for (int r=0; r<m.getRowCount(); r++) {
                        for (int c=0; c<m.getColumnCount(); c++) {
                            Object v = m.getValueAt(r,c);
                            pw.print(v!=null?v.toString():"");
                            if (c<m.getColumnCount()-1) pw.print(",");
                        }
                        pw.println();
                    }
                    JOptionPane.showMessageDialog(p, "CSV exported successfully!");
                } catch (IOException ex) {
                    showError("CSV export failed: "+ex.getMessage());
                }
            }
        });

        return p;
    }
    // Fetch classes taught by this teacher
    private List<ClassItem> fetchTeacherClasses() {
        List<ClassItem> list = new ArrayList<>();
        String sql = "SELECT DISTINCT c.id, c.name FROM classes c " +
                     "JOIN timetable t ON c.id=t.class_id " +
                     "WHERE t.teacher_id=? ORDER BY c.name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new ClassItem(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            showError("Could not load classes: " + ex.getMessage());
        }
        return list;
    }

    // Fetch subjects taught by this teacher
    private List<Subject> fetchTeacherSubjects() {
        List<Subject> list = new ArrayList<>();
        String sql = "SELECT DISTINCT s.id, s.name FROM subjects s " +
                     "JOIN timetable t ON s.id=t.subject_id " +
                     "WHERE t.teacher_id=? ORDER BY s.name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Subject(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            showError("Could not load subjects: " + ex.getMessage());
        }
        return list;
    }

    // Fetch students in a given class
    private List<Student> fetchStudentsForClass(int classId) {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT st.id, st.name FROM students st " +
                     "JOIN student_classes sc ON st.id=sc.student_id " +
                     "WHERE sc.class_id=? ORDER BY st.name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, classId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Student(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            showError("Could not load students: " + ex.getMessage());
        }
        return list;
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

private List<Object[]> getStudentsForClass(int classId) {
    List<Object[]> students = new ArrayList<>();
    String sql = "SELECT st.id, st.name FROM students st JOIN student_classes sc ON st.id = sc.student_id WHERE sc.class_id = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, classId);
        ResultSet rs = ps.executeQuery();
        while (rs.next())
            students.add(new Object[]{ rs.getInt(1), rs.getString(2) });
    } catch (SQLException ex) {
        showError("Could not load students: " + ex.getMessage());
    }
    return students;
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

    private static class ClassItem {
        final int id; final String name;
        ClassItem(int i, String n){ id=i; name=n; }
        @Override public String toString(){ return name; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
            new TeacherDashboard("ravi@college.com")
        );
    }
}
