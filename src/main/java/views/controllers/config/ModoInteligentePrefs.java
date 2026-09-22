package views.controllers.config;

import java.util.prefs.Preferences;

public class ModoInteligentePrefs {

    private static final Preferences prefs = Preferences.userRoot().node("utilmarket/open_cash");

    private static final String KEY_MODO_INTELIGENTE = "modo_inteligente_activo";
    private static final String KEY_IMPRESION_INTELIGENTE = "impresion_inteligente_activo";

    public static boolean isModoInteligenteActivo() {
        return prefs.getBoolean(KEY_MODO_INTELIGENTE, false);
    }

    public static void setModoInteligenteActivo(boolean activo) {
        prefs.putBoolean(KEY_MODO_INTELIGENTE, activo);
    }

    public static boolean isImpresionInteligenteActiva() {
        return prefs.getBoolean(KEY_IMPRESION_INTELIGENTE, false);
    }

    public static void setImpresionInteligenteActiva(boolean activo) {
        prefs.putBoolean(KEY_IMPRESION_INTELIGENTE, activo);
    }
}