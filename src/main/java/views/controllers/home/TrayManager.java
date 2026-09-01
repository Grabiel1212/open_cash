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
import javafx.stage.StageStyle;
import services.cash.HotkeyManager;

public class TrayManager {

    private final Stage primaryStage;
    private HotkeyManager hotkeyManager;
    private TrayIcon trayIcon;

    public TrayManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setHotkeyManager(HotkeyManager hotkeyManager) {
        this.hotkeyManager = hotkeyManager;
    }

    public void ocultarEnBandeja() {
        try {
            if (!SystemTray.isSupported()) {
                System.out.println("⚠️ El SystemTray no es compatible con este sistema.");
                return;
            }

            Platform.runLater(() -> {
                if (primaryStage != null) {
                    primaryStage.setIconified(true);
                    primaryStage.hide();
                }
            });

            SystemTray tray = SystemTray.getSystemTray();

            // remover icono anterior
            if (trayIcon != null) {
                tray.remove(trayIcon);
                trayIcon = null;
            }

            BufferedImage image;
            try (InputStream is = getClass().getResourceAsStream("/images/logo/libro_logo.png")) {
                if (is == null) {
                    image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                } else {
                    image = ImageIO.read(is);
                }
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
                System.out.println("🚪 Cerrando aplicación desde menú de bandeja...");
                if (hotkeyManager != null) {
                    hotkeyManager.stopListening();
                }
                tray.remove(trayIcon);
                Platform.exit();
                System.exit(0);
            });
            menu.add(salir);

            trayIcon.setPopupMenu(menu);
            tray.add(trayIcon);

            trayIcon.displayMessage("Aplicación minimizada", "Utilmarket sigue ejecutándose en segundo plano.",
                    TrayIcon.MessageType.INFO);

            System.out.println("🟡 Aplicación oculta en bandeja del sistema.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarDesdeBandeja() {
        Platform.runLater(() -> {
            try {
                if (primaryStage != null) {
                    System.out.println("🟢 Restaurando ventana desde bandeja...");
                    primaryStage.setIconified(false);
                    primaryStage.show();
                    primaryStage.toFront();
                    primaryStage.requestFocus();

                    // truco para Windows 11
                    Stage temp = new Stage();
                    temp.setOpacity(0);
                    temp.initStyle(StageStyle.UTILITY);
                    temp.setAlwaysOnTop(true);
                    temp.show();
                    temp.toFront();
                    temp.close();

                    primaryStage.setAlwaysOnTop(true);
                    primaryStage.setAlwaysOnTop(false);

                    System.out.println("✅ Ventana restaurada correctamente.");
                }
            } catch (Exception e) {
                System.err.println("❌ Error al restaurar ventana: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}