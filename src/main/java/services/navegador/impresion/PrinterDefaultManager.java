package services.navegador.impresion;

import java.util.concurrent.TimeUnit;

import services.cash.ConfigManagerDB;

/**
 * Aplica la impresora seleccionada en Home como impresora
 * predeterminada de Windows (necesario para --kiosk-printing).
 */
final class PrinterDefaultManager {

    private final ConfigManagerDB configManager;

    PrinterDefaultManager(ConfigManagerDB configManager) {
        this.configManager = configManager;
    }

    void aplicarImpresoraComoDefault() {
        if (configManager == null)
            return;

        String impresora = null;
        try {
            impresora = configManager.getImpresoraSeleccionada();
        } catch (Exception ignored) {
        }

        if (impresora == null || impresora.isBlank()) {
            System.out.println("⚠️ ChromePrintService: no hay impresora seleccionada en Home.");
            return;
        }

        try {
            String nombreEscapado = impresora.replace("'", "''");
            String ps = "(New-Object -ComObject WScript.Network).SetDefaultPrinter('"
                    + nombreEscapado + "')";

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command", ps);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, TimeUnit.SECONDS);

            System.out.println("🖨️ Impresora default → " + impresora);
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo setear la impresora default: " + e.getMessage());
        }
    }
}