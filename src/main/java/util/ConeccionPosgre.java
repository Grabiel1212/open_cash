
package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConeccionPosgre {

    private static final String URL = "jdbc:postgresql://localhost:5432/bd_dosar_v1_0";

    private static final String USUARIO = "postgres";

    private static final String PASSWORD = "1212";

    public static Connection conectar() {

        try {

            Connection conexion = DriverManager.getConnection(
                    URL,
                    USUARIO,
                    PASSWORD);

            System.out.println("✅ Conexión a PostgreSQL exitosa.");

            return conexion;

        } catch (SQLException e) {

            System.out.println("❌ Error al conectar con PostgreSQL:");
            e.printStackTrace();

            return null;
        }
    }

    public static void cerrar(Connection conexion) {

        if (conexion != null) {

            try {

                conexion.close();

                System.out.println("🔒 Conexión cerrada.");

            } catch (SQLException e) {

                e.printStackTrace();
            }
        }
    }
}
