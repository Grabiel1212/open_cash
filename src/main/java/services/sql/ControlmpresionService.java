
package services.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import model.ControlImpresion;
import model.ControlImpresionInsumo;
import repository.ControlImpresionRepository;
import util.ConeccionPosgre;

public class ControlmpresionService implements ControlImpresionRepository {

    @Override
    public Long CrearConteoInicial(
            Long idSucursal,
            Long inicialRicoh,
            Long inicialEpson) {

        if (idSucursal == null || idSucursal <= 0
                || inicialRicoh == null || inicialRicoh < 0
                || inicialEpson == null || inicialEpson < 0) {
            return null;
        }

        String sql = """
                INSERT INTO control_impresion (
                    id_sucursal,
                    inicial_ricoh,
                    inicial_epson
                )
                VALUES (?, ?, ?)
                """;

        try (Connection conn = ConeccionPosgre.conectar();
                PreparedStatement ps = conn.prepareStatement(
                        sql,
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, idSucursal);
            ps.setLong(2, inicialRicoh);
            ps.setLong(3, inicialEpson);

            int filas = ps.executeUpdate();

            if (filas == 1) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }

            return null;

        } catch (SQLException e) {
            System.err.println(
                    "Error al crear el conteo inicial: " + e.getMessage());
            return null;
        }
    }

    @Override
    public Boolean CrearConteoFinal(
            Long idControl,
            Long finalRicoh,
            Long finalEpson,
            List<ControlImpresionInsumo> insumos) {

        // 1. Validaciones básicas (insumos ya NO es obligatorio)
        if (idControl == null || idControl <= 0
                || finalRicoh == null || finalRicoh < 0
                || finalEpson == null || finalEpson < 0) {
            return false;
        }

        String sqlBuscar = """
                SELECT inicial_ricoh, inicial_epson
                FROM control_impresion
                WHERE id_control = ?
                  AND estado = 'ABIERTO'
                FOR UPDATE
                """;

        String sqlCerrar = """
                UPDATE control_impresion
                SET final_ricoh = ?,
                    final_epson = ?,
                    fecha_hora_final = LOCALTIMESTAMP,
                    estado = 'FINALIZADO',
                    actualizado_en = LOCALTIMESTAMP
                WHERE id_control = ?
                  AND estado = 'ABIERTO'
                """;

        String sqlInsumo = """
                INSERT INTO control_impresion_insumo (
                    id_control,
                    tipo_insumo,
                    tamano,
                    cantidad
                )
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conn = ConeccionPosgre.conectar()) {

            conn.setAutoCommit(false);

            try {
                long inicialRicoh;
                long inicialEpson;

                // 2. Buscar el control abierto y bloquearlo
                try (PreparedStatement ps = conn.prepareStatement(sqlBuscar)) {

                    ps.setLong(1, idControl);

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }

                        inicialRicoh = rs.getLong("inicial_ricoh");
                        inicialEpson = rs.getLong("inicial_epson");
                    }
                }

                // 3. Validar contadores finales
                if (finalRicoh < inicialRicoh
                        || finalEpson < inicialEpson) {
                    conn.rollback();
                    return false;
                }

                // 4. Registrar los insumos (solo si hay)
                if (insumos != null && !insumos.isEmpty()) {

                    try (PreparedStatement ps = conn.prepareStatement(sqlInsumo)) {

                        for (ControlImpresionInsumo insumo : insumos) {

                            if (insumo == null
                                    || insumo.getTipoInsumo() == null
                                    || insumo.getTamano() == null
                                    || insumo.getCantidad() == null
                                    || insumo.getCantidad() < 0) {
                                conn.rollback();
                                return false;
                            }

                            ps.setLong(1, idControl);
                            ps.setString(2, insumo.getTipoInsumo());
                            ps.setString(3, insumo.getTamano());
                            ps.setLong(4, insumo.getCantidad());

                            ps.addBatch();
                        }

                        ps.executeBatch();
                    }
                }

                // 5. Cerrar el control
                try (PreparedStatement ps = conn.prepareStatement(sqlCerrar)) {

                    ps.setLong(1, finalRicoh);
                    ps.setLong(2, finalEpson);
                    ps.setLong(3, idControl);

                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }

                // 6. Confirmar todas las operaciones
                conn.commit();
                return true;

            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println(
                    "Error al cerrar el control de impresión: "
                            + e.getMessage());
            return false;
        }
    }

    @Override
    public List<ControlImpresion> ListarControles(Long idSucursal) {

        List<ControlImpresion> lista = new java.util.ArrayList<>();

        if (idSucursal == null || idSucursal <= 0) {
            return lista;
        }

        String sql = """
                SELECT
                    id_control,
                    id_sucursal,
                    fecha_hora_inicio,
                    inicial_ricoh,
                    inicial_epson,
                    fecha_hora_final,
                    final_ricoh,
                    final_epson,
                    estado,
                    observacion,
                    creado_en,
                    actualizado_en
                FROM control_impresion
                WHERE id_sucursal = ?
                ORDER BY fecha_hora_inicio DESC
                """;

        try (Connection conn = ConeccionPosgre.conectar();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idSucursal);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    ControlImpresion control = new ControlImpresion();

                    control.setIdControl(rs.getLong("id_control"));
                    control.setIdSucursal(rs.getLong("id_sucursal"));

                    control.setFechaHoraInicio(
                            rs.getTimestamp("fecha_hora_inicio").toLocalDateTime());

                    control.setInicialRicoh(rs.getLong("inicial_ricoh"));
                    control.setInicialEpson(rs.getLong("inicial_epson"));

                    java.sql.Timestamp fechaFinal = rs.getTimestamp("fecha_hora_final");

                    if (fechaFinal != null) {
                        control.setFechaHoraFinal(
                                fechaFinal.toLocalDateTime());
                    }

                    long valorFinalRicoh = rs.getLong("final_ricoh");
                    if (!rs.wasNull()) {
                        control.setFinalRicoh(valorFinalRicoh);
                    }

                    long valorFinalEpson = rs.getLong("final_epson");
                    if (!rs.wasNull()) {
                        control.setFinalEpson(valorFinalEpson);
                    }

                    control.setEstado(rs.getString("estado"));
                    control.setObservacion(rs.getString("observacion"));

                    control.setCreadoEn(
                            rs.getTimestamp("creado_en").toLocalDateTime());

                    control.setActualizadoEn(
                            rs.getTimestamp("actualizado_en").toLocalDateTime());

                    lista.add(control);
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Error al listar controles de impresión: " + e.getMessage());
        }

        return lista;
    }

    @Override
    public ControlImpresion ListarPorId(Long idControl) {

        if (idControl == null || idControl <= 0) {
            return null;
        }

        String sql = """
                SELECT
                    id_control,
                    id_sucursal,
                    fecha_hora_inicio,
                    inicial_ricoh,
                    inicial_epson,
                    fecha_hora_final,
                    final_ricoh,
                    final_epson,
                    estado,
                    observacion,
                    creado_en,
                    actualizado_en
                FROM control_impresion
                WHERE id_control = ?
                """;

        try (Connection conn = ConeccionPosgre.conectar();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idControl);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    ControlImpresion control = new ControlImpresion();

                    control.setIdControl(
                            rs.getLong("id_control"));

                    control.setIdSucursal(
                            rs.getLong("id_sucursal"));

                    control.setFechaHoraInicio(
                            rs.getTimestamp("fecha_hora_inicio")
                                    .toLocalDateTime());

                    control.setInicialRicoh(
                            rs.getLong("inicial_ricoh"));

                    control.setInicialEpson(
                            rs.getLong("inicial_epson"));

                    java.sql.Timestamp fechaFinal = rs.getTimestamp("fecha_hora_final");

                    control.setFechaHoraFinal(
                            fechaFinal != null
                                    ? fechaFinal.toLocalDateTime()
                                    : null);

                    Long finalRicoh = rs.getObject(
                            "final_ricoh", Long.class);

                    Long finalEpson = rs.getObject(
                            "final_epson", Long.class);

                    control.setFinalRicoh(finalRicoh);
                    control.setFinalEpson(finalEpson);

                    control.setEstado(
                            rs.getString("estado"));

                    control.setObservacion(
                            rs.getString("observacion"));

                    java.sql.Timestamp creadoEn = rs.getTimestamp("creado_en");

                    control.setCreadoEn(
                            creadoEn != null
                                    ? creadoEn.toLocalDateTime()
                                    : null);

                    java.sql.Timestamp actualizadoEn = rs.getTimestamp("actualizado_en");

                    control.setActualizadoEn(
                            actualizadoEn != null
                                    ? actualizadoEn.toLocalDateTime()
                                    : null);

                    return control;
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Error al listar el control de impresión: "
                            + e.getMessage());
        }

        return null;
    }

    @Override
    public boolean ExisteAbiertoPorSucursal(Long idSucursal) {

        if (idSucursal == null || idSucursal <= 0) {
            return false;
        }

        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM control_impresion
                    WHERE id_sucursal = ?
                      AND estado = 'ABIERTO'
                )
                """;

        try (Connection conn = ConeccionPosgre.conectar();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idSucursal);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Error al comprobar el control abierto: " + e.getMessage());
        }

        return false;
    }

    @Override
    public ControlImpresion ObtenerControlAbiertoPorSucursal(Long idSucursal) {

        if (idSucursal == null || idSucursal <= 0) {
            return null;
        }

        String sql = """
                SELECT
                    id_control,
                    id_sucursal,
                    fecha_hora_inicio,
                    inicial_ricoh,
                    inicial_epson,
                    fecha_hora_final,
                    final_ricoh,
                    final_epson,
                    estado,
                    observacion,
                    creado_en,
                    actualizado_en
                FROM control_impresion
                WHERE id_sucursal = ?
                  AND estado = 'ABIERTO'
                ORDER BY fecha_hora_inicio DESC
                LIMIT 1
                """;

        try (Connection conn = ConeccionPosgre.conectar();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, idSucursal);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ControlImpresion control = new ControlImpresion();

                    control.setIdControl(rs.getLong("id_control"));
                    control.setIdSucursal(rs.getLong("id_sucursal"));
                    control.setFechaHoraInicio(
                            rs.getTimestamp("fecha_hora_inicio").toLocalDateTime());
                    control.setInicialRicoh(rs.getLong("inicial_ricoh"));
                    control.setInicialEpson(rs.getLong("inicial_epson"));
                    control.setEstado(rs.getString("estado"));
                    control.setObservacion(rs.getString("observacion"));
                    return control;
                }
            }

        } catch (SQLException e) {
            System.err.println(
                    "Error al obtener el control abierto: " + e.getMessage());
        }

        return null;
    }

}
