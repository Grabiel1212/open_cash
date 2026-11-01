package views.controllers.config;

import java.util.prefs.Preferences;

/**
 * Persiste la sucursal con la que opera el usuario entre reinicios.
 * Usa Java Preferences → en Windows queda en el registro,
 * en Linux en ~/.java/.userPrefs, en Mac en un plist.
 */
public class SucursalPrefs {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SucursalPrefs.class);

    private static final String KEY_ID = "sucursal_id";
    private static final String KEY_NOMBRE = "sucursal_nombre";

    public static void guardar(Long id, String nombre) {
        if (id == null) {
            PREFS.remove(KEY_ID);
            PREFS.remove(KEY_NOMBRE);
            return;
        }
        PREFS.putLong(KEY_ID, id);
        PREFS.put(KEY_NOMBRE, nombre != null ? nombre : "");
    }

    public static Long getId() {
        long v = PREFS.getLong(KEY_ID, -1L);
        return v <= 0 ? null : v;
    }

    public static String getNombre() {
        return PREFS.get(KEY_NOMBRE, null);
    }

    public static void limpiar() {
        PREFS.remove(KEY_ID);
        PREFS.remove(KEY_NOMBRE);
    }
}