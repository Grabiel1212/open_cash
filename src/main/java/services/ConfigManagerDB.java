package services;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import helpers.MensajeHelper;
import model.Cajero;

public class ConfigManagerDB {

    private final Preferences prefs;
    private final CajeroService cajeroService;
    private Cajero cajeroActual;

    public ConfigManagerDB() {
        prefs = Preferences.userNodeForPackage(ConfigManagerDB.class);
        cajeroService = new CajeroService();
        cargarCajeroDesdePreferencias();
    }

    // 🔹 Cargar cajero desde preferencias (si hay uno guardado)
    private void cargarCajeroDesdePreferencias() {
        int idCajero = prefs.getInt("idCajero", -1);
        if (idCajero != -1) {
            cajeroActual = cajeroService.obtenerCajeroPorId(idCajero);
            MensajeHelper.info("Cajero cargado desde preferencias: "
                    + (cajeroActual != null ? cajeroActual.getNombre() : "No encontrado"));
        }
    }

    // 🔹 Validar PIN del cajero
    public boolean validarPin(String pin) {
        cajeroActual = cajeroService.validarCajero(pin);
        if (cajeroActual != null) {
            guardarCajeroEnPreferencias();
            MensajeHelper.info("PIN validado correctamente para cajero: " + cajeroActual.getNombre());
            return true;
        }
        MensajeHelper.advertencia("PIN incorrecto");
        return false;
    }

    // 🔹 Guardar datos del cajero actual en preferencias
    private void guardarCajeroEnPreferencias() {
        if (cajeroActual != null) {
            prefs.putInt("idCajero", cajeroActual.getIdCajero());
            prefs.put("nombreCajero", cajeroActual.getNombre());
            prefs.putBoolean("sesionActiva", true);
            try {
                prefs.flush();
                MensajeHelper.info("Cajero guardado en preferencias: " + cajeroActual.getNombre());
            } catch (BackingStoreException e) {
                MensajeHelper.error("Error al guardar preferencias", e);
            }
        }
    }

    // 🔹 Actualizar PIN del cajero actual
    public boolean actualizarPin(String nuevoPin) {
        // Si no hay cajero cargado, intentar cargar desde preferencias
        if (cajeroActual == null) {
            cargarCajeroDesdePreferencias();
        }

        if (cajeroActual == null) {
            MensajeHelper.error("No hay cajero autenticado", null);
            return false;
        }

        boolean actualizado = cajeroService.actualizarPin(cajeroActual.getIdCajero(), nuevoPin);

        if (actualizado) {
            // Recargar el cajero desde la BD
            cajeroActual = cajeroService.obtenerCajeroPorId(cajeroActual.getIdCajero());
            // Guardar nuevamente en preferencias
            guardarCajeroEnPreferencias();
            MensajeHelper.info("PIN actualizado exitosamente para cajero: " + cajeroActual.getNombre());
            return true;
        }
        MensajeHelper.error("Error al actualizar PIN", null);
        return false;
    }

    // 🔹 Obtener nombre del cajero actual
    public String getNombreCajero() {
        if (cajeroActual != null) {
            return cajeroActual.getNombre();
        }
        return prefs.get("nombreCajero", "Cajero");
    }

    // 🔹 Obtener ID del cajero actual
    public int getIdCajero() {
        return prefs.getInt("idCajero", -1);
    }

    // 🔹 Cerrar sesión del cajero
    public void cerrarSesion() {
        prefs.putBoolean("sesionActiva", false);
        try {
            prefs.flush();
            MensajeHelper.info("Sesión cerrada correctamente");
        } catch (BackingStoreException e) {
            MensajeHelper.error("Error al cerrar sesión en preferencias", e);
        }
    }

    // 🔹 Verificar si hay sesión activa
    public boolean sesionActiva() {
        return prefs.getBoolean("sesionActiva", false);
    }

    // 🔹 Establecer el cajero actual y guardar en preferencias
    public void setCajeroActual(Cajero cajero) {
        if (cajero == null)
            return;

        this.cajeroActual = cajero;
        prefs.putInt("idCajero", cajero.getIdCajero());
        prefs.put("nombreCajero", cajero.getNombre());
        prefs.putBoolean("sesionActiva", true);
        try {
            prefs.flush();
            MensajeHelper.info("Cajero actual establecido: " + cajero.getNombre());
        } catch (BackingStoreException e) {
            MensajeHelper.error("Error al establecer cajero actual", e);
        }
    }

    // ---------- Gestión de impresoras ----------
    public void guardarImpresoraSeleccionada(String nombreImpresora) {
        if (nombreImpresora != null && !nombreImpresora.trim().isEmpty()) {
            prefs.put("impresoraSeleccionada", nombreImpresora);
            try {
                prefs.flush();
                MensajeHelper.info("Impresora guardada en preferencias: " + nombreImpresora);
            } catch (BackingStoreException e) {
                MensajeHelper.error("Error al guardar impresora en preferencias", e);
            }
        }
    }

    public String getImpresoraSeleccionada() {
        return prefs.get("impresoraSeleccionada", "");
    }

    // ---------- Tiempo de sesión fijo (22 horas) ----------
    public void setTiempoSesion(int minutos, int segundos) {
        // Tiempo fijo, no se modifica
    }

    public int getTiempoSesionMinutos() {
        return 22 * 60; // 22 horas
    }

    public int getTiempoSesionSegundos() {
        return 0;
    }

    public long getTiempoSesionMillis() {
        return 22L * 60 * 60 * 1000; // 22 horas en milisegundos
    }
}