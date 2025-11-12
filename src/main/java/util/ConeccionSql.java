package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import helpers.MensajeHelper;

public class ConeccionSql {
    
    private static final String URL = "jdbc:mysql://192.168.1.100:3306/OPEN_CASH?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
     private static final String USER = "juan75";
    private static final String PASSWORD = "6666";
    
    /* 
    private static final String URL = "jdbc:mysql://localhost:3306/OPEN_CASH?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "1212";

    */

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            MensajeHelper.info("MySQL Driver cargado correctamente ✅");
        } catch (ClassNotFoundException e) {
            MensajeHelper.errorDialog("Error al iniciar el sistema. Contacte con su técnico.");
            MensajeHelper.error("Error al cargar el driver MySQL", e);
        }
    }

    public static Connection getConnection() {
        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            MensajeHelper.info("Conexión exitosa con la base de datos.");
            return conn;
        } catch (SQLException e) {
            MensajeHelper.errorDialog(
                    "⚠️ No se pudo conectar con la base de datos.\nPor favor, contacte con su técnico o administrador del sistema.");
            MensajeHelper.error("Fallo al conectar a la base de datos", e);
            return null;
        }
    }

    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                    MensajeHelper.info("Conexión cerrada correctamente.");
                }
            } catch (SQLException e) {
                MensajeHelper.errorDialog("Ocurrió un error al cerrar la conexión. Contacte con su técnico.");
                MensajeHelper.error("Error al cerrar la conexión", e);
            }
        }
    }
}
