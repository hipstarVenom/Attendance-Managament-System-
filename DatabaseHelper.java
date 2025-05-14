import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseHelper {

    private static final String DB_URL = "jdbc:sqlite:college.db";

    // Method to initialize the database schema and insert some dummy data
    public static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // Students Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS students (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  name TEXT NOT NULL," +
                "  password TEXT NOT NULL," +
                "  email TEXT UNIQUE NOT NULL," +
                "  attendance_count INTEGER DEFAULT 0 CHECK(attendance_count >= 0)" +
                ");"
            );

            // Teachers Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS teachers (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  name TEXT NOT NULL," +
                "  email TEXT UNIQUE NOT NULL," +
                "  password TEXT NOT NULL" +
                ");"
            );

            // Subjects Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS subjects (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  name TEXT NOT NULL," +
                "  department TEXT NOT NULL," +
                "  teacher_id INTEGER," +
                "  FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE SET NULL" +
                ");"
            );

            // Classes Table
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS classes (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  name TEXT NOT NULL" +
                ");"
            );

            // Link: class → subjects
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS class_subjects (" +
                "  class_id INTEGER," +
                "  subject_id INTEGER," +
                "  PRIMARY KEY (class_id, subject_id)," +
                "  FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE" +
                ");"
            );

            // Link: student → class
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS student_classes (" +
                "  student_id INTEGER," +
                "  class_id INTEGER," +
                "  PRIMARY KEY (student_id, class_id)," +
                "  FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE" +
                ");"
            );

            // Attendance Table (with hour and Present/Absent status)
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS attendance (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  student_id INTEGER NOT NULL," +
                "  subject_id INTEGER NOT NULL," +
                "  date TEXT NOT NULL," +
                "  hour INTEGER NOT NULL CHECK(hour BETWEEN 1 AND 7)," +
                "  status TEXT CHECK(status IN ('Present', 'Absent')) NOT NULL," +
                "  FOREIGN KEY (student_id) REFERENCES students(id)," +
                "  FOREIGN KEY (subject_id) REFERENCES subjects(id)," +
                "  UNIQUE(student_id, subject_id, date, hour)" +
                ");"
            );

            // Timetable Table (including Friday)
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS timetable (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  class_id INTEGER NOT NULL," +
                "  subject_id INTEGER NOT NULL," +
                "  teacher_id INTEGER NOT NULL," +
                "  day TEXT NOT NULL," +
                "  hour INTEGER NOT NULL CHECK(hour BETWEEN 1 AND 7)," +
                "  FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE CASCADE" +
                ");"
            );

            // Dummy Teachers
            stmt.executeUpdate(
                "INSERT OR IGNORE INTO teachers (name, email, password) VALUES " +
                "('Ms. Anu', 'anu@college.com', 'anu123')," +
                "('Mr. Ravi', 'ravi@college.com', 'ravi123')," +
                "('Dr. Priya', 'priya@college.com', 'priya123')," +
                "('Ms. Divya', 'divya@college.com', 'divya123')," +
                "('Mr. Arjun', 'arjun@college.com', 'arjun123')," +
                "('Mr. John', 'john@college.com', 'john123');"
            );

            // Dummy Subjects
            stmt.executeUpdate(
                "INSERT OR IGNORE INTO subjects (name, department, teacher_id) VALUES " +
                "('Math', 'Engineering', 1)," +
                "('Science', 'Engineering', 2)," +
                "('English', 'Arts', 3)," +
                "('Data Structures', 'Computer Science', 4)," +
                "('Physics', 'Engineering', 5)," +
                "('Chemistry', 'Engineering', 6);"
            );

            // Dummy Classes
            stmt.executeUpdate(
                "INSERT OR IGNORE INTO classes (name) VALUES " +
                "('Class A'), ('Class B'), ('Class C');"
            );

            // Link class → subjects
            stmt.executeUpdate(
                "INSERT OR IGNORE INTO class_subjects (class_id, subject_id) VALUES " +
                "(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6)," +
                "(2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6)," +
                "(3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (3, 6);"
            );

            // Dummy Students
            stmt.executeUpdate(
                "INSERT OR IGNORE INTO students (name, password, email) VALUES " +
                "('Yuva', 'yuva123', 'yuva@example.com')," +
                "('Karthik', 'kart123', 'karthik@example.com')," +
                "('Sneha', 'sneh123', 'sneha@example.com')," +
                "('Raj', 'raj123', 'raj@example.com')," +
                "('Ravi', 'ravi123', 'ravi@example.com')," +
                "('Asha', 'asha123', 'asha@example.com')," +
                "('Amit', 'amit123', 'amit@example.com')," +
                "('Priya', 'priya123', 'priya@example.com')," +
                "('Neha', 'neha123', 'neha@example.com')," +
                "('Kiran', 'kiran123', 'kiran@example.com');"
            );

            // Link students to class
            stmt.executeUpdate(
                "INSERT OR IGNORE INTO student_classes (student_id, class_id) VALUES " +
                "(1, 1), (2, 1), (3, 1), (4, 2), (5, 2), (6, 2)," +
                "(7, 3), (8, 3), (9, 3), (10, 3);"
            );

            // Sample Timetable: Monday–Friday, hours 1–7
           // Sample Timetable: Monday–Friday, hours 1–7
    stmt.executeUpdate(
    "INSERT OR IGNORE INTO timetable (class_id, subject_id, teacher_id, day, hour) VALUES " +
    "(1, 1, 1, 'Monday', 1), (1, 2, 2, 'Monday', 2), (1, 3, 3, 'Monday', 3), (1, 4, 4, 'Monday', 4), (1, 5, 5, 'Monday', 5), (1, 6, 6, 'Monday', 6), (1, 1, 1, 'Monday', 7)," +
    "(1, 1, 1, 'Tuesday', 1), (1, 2, 2, 'Tuesday', 2), (1, 3, 3, 'Tuesday', 3), (1, 4, 4, 'Tuesday', 4), (1, 5, 5, 'Tuesday', 5), (1, 6, 6, 'Tuesday', 6), (1, 1, 1, 'Tuesday', 7)," +
    "(1, 1, 1, 'Wednesday', 1), (1, 2, 2, 'Wednesday', 2), (1, 3, 3, 'Wednesday', 3), (1, 4, 4, 'Wednesday', 4), (1, 5, 5, 'Wednesday', 5), (1, 6, 6, 'Wednesday', 6), (1, 1, 1, 'Wednesday', 7)," +
    "(1, 1, 1, 'Thursday', 1), (1, 2, 2, 'Thursday', 2), (1, 3, 3, 'Thursday', 3), (1, 4, 4, 'Thursday', 4), (1, 5, 5, 'Thursday', 5), (1, 6, 6, 'Thursday', 6), (1, 1, 1, 'Thursday', 7)," +
    "(1, 1, 1, 'Friday', 1), (1, 2, 2, 'Friday', 2), (1, 3, 3, 'Friday', 3), (1, 4, 4, 'Friday', 4), (1, 5, 5, 'Friday', 5), (1, 6, 6, 'Friday', 6), (1, 1, 1, 'Friday', 7);"
    );

            System.out.println("Database initialized with updated schema and data.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void insertTestAttendance() {
    String dbUrl = DB_URL;
    String[] dates = {
        "2025-04-01", "2025-04-02",
        "2025-04-03", "2025-04-04", "2025-04-05"
    };
    String[] statuses = {"Present", "Absent"};

    // SQL to fetch the timetable details
    String fetchTimetableSql =
        "SELECT class_id, subject_id, hour " +
        "FROM timetable";

    // SQL to fetch students for a specific class
    String fetchStudentsSql =
        "SELECT student_id FROM student_classes WHERE class_id = ?";

    // SQL to insert attendance data
    String insertSql =
        "INSERT OR IGNORE INTO attendance " +
        "(student_id, subject_id, date, hour, status) " +
        "VALUES (?, ?, ?, ?, ?)";

    try (Connection conn = DriverManager.getConnection(dbUrl);
         PreparedStatement fetchTimetable = conn.prepareStatement(fetchTimetableSql);
         PreparedStatement fetchStudents  = conn.prepareStatement(fetchStudentsSql);
         PreparedStatement insertStmt     = conn.prepareStatement(insertSql)) {

        // 1) Load all timetable entries
        List<int[]> timetable = new ArrayList<>();
        try (ResultSet rs = fetchTimetable.executeQuery()) {
            while (rs.next()) {
                int classId   = rs.getInt("class_id");
                int subjectId = rs.getInt("subject_id");
                int hour      = rs.getInt("hour");
                timetable.add(new int[]{classId, subjectId, hour});
            }
        }

        // 2) For each timetable entry, find students in that class
        Map<Integer, List<Integer>> classToStudents = new HashMap<>();
        for (int[] entry : timetable) {
            int classId = entry[0];
            if (!classToStudents.containsKey(classId)) {
                fetchStudents.setInt(1, classId);
                List<Integer> students = new ArrayList<>();
                try (ResultSet rs = fetchStudents.executeQuery()) {
                    while (rs.next()) {
                        students.add(rs.getInt("student_id"));
                    }
                }
                classToStudents.put(classId, students);
            }
        }

        // 3) For each date, timetable entry, and student, insert attendance
        for (String date : dates) {
            for (int[] entry : timetable) {
                int classId   = entry[0];
                int subjectId = entry[1];
                int hour      = entry[2];
                List<Integer> students = classToStudents.getOrDefault(classId, List.of());
                for (int sid : students) {
                    // alternate status for variety
                    String status = statuses[(sid + subjectId + hour) % statuses.length];
                    insertStmt.setInt(1, sid);
                    insertStmt.setInt(2, subjectId);
                    insertStmt.setString(3, date);
                    insertStmt.setInt(4, hour);
                    insertStmt.setString(5, status);
                    insertStmt.executeUpdate();
                }
            }
        }

        System.out.println("Test attendance inserted according to schema relationships.");
    } catch (SQLException e) {
        e.printStackTrace();
    }
}


}
