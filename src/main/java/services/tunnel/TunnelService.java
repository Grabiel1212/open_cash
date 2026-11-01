
package services.tunnel;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class TunnelService {

    private static Process proceso;
    private static Thread hiloSalida;

    private TunnelService() {
    }

    // =========================================
    // ENCENDER TUNNEL
    // =========================================
    public static synchronized void iniciar() {

        if (proceso != null && proceso.isAlive()) {
            System.out.println("CDP Tunnel ya está encendido.");
            return;
        }

        try {

            ProcessBuilder builder = new ProcessBuilder(
                    "cmd.exe",
                    "/c",
                    "cdp-tunnel start --auto-restart");

            builder.redirectErrorStream(true);

            proceso = builder.start();

            Process procesoActual = proceso;

            System.out.println("Iniciando CDP Tunnel...");

            hiloSalida = new Thread(() -> {

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                procesoActual.getInputStream()))) {

                    String linea;

                    while ((linea = reader.readLine()) != null) {
                        System.out.println("[CDP TUNNEL] " + linea);
                    }

                } catch (Exception e) {
                    System.err.println("Error Tunnel: " + e.getMessage());
                }

            }, "cdp-tunnel-output");

            hiloSalida.setDaemon(true);
            hiloSalida.start();

        } catch (Exception e) {

            proceso = null;

            System.err.println(
                    "Error iniciando CDP Tunnel: " + e.getMessage());
        }
    }

    // =========================================
    // APAGAR TUNNEL
    // =========================================
    // =========================================
    // APAGAR TUNNEL
    // =========================================
    public static synchronized void detener() {

        System.out.println("Apagando CDP Tunnel...");

        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-Command",
                    "Get-CimInstance Win32_Process | " +
                            "Where-Object { " +
                            "$_.Name -eq 'node.exe' -and " +
                            "$_.CommandLine -like '*cdp-tunnel*' " +
                            "} | ForEach-Object { " +
                            "Stop-Process -Id $_.ProcessId -Force " +
                            "}");

            builder.redirectErrorStream(true);

            Process apagar = builder.start();
            int resultado = apagar.waitFor();

            if (proceso != null) {
                proceso.destroyForcibly();
                proceso = null;
            }

            if (hiloSalida != null) {
                hiloSalida.interrupt();
                hiloSalida = null;
            }

            if (resultado == 0) {
                System.out.println("CDP Tunnel apagado correctamente.");
            } else {
                System.err.println("No se pudo apagar CDP Tunnel.");
            }

        } catch (Exception e) {
            System.err.println("Error apagando CDP Tunnel: " + e.getMessage());
        }
    }

    // =========================================
    // VERIFICAR ESTADO
    // =========================================
    public static synchronized boolean estaActivo() {

        return proceso != null && proceso.isAlive();
    }
}
