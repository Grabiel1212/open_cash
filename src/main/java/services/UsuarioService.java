package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import helpers.MensajeHelper;
import model.Usuario;
import schemas.Consultas;
import util.ConeccionSql;

public class UsuarioService {

    private static final Consultas SQLCONSULTA = new Consultas();

    public Usuario validarUsuario(Usuario user) {
        Connection conn = null;
        try {
            conn = ConeccionSql.getConnection();
            if (conn == null) {
                MensajeHelper.errorDialog("No se pudo establecer conexión con la base de datos.");
                return null;
            }

            try (PreparedStatement stmt = conn.prepareStatement(SQLCONSULTA.VALIDAR_USUARIO)) {
                stmt.setString(1, user.getUsuario());
                stmt.setString(2, user.getPassword());

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    // 🔹 Asegúrate de usar los nombres reales de tus columnas:
                    return new Usuario(
                            rs.getInt("IdUser"),
                            rs.getString("Nombre"),
                            rs.getString("Password"));
                }
            }
        } catch (SQLException e) {
            MensajeHelper.error("Error al validar usuario", e);
        } finally {
            ConeccionSql.closeConnection(conn);
        }
        return null;
    }
}
