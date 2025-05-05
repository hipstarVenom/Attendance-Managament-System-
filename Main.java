import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // 1) Initialize the database schema (will create tables and insert dummy data if not present)
        DatabaseHelper.initializeDatabase();

        // 2) Seed test attendance data (Present/Absent) for development/testing
        DatabaseHelper.insertTestAttendance();

        // 3) Launch the LoginForm (which applies FlatLightLaf and shows the UI)
        SwingUtilities.invokeLater(() -> {
            LoginForm.initialize();
        });
    }
}
