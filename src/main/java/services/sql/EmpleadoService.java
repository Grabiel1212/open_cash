package services.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import model.Empleado;
import model.Sucursal;
import repository.EmpleadoRepository;
import schemas.ConsultasPosgre;
import util.ConeccionPosgre;

public class EmpleadoService implements EmpleadoRepository {

    // =====================================================
    // LOGIN EMPLEADO
    // =====================================================
    @Override
    public Empleado loginEmpleado(String dni) {
        try (Connection cn = ConeccionPosgre.conectar();
                PreparedStatement ps = cn.prepareStatement(ConsultasPosgre.VALIDAR_ACCESO)) {
            ps.setString(1, dni);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Empleado empleado = new Empleado();
                    empleado.setIdEmpleado(rs.getLong("id_empleado"));
                    empleado.setNombre(rs.getString("nombre"));
                    empleado.setApellido(rs.getString("apellido"));
                    empleado.setDni(rs.getString("dni"));
                    empleado.setFoto(rs.getString("foto"));
                    return empleado;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // =====================================================
    // VER EMPLEADO
    // =====================================================
    @Override
    public Empleado verEmpleado(Long idEmpleado) {
        try (Connection cn = ConeccionPosgre.conectar();
                PreparedStatement ps = cn.prepareStatement(ConsultasPosgre.OBTENER_EMPLEADO)) {
            ps.setLong(1, idEmpleado);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Empleado empleado = new Empleado();
                    empleado.setIdEmpleado(rs.getLong("id_empleado"));
                    empleado.setNombre(rs.getString("nombre"));
                    empleado.setApellido(rs.getString("apellido"));
                    empleado.setFoto(rs.getString("foto"));
                    return empleado;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // =====================================================
    // VER SUCURSALES DEL EMPLEADO
    // =====================================================
    @Override
    public List<Sucursal> verSucursalEmpleado(Long idEmpleado) {
        List<Sucursal> sucursales = new ArrayList<>();
        try (Connection cn = ConeccionPosgre.conectar();
                PreparedStatement ps = cn.prepareStatement(ConsultasPosgre.OBTENER_SUCURSALES_EMPLEADO)) {
            ps.setLong(1, idEmpleado);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sucursal sucursal = new Sucursal();
                    sucursal.setIdSucursal(rs.getLong("id_sucursal"));
                    sucursal.setNombre(rs.getString("nombre"));
                    sucursales.add(sucursal);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sucursales;
    }

    // =====================================================
    // VALIDAR PIN (tabla caja_cash)
    // =====================================================
    @Override
    public Boolean validarPin(Long idEmpleado, String pin) {
        try (Connection cn = ConeccionPosgre.conectar();
                PreparedStatement ps = cn.prepareStatement(ConsultasPosgre.VALIDAR_PIN)) {
            ps.setLong(1, idEmpleado);
            ps.setString(2, pin);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // =====================================================
    // ACTUALIZAR PIN (tabla caja_cash)
    // =====================================================
    @Override
    public Boolean updatePin(Long idEmpleado, String pin) {
        try (Connection cn = ConeccionPosgre.conectar();
                PreparedStatement ps = cn.prepareStatement(ConsultasPosgre.ACTUALIZAR_PIN)) {
            ps.setString(1, pin);
            ps.setLong(2, idEmpleado);
            int filas = ps.executeUpdate();
            return filas > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // =====================================================
    // CREAR PIN (tabla caja_cash)
    // =====================================================
    @Override
    public Boolean crearPin(Long idEmpleado, String pin) {
        try (Connection cn = ConeccionPosgre.conectar();
                PreparedStatement ps = cn.prepareStatement(ConsultasPosgre.CREAR_PIN)) {
            ps.setLong(1, idEmpleado);
            ps.setString(2, pin);
            int filas = ps.executeUpdate();
            return filas > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // =====================================================
    // VERIFICAR SI EXISTE PIN (tabla caja_cash)
    // =====================================================
    @Override
    public Boolean existePin(Long idEmpleado) {
        String sql = "SELECT EXISTS (SELECT 1 FROM caja_cash WHERE id_empleado = ?)";
        try (Connection cn = ConeccionPosgre.conectar();
                PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, idEmpleado);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
