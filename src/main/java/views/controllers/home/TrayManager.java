package views.controllers.home;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.stage.Stage;
import services.cash.HotkeyManager;

public class TrayManager {

    private final Stage primaryStage;
    private HotkeyManager hotkeyManager;
    private TrayIcon trayIcon;
    private Runnable shutdownCallback;

    // 👇 Bandera para crear el icono UNA sola vez
    private boolean trayReady = false;

    public TrayManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setHotkeyManager(HotkeyManager hotkeyManager) {
        this.hotkeyManager = hotkeyManager;
    }

    public void setShutdownCallback(Runnable shutdownCallback) {
        this.shutdownCallback = shutdownCallback;
    }

    public void ocultarEnBandeja() {
        try {
            if (!SystemTray.isSupported()) {
                System.out.println("⚠️ SystemTray no soportado.");
                return;
            }

            // 1) OCULTAR: solo hide(), NUNCA setIconified(true) antes de hide
            Platform.runLater(() -> {
                if (primaryStage != null) {
                    primaryStage.hide();
                }
            });

            // 2) Crear el icono de bandeja SOLO la primera vez
            if (!trayReady) {
                SystemTray tray = SystemTray.getSystemTray();

                BufferedImage image;
                try (InputStream is = getClass().getResourceAsStream("/images/logo/libro_logo.png")) {
                    image = (is == null)
                            ? new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                            : ImageIO.read(is);
                }

                trayIcon = new TrayIcon(image, "Utilmarket - en segundo plano");
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> Platform.runLater(this::mostrarDesdeBandeja));

                PopupMenu menu = new PopupMenu();

                MenuItem abrir = new MenuItem("Abrir");
                abrir.addActionListener(e -> Platform.runLater(this::mostrarDesdeBandeja));
                menu.add(abrir);

                MenuItem salir = new MenuItem("Salir");
                salir.addActionListener(e -> {
                    if (shutdownCallback != null) {
                        try {
                            shutdownCallback.run();
                        } catch (Exception ignored) {
                        }
                    }
                    if (hotkeyManager != null)
                        hotkeyManager.stopListening();
                    tray.remove(trayIcon);
                    trayReady = false;
                    Platform.exit();
                    System.exit(0);
                });
                menu.add(salir);

                trayIcon.setPopupMenu(menu);
                tray.add(trayIcon);
                trayReady = true;

                trayIcon.displayMessage(
                        "Aplicación minimizada",
                        "Utilmarket sigue ejecutándose en segundo plano.",
                        TrayIcon.MessageType.INFO);
            }

            System.out.println("🟡 Aplicación oculta en bandeja.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarDesdeBandeja() {
        try {
            if (primaryStage == null)
                return;

            // ORDEN CORRECTO:
            // 1) mostrar
            primaryStage.show();
            // 2) des-iconificar (por si quedó minimizada)
            if (primaryStage.isIconified()) {
                primaryStage.setIconified(false);
            }
            // 3) traer al frente
            primaryStage.toFront();
            primaryStage.requestFocus();

            System.out.println("✅ Ventana restaurada desde bandeja.");

        } catch (Exception e) {
            System.err.println("❌ Error restaurando ventana: " + e.getMessage());
            e.printStackTrace();
        }
    }
}