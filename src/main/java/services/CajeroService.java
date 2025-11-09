package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import helpers.MensajeHelper;
import model.Cajero;
import schemas.Consultas;
import util.ConeccionSql;

public class CajeroService {

    private static final Consultas SQLCONSULTA = new Consultas();
    private final Connection conn;

    public CajeroService() {
        this.conn = ConeccionSql.getConnection();
    }

    // ✅ Validar cajero por PIN
    public Cajero validarCajero(String pin) {
        try (PreparedStatement stmt = conn.prepareStatement(SQLCONSULTA.VALIDAR_CAJERO)) {
            stmt.setString(1, pin);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Cajero cajero = new Cajero(
                            rs.getInt("IdCajero"),
                            rs.getString("Nombre"),
                            rs.getString("Pin"),
                            rs.getInt("IdUser"));

                    MensajeHelper.info("Cajero validado correctamente: " + cajero.getNombre());
                    return cajero;
                }
            }

            MensajeHelper.advertencia("PIN incorrecto o cajero no encontrado.");
        } catch (SQLException e) {
            MensajeHelper.error("Error al validar cajero.", e);
        }
        return null;
    }

    // ✅ Actualizar PIN
    public boolean actualizarPin(int idCajero, String nuevoPin) {
        try (PreparedStatement stmt = conn.prepareStatement(SQLCONSULTA.ACTUALIZAR_PIN)) {
            stmt.setString(1, nuevoPin);
            stmt.setInt(2, idCajero);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                MensajeHelper.info("PIN actualizado correctamente para cajero ID: " + idCajero);
                return true;
            }

            MensajeHelper.advertencia("No se encontró el cajero con ID: " + idCajero);

        } catch (SQLException e) {
            MensajeHelper.error("Error al actualizar el PIN del cajero.", e);
        }
        return false;
    }

    // ✅ Obtener cajero por ID
    public Cajero obtenerCajeroPorId(int idCajero) {
        try (PreparedStatement stmt = conn.prepareStatement(SQLCONSULTA.OBTENER_CAJERO_ID)) {
            stmt.setInt(1, idCajero);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Cajero cajero = new Cajero(
                            rs.getInt("IdCajero"),
                            rs.getString("Nombre"),
                            rs.getString("Pin"),
                            rs.getInt("IdUser"));
                    MensajeHelper.info("Cajero obtenido correctamente por ID: " + idCajero);
                    return cajero;
                }
            }

            MensajeHelper.advertencia("No se encontró un cajero con ID: " + idCajero);
        } catch (SQLException e) {
            MensajeHelper.error("Error al obtener cajero por ID.", e);
        }
        return null;
    }

    // ✅ Obtener cajero por ID de usuario
    public Cajero obtenerCajeroPorUsuario(int idUser) {
        try (PreparedStatement stmt = conn.prepareStatement(SQLCONSULTA.OBTENER_CAJERO_USUARIO)) {
            stmt.setInt(1, idUser);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Cajero cajero = new Cajero(
                            rs.getInt("IdCajero"),
                            rs.getString("Nombre"),
                            rs.getString("Pin"),
                            rs.getInt("IdUser"));
                    MensajeHelper.info("Cajero obtenido correctamente para usuario ID: " + idUser);
                    return cajero;
                }
            }

            MensajeHelper.advertencia("No se encontró cajero asociado al usuario ID: " + idUser);
        } catch (SQLException e) {
            MensajeHelper.error("Error al obtener cajero por usuario.", e);
        }
        return null;
    }
}
