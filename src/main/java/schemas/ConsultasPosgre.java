
package schemas;

public class ConsultasPosgre {

    // =====================================================
    // ACCESO AL SISTEMA
    // Solo con DNI del empleado
    // =====================================================

    public static final String VALIDAR_ACCESO = """
            SELECT id_empleado,
                   nombre,
                   apellido,
                   dni,
                   foto
            FROM empleado
            WHERE dni = ?
              AND estado = 'ACTIVO'
            """;
    public static final String VALIDAR_PIN = """
            SELECT EXISTS (
                SELECT 1 FROM caja_cash
                WHERE id_empleado = ? AND pin = ?
            )
            """;

    public static final String ACTUALIZAR_PIN = """
            UPDATE caja_cash
            SET pin = ?
            WHERE id_empleado = ?
            """;

    public static final String CREAR_PIN = """
            INSERT INTO caja_cash (id_empleado, pin)
            VALUES (?, ?)
            """;

    // =====================================================
    // EMPLEADO
    // =====================================================

    public static final String OBTENER_EMPLEADO = """
            SELECT id_empleado,
                   nombre,
                   apellido,
                   foto
            FROM empleado
            WHERE id_empleado = ?
            """;

    // =====================================================
    // SUCURSAL DEL EMPLEADO
    // =====================================================
    public static final String OBTENER_SUCURSALES_EMPLEADO = """
            SELECT
                s.id_sucursal,
                s.nombre
            FROM empleado e
            INNER JOIN empleado_sucursal es
                    ON es.id_empleado = e.id_empleado
            INNER JOIN sucursal s
                    ON s.id_sucursal = es.id_sucursal
            WHERE e.id_empleado = ?
              AND e.estado = 'ACTIVO'
              AND es.activo = TRUE
              AND s.activo = TRUE
            ORDER BY s.nombre
            """;

}