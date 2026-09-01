package services.cash;

import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import helpers.MensajeHelper;
import model.Empleado;
import model.Sucursal;
import services.sql.EmpleadoService;

public class ConfigManagerDB {

    private final Preferences prefs;
    private final EmpleadoService empleadoService;

    private Empleado empleadoActual;
    private Sucursal sucursalActual;

    public ConfigManagerDB() {
        prefs = Preferences.userNodeForPackage(ConfigManagerDB.class);
        empleadoService = new EmpleadoService();
        cargarEmpleadoDesdePreferencias();
    }

    // =====================================================
    // CARGAR EMPLEADO Y SUCURSAL (PRIMERA DE LA LISTA)
    // =====================================================
    private void cargarEmpleadoDesdePreferencias() {
        long idEmpleado = prefs.getLong("idEmpleado", -1);
        if (idEmpleado != -1) {
            empleadoActual = empleadoService.verEmpleado(idEmpleado);
            if (empleadoActual != null) {
                List<Sucursal> sucursales = empleadoService.verSucursalEmpleado(idEmpleado);
                sucursalActual = (sucursales != null && !sucursales.isEmpty()) ? sucursales.get(0) : null;
                MensajeHelper.info("Empleado cargado: " + empleadoActual.getNombre());
            } else {
                limpiarPreferencias();
            }
        }
    }

    private void limpiarPreferencias() {
        prefs.remove("idEmpleado");
        prefs.remove("nombreEmpleado");
        prefs.remove("apellidoEmpleado");
        prefs.remove("dniEmpleado");
        prefs.remove("sesionActiva");
        empleadoActual = null;
        sucursalActual = null;
    }

    // =====================================================
    // VALIDAR PIN
    // =====================================================
    public boolean validarPin(String pin) {
        if (empleadoActual == null) {
            cargarEmpleadoDesdePreferencias();
        }
        if (empleadoActual == null) {
            MensajeHelper.advertencia("No hay empleado autenticado");
            return false;
        }
        boolean valido = empleadoService.validarPin(empleadoActual.getIdEmpleado(), pin);
        if (valido) {
            guardarEmpleadoEnPreferencias();
            MensajeHelper.info("PIN válido para: " + empleadoActual.getNombre());
        } else {
            MensajeHelper.advertencia("PIN incorrecto");
        }
        return valido;
    }

    public boolean tienePin() {

        if (empleadoActual == null) {
            cargarEmpleadoDesdePreferencias();
        }

        if (empleadoActual == null) {
            return false;
        }

        return empleadoService.existePin(
                empleadoActual.getIdEmpleado());
    }

    // =====================================================
    // GUARDAR EMPLEADO EN PREFERENCIAS
    // =====================================================
    private void guardarEmpleadoEnPreferencias() {
        if (empleadoActual == null) {
            MensajeHelper.advertencia("No se puede guardar empleado nulo en preferencias");
            return;
        }
        prefs.putLong("idEmpleado", empleadoActual.getIdEmpleado());
        prefs.put("nombreEmpleado", empleadoActual.getNombre() != null ? empleadoActual.getNombre() : "");
        prefs.put("apellidoEmpleado", empleadoActual.getApellido() != null ? empleadoActual.getApellido() : "");
        prefs.put("dniEmpleado", empleadoActual.getDni() != null ? empleadoActual.getDni() : "");
        prefs.putBoolean("sesionActiva", true);
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            MensajeHelper.error("Error al guardar preferencias", e);
        }
    }

    // =====================================================
    // ACTUALIZAR PIN (CREA O ACTUALIZA)
    // =====================================================
    public boolean actualizarPin(String nuevoPin) {
        if (empleadoActual == null) {
            cargarEmpleadoDesdePreferencias();
        }
        if (empleadoActual == null) {
            MensajeHelper.error("No hay empleado autenticado", null);
            return false;
        }

        boolean tienePin = empleadoService.existePin(empleadoActual.getIdEmpleado());
        boolean exitoso;

        if (tienePin) {
            exitoso = empleadoService.updatePin(empleadoActual.getIdEmpleado(), nuevoPin);
        } else {
            exitoso = empleadoService.crearPin(empleadoActual.getIdEmpleado(), nuevoPin);
        }

        if (exitoso) {
            // Recargar empleado (pero si falla, no actualizar empleadoActual)
            Empleado recargado = empleadoService.verEmpleado(empleadoActual.getIdEmpleado());
            if (recargado != null) {
                empleadoActual = recargado;
                // Recargar sucursal
                List<Sucursal> sucursales = empleadoService.verSucursalEmpleado(empleadoActual.getIdEmpleado());
                sucursalActual = (sucursales != null && !sucursales.isEmpty()) ? sucursales.get(0) : null;
                guardarEmpleadoEnPreferencias();
            } else {
                // Si no se puede recargar, al menos guardamos el id y nombre que ya teníamos
                // pero mejor mostrar error
                MensajeHelper.error("No se pudo recargar el empleado después de actualizar el PIN", null);
                return false;
            }
            MensajeHelper.info("PIN " + (tienePin ? "actualizado" : "creado") + " correctamente");
        } else {
            MensajeHelper.error("Error al " + (tienePin ? "actualizar" : "crear") + " PIN", null);
        }
        return exitoso;
    }

    public void recargarEmpleado() {
        if (empleadoActual != null) {
            long id = empleadoActual.getIdEmpleado();
            Empleado recargado = empleadoService.verEmpleado(id);
            if (recargado != null) {
                empleadoActual = recargado;
                List<Sucursal> sucursales = empleadoService.verSucursalEmpleado(id);
                sucursalActual = (sucursales != null && !sucursales.isEmpty()) ? sucursales.get(0) : null;
                guardarEmpleadoEnPreferencias();
            } else {
                limpiarPreferencias();
            }
        } else {
            cargarEmpleadoDesdePreferencias();
        }
    }

    // =====================================================
    // GETTERS Y OTROS MÉTODOS (igual que antes)
    // =====================================================
    public Empleado getEmpleadoActual() {
        return empleadoActual;
    }

    public Sucursal getSucursalActual() {
        return sucursalActual;
    }

    public String getNombreCompletoEmpleado() {
        if (empleadoActual != null) {
            return empleadoActual.getNombre() + " " + empleadoActual.getApellido();
        }
        return prefs.get("nombreEmpleado", "Empleado") + " " + prefs.get("apellidoEmpleado", "");
    }

    public String getNombreEmpleado() {
        return empleadoActual != null ? empleadoActual.getNombre() : prefs.get("nombreEmpleado", "Empleado");
    }

    public String getApellidoEmpleado() {
        return empleadoActual != null ? empleadoActual.getApellido() : prefs.get("apellidoEmpleado", "");
    }

    public String getDniEmpleado() {
        return empleadoActual != null ? empleadoActual.getDni() : prefs.get("dniEmpleado", "");
    }

    public long getIdEmpleado() {
        return empleadoActual != null ? empleadoActual.getIdEmpleado() : prefs.getLong("idEmpleado", -1);
    }

    public boolean sesionActiva() {
        return prefs.getBoolean("sesionActiva", false);
    }

    public void setEmpleadoActual(Empleado empleado) {
        if (empleado == null) {
            limpiarPreferencias();
            return;
        }
        this.empleadoActual = empleado;
        List<Sucursal> sucursales = empleadoService.verSucursalEmpleado(empleado.getIdEmpleado());
        this.sucursalActual = (sucursales != null && !sucursales.isEmpty()) ? sucursales.get(0) : null;
        guardarEmpleadoEnPreferencias();
        MensajeHelper.info("Empleado establecido: " + empleado.getNombre());
    }

    public void cerrarSesion() {
        empleadoActual = null;
        sucursalActual = null;
        prefs.putBoolean("sesionActiva", false);
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            MensajeHelper.error("Error al cerrar sesión", e);
        }
    }

    // IMPRESORA
    public void guardarImpresoraSeleccionada(String nombreImpresora) {
        if (nombreImpresora != null && !nombreImpresora.trim().isEmpty()) {
            prefs.put("impresoraSeleccionada", nombreImpresora);
            try {
                prefs.flush();
            } catch (BackingStoreException e) {
                MensajeHelper.error("Error al guardar impresora", e);
            }
        }
    }

    public String getImpresoraSeleccionada() {
        return prefs.get("impresoraSeleccionada", "");
    }

    public long getTiempoSesionMillis() {
        return 22L * 60 * 60 * 1000;
    }
}