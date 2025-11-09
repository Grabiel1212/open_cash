package helpers;

import javax.swing.JOptionPane;

/**
 *
 * @author juang
 */
public class MensajeHelper {

    // --- Información ---
    public static void info(String mensaje) {
        System.out.println("✅ INFO: " + mensaje);
    }

    public static void infoDialog(String mensaje) {
        JOptionPane.showMessageDialog(null, mensaje, "Información", JOptionPane.INFORMATION_MESSAGE);
    }

    // --- Advertencia ---
    public static void advertencia(String mensaje) {
        System.out.println("⚠️ ADVERTENCIA: " + mensaje);
    }

    public static void advertenciaDialog(String mensaje) {
        JOptionPane.showMessageDialog(null, mensaje, "Advertencia", JOptionPane.WARNING_MESSAGE);
    }

    // --- Error ---
    public static void error(String mensaje, Exception e) {
        System.err.println("❌ ERROR: " + mensaje);
        if (e != null) {
            e.printStackTrace();
        }
    }

    public static void handleError(String mensaje) {
        System.err.println("====================================");
        System.err.println("⚠️  ERROR DETECTADO");
        System.err.println("Mensaje: " + mensaje);
        System.err.println("====================================\n");
    }

    public static void errorDialog(String mensaje) {
        JOptionPane.showMessageDialog(null, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }

}
