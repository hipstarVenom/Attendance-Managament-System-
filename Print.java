import java.sql.*;

public class Print {

    private static final String DB_URL = "jdbc:sqlite:college.db";

    public static void printAllTables() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                System.out.println("\n=== TABLE: " + tableName + " ===");

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

                    ResultSetMetaData rsMeta = rs.getMetaData();
                    int columnCount = rsMeta.getColumnCount();

                    // Print column headers
                    for (int i = 1; i <= columnCount; i++) {
                        System.out.print(rsMeta.getColumnName(i) + "\t");
                    }
                    System.out.println();

                    // Print rows
                    while (rs.next()) {
                        for (int i = 1; i <= columnCount; i++) {
                            System.out.print(rs.getString(i) + "\t");
                        }
                        System.out.println();
                    }

                } catch (SQLException e) {
                    System.out.println("Error reading table " + tableName);
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Test it
    public static void main(String[] args) {
        printAllTables();
    }
}
