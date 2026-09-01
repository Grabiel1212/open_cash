
package repository;

import java.util.List;

import model.Empleado;
import model.Sucursal;

public interface EmpleadoRepository {

    // =====================================================
    // LOGIN
    // =====================================================

    Empleado loginEmpleado(String dni);

    // =====================================================
    // EMPLEADO
    // =====================================================

    Empleado verEmpleado(Long idEmpleado);

    // =====================================================
    // SUCURSAL DEL EMPLEADO
    // =====================================================

    List<Sucursal> verSucursalEmpleado(Long idEmpleado);

    // =====================================================
    // CAJA CASH
    // =====================================================

    Boolean validarPin(Long idEmpleado, String pin);

    // =====================================================
    // ACTUALIZAR PIN
    // =====================================================

    Boolean updatePin(Long idEmpleado, String pin);

    Boolean crearPin(Long idEmpleado, String pin);

    Boolean existePin(Long idEmpleado);
}
