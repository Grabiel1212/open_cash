package views.controllers.config;

import java.util.prefs.Preferences;

/**
 * Marca si la última sesión se cerró con un control ABIERTO sin finalizar.
 * Se usa para forzar el registro final al próximo arranque.
 */
public class CierrePendientePrefs {

    private static final Preferences PREFS = Preferences.userNodeForPackage(CierrePendientePrefs.class);

    private static final String KEY = "cierre_pendiente_sucursal";

    public static void marcar(Long idSucursal) {
        if (idSucursal == null) {
            PREFS.remove(KEY);
            return;
        }
        PREFS.putLong(KEY, idSucursal);
    }

    public static Long getIdSucursalPendiente() {
        long v = PREFS.getLong(KEY, -1L);
        return v <= 0 ? null : v;
    }

    public static void limpiar() {
        PREFS.remove(KEY);
    }
}