import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.*;
import java.util.Vector;
import java.util.List;

public class AdminDashboard extends JFrame {
    private static final String DB_URL = "jdbc:sqlite:college.db";
    private Connection conn;
    private JTabbedPane tabs;

    public AdminDashboard() {
        super("Admin Dashboard");
        initDB();
        initUI();
    }

    private void initDB() {
        try {
            conn = DriverManager.getConnection(DB_URL);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "DB connection failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void initUI() {
        try { UIManager.setLookAndFeel(new FlatLightLaf()); } catch (Exception ignored) {}
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(10,10,10,10));
        tabs.addTab("Home",                makeHomePanel());
        tabs.addTab("Add/Delete Student", makeAddDeleteStudentPanel());
        tabs.addTab("View Students",      makeViewStudentsPanel());
        tabs.addTab("Add/Delete Teacher", makeAddDeleteTeacherPanel());
        tabs.addTab("View Teachers",      makeViewTeachersPanel());
        tabs.addTab("Add/Delete Class",   makeAddDeleteClassPanel());
        tabs.addTab("View Classes",       makeViewClassesPanel());
        tabs.addTab("Add/Delete Subject", makeAddDeleteSubjectPanel());
        tabs.addTab("View Subjects", makeViewSubjectsPanel());
        tabs.addTab("Generate Report",    makeReportPanel());
        tabs.addTab("Logout",             makeLogoutPanel());

        getContentPane().add(tabs, BorderLayout.CENTER);
        setVisible(true);
    }

    private JPanel makeHomePanel() {
        JPanel p = new JPanel(new BorderLayout());
        JLabel lbl = new JLabel("Welcome, Admin", SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 28f));
        p.add(lbl, BorderLayout.CENTER);
        return p;
    }
    private void addTeacherToSubject(int subjectId, int teacherId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE subjects SET teacher_id = ? WHERE id = ?")) {
            ps.setInt(1, teacherId);
            ps.setInt(2, subjectId);
            ps.executeUpdate();
        }
    }

    private void loadSubjectsForView(DefaultTableModel model) {
        model.setRowCount(0);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, department, teacher_id FROM subjects")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("department"),
                        rs.getInt("teacher_id")
                });
            }
        } catch (SQLException ex) {
            showError(ex.getMessage());
        }
    }
  // ---- Add/Delete Student ----
    private JPanel makeAddDeleteStudentPanel() {
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name","Email"},0);
        JTable table = new JTable(model);

        // Form
        JPanel form = new JPanel(new GridLayout(0,2,5,5));
        JTextField nameF = new JTextField(), emailF = new JTextField();
        JPasswordField pwdF = new JPasswordField();
        form.add(new JLabel("Name:")); form.add(nameF);
        form.add(new JLabel("Email:")); form.add(emailF);
        form.add(new JLabel("Password:")); form.add(pwdF);

        JButton add = new JButton("Add"); style(add);
        add.addActionListener(e -> {
            String n = nameF.getText(), em = emailF.getText(), pw = new String(pwdF.getPassword());
            if (n.isBlank() || em.isBlank() || pw.isBlank()) {
                showError("All fields are required.");
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO students(name,email,password) VALUES(?,?,?)")) {
                ps.setString(1,n); ps.setString(2,em); ps.setString(3,pw);
                ps.executeUpdate();
                loadStudents(model);
                nameF.setText(""); emailF.setText(""); pwdF.setText("");
            } catch (SQLException ex) { showError(ex.getMessage()); }
        });

        // Delete
        JButton del = new JButton("Delete"); style(del);
        del.addActionListener(e -> {
            int r = table.getSelectedRow(); if (r < 0) return;
            int id = (int)model.getValueAt(r,0);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM students WHERE id=?")) {
                ps.setInt(1,id); ps.executeUpdate();
                model.removeRow(r);
            } catch(SQLException ex){ showError(ex.getMessage()); }
        });

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadStudents(model));

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(add, BorderLayout.EAST);

        JPanel bot = new JPanel(new BorderLayout());
        JPanel btns = new JPanel(); btns.add(del); btns.add(refresh);
        bot.add(new JScrollPane(table), BorderLayout.CENTER);
        bot.add(btns, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(new EmptyBorder(10,10,10,10));
        p.add(top, BorderLayout.NORTH);
        p.add(bot, BorderLayout.CENTER);
        loadStudents(model);
        return p;
    }

    private void loadStudents(DefaultTableModel m) {
        m.setRowCount(0);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT s.id, s.name, s.email, c.name AS class_name " +
                 "FROM students s " +
                 "LEFT JOIN student_classes sc ON s.id = sc.student_id " +
                 "LEFT JOIN classes c ON sc.class_id = c.id")) {
            while (rs.next()) {
                m.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("class_name") != null ? rs.getString("class_name") : "No Class"
                });
            }
        } catch (SQLException ex) {
            showError(ex.getMessage());
        }
    }
    
    private JPanel makeViewStudentsPanel() {
        DefaultTableModel m = new DefaultTableModel(new String[]{"ID", "Name", "Email", "Class"}, 0);
        JTable t = new JTable(m);
        JButton r = new JButton("Refresh");
        r.addActionListener(e -> loadStudents(m));
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        p.add(r, BorderLayout.SOUTH);
        loadStudents(m);
        return p;
    }

    // ---- Add/Delete Teacher ----
    private JPanel makeAddDeleteTeacherPanel() {
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name","Email"},0);
        JTable table = new JTable(model);

        JPanel form = new JPanel(new GridLayout(0,2,5,5));
        JTextField nameF = new JTextField(), emailF = new JTextField();
        JPasswordField pwdF = new JPasswordField();
        form.add(new JLabel("Name:")); form.add(nameF);
        form.add(new JLabel("Email:")); form.add(emailF);
        form.add(new JLabel("Password:")); form.add(pwdF);

        JButton add = new JButton("Add"); style(add);
        add.addActionListener(e -> {
            String n=nameF.getText(), em=emailF.getText(), pw=new String(pwdF.getPassword());
            if(n.isBlank()||em.isBlank()||pw.isBlank()){ showError("All required"); return; }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO teachers(name,email,password) VALUES(?,?,?)")) {
                ps.setString(1,n); ps.setString(2,em); ps.setString(3,pw);
                ps.executeUpdate();
                loadTeachers(model);
            } catch(SQLException ex){ showError(ex.getMessage()); }
        });

        JButton del = new JButton("Delete"); style(del);
        del.addActionListener(e -> {
            int r=table.getSelectedRow(); if(r<0) return;
            int id=(int)model.getValueAt(r,0);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM teachers WHERE id=?")) {
                ps.setInt(1,id); ps.executeUpdate(); model.removeRow(r);
            } catch(SQLException ex){ showError(ex.getMessage()); }
        });

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadTeachers(model));

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(add, BorderLayout.EAST);

        JPanel bot = new JPanel(new BorderLayout());
        JPanel btns = new JPanel(); btns.add(del); btns.add(refresh);
        bot.add(new JScrollPane(table), BorderLayout.CENTER);
        bot.add(btns, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(new EmptyBorder(10,10,10,10));
        p.add(top, BorderLayout.NORTH);
        p.add(bot, BorderLayout.CENTER);
        loadTeachers(model);
        return p;
    }


    private void loadTeachers(DefaultTableModel m) {
        m.setRowCount(0);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT DISTINCT t.id AS teacher_id, t.name AS teacher_name, t.email AS teacher_email, " +
                 "c.name AS class_name, s.name AS subject_name " +
                 "FROM teachers t " +
                 "LEFT JOIN timetable tt ON t.id = tt.teacher_id " +
                 "LEFT JOIN classes c ON tt.class_id = c.id " +
                 "LEFT JOIN subjects s ON tt.subject_id = s.id " +
                 "ORDER BY t.id, c.name, s.name")) {
            while (rs.next()) {
                m.addRow(new Object[]{
                    rs.getInt("teacher_id"),
                    rs.getString("teacher_name"),
                    rs.getString("teacher_email"),
                    rs.getString("class_name") != null ? rs.getString("class_name") : "No Class",
                    rs.getString("subject_name") != null ? rs.getString("subject_name") : "No Subject"
                });
            }
        } catch (SQLException ex) {
            showError(ex.getMessage());
        }
    }
    private JPanel makeViewTeachersPanel() {
        DefaultTableModel m = new DefaultTableModel(new String[]{"ID", "Name", "Email", "Class", "Subject"}, 0);
        JTable t = new JTable(m);
        JButton r = new JButton("Refresh");
        r.addActionListener(e -> loadTeachers(m));
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        p.add(r, BorderLayout.SOUTH);
        loadTeachers(m);
        return p;
    }

    // ---- Add/Delete Class ----
    private JPanel makeAddDeleteClassPanel() {
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID","Name"},0);
        JTable table = new JTable(model);

        JPanel form = new JPanel(new BorderLayout(5,5));
        JTextField nameF = new JTextField();
        form.add(new JLabel("Class Name:"), BorderLayout.WEST);
        form.add(nameF, BorderLayout.CENTER);

        JButton add = new JButton("Add"); style(add);
        add.addActionListener(e -> {
            String n = nameF.getText();
            if (n.isBlank()) { showError("Name required"); return; }
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO classes(name) VALUES(?)")) {
                ps.setString(1,n); ps.executeUpdate(); loadClasses(model);
            } catch(SQLException ex){ showError(ex.getMessage()); }
        });

        JButton del = new JButton("Delete"); style(del);
        del.addActionListener(e -> {
            int r = table.getSelectedRow(); if(r<0) return;
            int id = (int)model.getValueAt(r,0);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM classes WHERE id=?")) {
                ps.setInt(1,id); ps.executeUpdate(); model.removeRow(r);
            } catch(SQLException ex){ showError(ex.getMessage()); }
        });

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadClasses(model));

        JPanel top = form;
        JPanel bot = new JPanel(new BorderLayout());
        JPanel btns = new JPanel(); btns.add(del); btns.add(refresh);
        bot.add(new JScrollPane(table), BorderLayout.CENTER);
        bot.add(btns, BorderLayout.SOUTH);

        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(new EmptyBorder(10,10,10,10));
        p.add(top, BorderLayout.NORTH);
        p.add(bot, BorderLayout.CENTER);
        loadClasses(model);
        return p;
    }

    private void loadClasses(DefaultTableModel m) {
        m.setRowCount(0);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id,name FROM classes")) {
            while (rs.next()) m.addRow(new Object[]{rs.getInt(1),rs.getString(2)});
        } catch(SQLException ex){ showError(ex.getMessage()); }
    }

    private JPanel makeViewClassesPanel() {
        DefaultTableModel classModel = new DefaultTableModel(new String[]{"ID", "Name"}, 0);
        JTable classTable = new JTable(classModel);
        JButton refresh = new JButton("Refresh");
        JButton viewDetails = new JButton("View Details");
    
        refresh.addActionListener(e -> loadClasses(classModel));
    
        viewDetails.addActionListener(e -> {
            int selectedRow = classTable.getSelectedRow();
            if (selectedRow < 0) {
                showError("Please select a class to view details.");
                return;
            }
            int classId = (int) classModel.getValueAt(selectedRow, 0);
            String className = (String) classModel.getValueAt(selectedRow, 1);
            showClassDetails(classId, className);
        });
    
        JPanel btnPanel = new JPanel();
        btnPanel.add(refresh);
        btnPanel.add(viewDetails);
    
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(classTable), BorderLayout.CENTER);
        p.add(btnPanel, BorderLayout.SOUTH);
    
        loadClasses(classModel);
        return p;
    }
    
    private void showClassDetails(int classId, String className) {
        JDialog dialog = new JDialog(this, "Class Details - " + className, true);
        dialog.setSize(600, 400);
        dialog.setLayout(new BorderLayout());
    
        // Students Table
        DefaultTableModel studentModel = new DefaultTableModel(new String[]{"ID", "Name", "Email"}, 0);
        JTable studentTable = new JTable(studentModel);
        loadClassStudents(classId, studentModel);
    
        // Add and Delete Buttons
        JButton addStudent = new JButton("Add Student");
        JButton deleteStudent = new JButton("Delete Student");
        JButton importStudents = new JButton("Import Students from Text File");
    
        // Add Student
        addStudent.addActionListener(e -> {
            String studentId = JOptionPane.showInputDialog(dialog, "Enter Student ID:");
            if (studentId != null && !studentId.isBlank()) {
                try {
                    addStudentToClass(classId, Integer.parseInt(studentId));
                    loadClassStudents(classId, studentModel);
                } catch (NumberFormatException ex) {
                    showError("Invalid Student ID.");
                }
            }
        });
    
        // Delete Student
        deleteStudent.addActionListener(e -> {
            int selectedRow = studentTable.getSelectedRow();
            if (selectedRow < 0) {
                showError("Please select a student to delete.");
                return;
            }
            int studentId = (int) studentModel.getValueAt(selectedRow, 0);
            deleteStudentFromClass(classId, studentId);
            loadClassStudents(classId, studentModel);
        });
    
        // Import Students from Text File
        importStudents.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(dialog);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                importStudentsFromTextFile(file, classId, studentModel);
            }
        });
    
        JPanel btnPanel = new JPanel();
        btnPanel.add(addStudent);
        btnPanel.add(deleteStudent);
        btnPanel.add(importStudents);
    
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Students", new JScrollPane(studentTable));    
        dialog.add(tabs, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }


    private void importStudentsFromTextFile(File file, int classId, DefaultTableModel model) {
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length != 3) {
                showError("Invalid line format: " + line);
                continue;
            }

            int studentId = Integer.parseInt(parts[0].trim());
            String name = parts[1].trim();
            String email = parts[2].trim();

            // Insert student into the database
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO students (id, name, email) VALUES (?, ?, ?)")) {
                ps.setInt(1, studentId);
                ps.setString(2, name);
                ps.setString(3, email);
                ps.executeUpdate();
            }

            // Link student to the class
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO student_classes (student_id, class_id) VALUES (?, ?)")) {
                ps.setInt(1, studentId);
                ps.setInt(2, classId);
                ps.executeUpdate();
            }
        }

        // Refresh the student table
        loadClassStudents(classId, model);
        JOptionPane.showMessageDialog(this, "Students imported successfully!");

    } catch (Exception ex) {
        showError("Failed to import students: " + ex.getMessage());
    }
}
    private void deleteStudentFromClass(int classId, int studentId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM student_classes WHERE class_id = ? AND student_id = ?")) {
            ps.setInt(1, classId);
            ps.setInt(2, studentId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            showError(ex.getMessage());
        }
    }


    private void loadClassStudents(int classId, DefaultTableModel model) {
        model.setRowCount(0);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT s.id, s.name, s.email FROM students s " +
                "JOIN student_classes sc ON s.id = sc.student_id " +
                "WHERE sc.class_id = ?")) {
            ps.setInt(1, classId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email")
                    });
                }
            }
        } catch (SQLException ex) {
            showError(ex.getMessage());
        }
    }
    
    
    private void addStudentToClass(int classId, int studentId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO student_classes (student_id, class_id) VALUES (?, ?)")) {
            ps.setInt(1, studentId);
            ps.setInt(2, classId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            showError(ex.getMessage());
        }
    }

    private JPanel makeAddDeleteSubjectPanel() {
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Name", "Department"}, 0);
        JTable table = new JTable(model);
    
        // Form for adding a subject
        JPanel form = new JPanel(new GridLayout(0, 2, 5, 5));
        JTextField nameF = new JTextField(), departmentF = new JTextField();
        form.add(new JLabel("Subject Name:")); form.add(nameF);
        form.add(new JLabel("Department:")); form.add(departmentF);
    
        // Add Subject Button
        JButton add = new JButton("Add Subject");
        style(add);
        add.addActionListener(e -> {
            String name = nameF.getText();
            String department = departmentF.getText();
    
            if (name.isBlank() || department.isBlank()) {
                showError("All fields are required.");
                return;
            }
    
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO subjects (name, department) VALUES (?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, department);
                ps.executeUpdate();
                loadSubjects(model);
                nameF.setText(""); departmentF.setText("");
            } catch (SQLException ex) {
                showError(ex.getMessage());
            }
        });
    
        // Delete Subject Button
        JButton delete = new JButton("Delete Subject");
        style(delete);
        delete.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow < 0) {
                showError("Please select a subject to delete.");
                return;
            }
    
            int subjectId = (int) model.getValueAt(selectedRow, 0);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM subjects WHERE id = ?")) {
                ps.setInt(1, subjectId);
                ps.executeUpdate();
                loadSubjects(model);
            } catch (SQLException ex) {
                showError(ex.getMessage());
            }
        });
    
        // Refresh Button
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadSubjectsForView(model));
    
        // Layout
        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.CENTER);
        top.add(add, BorderLayout.EAST);
    
        JPanel bottom = new JPanel(new BorderLayout());
        JPanel buttons = new JPanel();
        buttons.add(delete);
        buttons.add(refresh);
        bottom.add(new JScrollPane(table), BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);
    
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(top, BorderLayout.NORTH);
        panel.add(bottom, BorderLayout.CENTER);
    
        loadSubjects(model);
        return panel;
    }
    private JPanel makeViewSubjectsPanel() {
        DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Name", "Department", "Teacher ID"}, 0);
        JTable table = new JTable(model);
    
        // Refresh Button
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> loadSubjects(model));
    
        // Add Teacher to Subject Button
        JButton addTeacher = new JButton("Add Teacher to Subject");
        addTeacher.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            if (selectedRow < 0) {
                showError("Please select a subject to add a teacher.");
                return;
            }
    
            int subjectId = (int) model.getValueAt(selectedRow, 0);
            String teacherId = JOptionPane.showInputDialog(this, "Enter Teacher ID:");
            if (teacherId != null && !teacherId.isBlank()) {
                try {
                    addTeacherToSubject(subjectId, Integer.parseInt(teacherId));
                    loadSubjects(model);
                } catch (NumberFormatException ex) {
                    showError("Invalid Teacher ID.");
                } catch (SQLException ex) {
                    showError(ex.getMessage());
                }
            }
        });
    
        // Layout
        JPanel btnPanel = new JPanel();
        btnPanel.add(refresh);
        btnPanel.add(addTeacher);
    
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);
    
        loadSubjects(model);
        return panel;
    }
    private void loadSubjects(DefaultTableModel model) {
        model.setRowCount(0);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, department, teacher_id FROM subjects")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("department"),
                        rs.getInt("teacher_id")
                });
            }
        } catch (SQLException ex) {
            showError(ex.getMessage());
        }
    }

    // ---- Generate Report ----
    private JPanel makeReportPanel() {
        JPanel p = new JPanel(new BorderLayout(10,10));
        p.setBorder(new EmptyBorder(10,10,10,10));

        JLabel lbl = new JLabel("Attendance Report", SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD,22f));
        p.add(lbl, BorderLayout.NORTH);

        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT,10,10));
        JComboBox<String> studentBox = new JComboBox<>(loadList("students","name"));
        JComboBox<String> classBox   = new JComboBox<>(loadList("classes","name"));
        JComboBox<String> subjectBox = new JComboBox<>(loadList("subjects","name"));
        JTextField from = new JTextField(8), to = new JTextField(8);
        JCheckBox lock = new JCheckBox("Lock Filters");
        JButton gen = new JButton("Generate"), exp = new JButton("Export");
        exp.setEnabled(false);

        // reload subjects when student or class changes
        ActionListener reloadSubjects = e -> {
            String stu = (String)studentBox.getSelectedItem();
            String cls = (String)classBox.getSelectedItem();
            Vector<String> subs = new Vector<>();
            subs.add("— All —");
            try (PreparedStatement ps = constructSubjectFilterQuery(stu, cls)) {
                int idx = 1;
                if (!"— All —".equals(stu)) ps.setString(idx++, stu);
                if (!"— All —".equals(cls)) ps.setString(idx++, cls);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) subs.add(rs.getString(1));
            } catch (SQLException ex) {
                showError("Could not load subjects: " + ex.getMessage());
            }
            subjectBox.setModel(new DefaultComboBoxModel<>(subs.toArray(new String[0])));
        };
        studentBox.addActionListener(reloadSubjects);
        classBox.addActionListener(reloadSubjects);

        lock.addActionListener(e -> {
            boolean l = lock.isSelected();
            studentBox.setEnabled(!l);
            classBox.setEnabled(!l);
            subjectBox.setEnabled(!l);
            from.setEnabled(!l);
            to.setEnabled(!l);
        });

        ctrl.add(new JLabel("Student:"));   ctrl.add(studentBox);
        ctrl.add(new JLabel("Class:"));     ctrl.add(classBox);
        ctrl.add(new JLabel("Subject:"));   ctrl.add(subjectBox);
        ctrl.add(new JLabel("From:"));      ctrl.add(from);
        ctrl.add(new JLabel("To:"));        ctrl.add(to);
        ctrl.add(lock);                     ctrl.add(gen);
        ctrl.add(exp);
        p.add(ctrl, BorderLayout.WEST);

        DefaultTableModel m = new DefaultTableModel(
            new String[]{"Student","Class","Subject","Date","Hour","Status"},0
        );
        JTable t = new JTable(m);
        p.add(new JScrollPane(t), BorderLayout.CENTER);

        gen.addActionListener(e -> {
            m.setRowCount(0);
            StringBuilder sql = new StringBuilder(
                "SELECT st.name,cl.name,s.name,a.date,a.hour,a.status " +
                "FROM attendance a " +
                "JOIN students st ON a.student_id=st.id " +
                "JOIN student_classes sc ON st.id=sc.student_id " +
                "JOIN classes cl ON sc.class_id=cl.id " +
                "JOIN subjects s ON a.subject_id=s.id " +
                "WHERE 1=1"
            );
            Vector<Object> params = new Vector<>();
            if (studentBox.getSelectedIndex() > 0) {
                sql.append(" AND st.name = ?");
                params.add(studentBox.getSelectedItem());
            }
            if (classBox.getSelectedIndex() > 0) {
                sql.append(" AND cl.name = ?");
                params.add(classBox.getSelectedItem());
            }
            if (subjectBox.getSelectedIndex() > 0) {
                sql.append(" AND s.name = ?");
                params.add(subjectBox.getSelectedItem());
            }
            if (from.getText().matches("\\d{4}-\\d{2}-\\d{2}")) {
                sql.append(" AND a.date >= ?");
                params.add(from.getText());
            }
            if (to.getText().matches("\\d{4}-\\d{2}-\\d{2}")) {
                sql.append(" AND a.date <= ?");
                params.add(to.getText());
            }
            sql.append(" ORDER BY a.date,a.hour");

            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i=0; i<params.size(); i++) ps.setObject(i+1, params.get(i));
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    m.addRow(new Object[]{
                        rs.getString(1),rs.getString(2),rs.getString(3),
                        rs.getString(4),rs.getInt(5),rs.getString(6)
                    });
                }
                exp.setEnabled(m.getRowCount()>0);
            } catch(SQLException ex){ showError(ex.getMessage()); }
        });

        exp.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(p)==JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                if (!f.getName().endsWith(".csv")) f = new File(f.getAbsolutePath()+".csv");
                try (FileWriter fw = new FileWriter(f)) {
                    for (int c=0; c<m.getColumnCount(); c++) {
                        fw.append(m.getColumnName(c));
                        if (c<m.getColumnCount()-1) fw.append(',');
                    }
                    fw.append('\n');
                    for (int r=0; r<m.getRowCount(); r++) {
                        for (int c=0; c<m.getColumnCount(); c++) {
                            fw.append(m.getValueAt(r,c).toString());
                            if (c<m.getColumnCount()-1) fw.append(',');
                        }
                        fw.append('\n');
                    }
                    JOptionPane.showMessageDialog(this, "Exported to " + f.getName());
                } catch (Exception ex) { showError(ex.getMessage()); }
            }
        });

        return p;
    }

    /**
 * Builds a PreparedStatement to fetch classes filtered by student name and/or subject name.
 * If the student parameter is not “— All —” it adds `AND st.name = ?`
 * If the subject parameter is not “— All —” it adds `AND s.name = ?`
 */
private PreparedStatement constructClassFilterQuery(String stu, String subj) throws SQLException {
    StringBuilder sql = new StringBuilder(
        "SELECT DISTINCT c.name\n" +
        "FROM classes c\n" +
        "JOIN student_classes sc ON c.id = sc.class_id\n" +
        "LEFT JOIN students st       ON sc.student_id = st.id\n" +
        "JOIN class_subjects cs      ON c.id = cs.class_id\n" +
        "LEFT JOIN subjects s        ON cs.subject_id = s.id\n" +
        "WHERE 1=1\n"
    );

    if (!"— All —".equals(stu)) {
        sql.append("  AND st.name = ?\n");
    }
    if (!"— All —".equals(subj)) {
        sql.append("  AND s.name = ?\n");
    }
    sql.append("ORDER BY c.name;");

    PreparedStatement ps = conn.prepareStatement(sql.toString());
    int idx = 1;
    if (!"— All —".equals(stu)) {
        ps.setString(idx++, stu);
    }
    if (!"— All —".equals(subj)) {
        ps.setString(idx++, subj);
    }
    return ps;
}


    private PreparedStatement constructSubjectFilterQuery(String stu, String cls) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT s.name " +
            "FROM subjects s " +
            "JOIN class_subjects cs ON s.id=cs.subject_id " +
            "JOIN classes c ON cs.class_id=c.id " +
            "LEFT JOIN student_classes sc ON sc.class_id=c.id " +
            "LEFT JOIN students st ON sc.student_id=st.id " +
            "WHERE 1=1"
        );
        if (!"— All —".equals(stu)) sql.append(" AND st.name = ?");
        if (!"— All —".equals(cls)) sql.append(" AND c.name = ?");
        sql.append(" ORDER BY s.name");
        return conn.prepareStatement(sql.toString());
    }

    private String[] loadList(String table, String col) {
        Vector<String> v = new Vector<>();
        v.add("— All —");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + col + " FROM " + table + " ORDER BY " + col)) {
            while (rs.next()) v.add(rs.getString(1));
        } catch (SQLException ignored) {}
        return v.toArray(new String[0]);
    }

    private JPanel makeLogoutPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JButton b = new JButton("Logout"); style(b);
        b.addActionListener(e -> {
                dispose();
                new LoginForm(); 
        });
        p.add(b, BorderLayout.CENTER);
        return p;
    }

    private void style(JButton b) {
        b.setBackground(new Color(33,150,243));
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI",Font.BOLD,14));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(8,16,8,16));
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){ b.setBackground(new Color(30,136,229)); }
            public void mouseExited(MouseEvent e){ b.setBackground(new Color(33,150,243)); }
        });
    }

    private void showError(String m) {
        JOptionPane.showMessageDialog(this, m, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AdminDashboard::new);
    }
}
