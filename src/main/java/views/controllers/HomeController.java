package views.controllers;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.print.Printer;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import services.ConfigManagerDB;
import services.HotkeyManager;
import views.alerts.AlertHelper;
import views.icons.Icons;

public class HomeController {

    @FXML
    private AnchorPane root;
    @FXML
    private StackPane loadingCard;
    @FXML
    private ImageView imgLogo, imgUpdate, imgPrint;
    @FXML
    private ComboBox<String> cmbImpresoras;
    @FXML
    private Button btnRefrescar, btnSeleccionar, btnActualizarContraseña;
    @FXML
    private Text lblTitulo;

    private double xOffset = 0, yOffset = 0;
    private ConfigManagerDB configManager;
    private HotkeyManager hotkeyManager;
    private TrayIcon trayIcon;
    private Stage primaryStage;

    @FXML
    public void initialize() {
        try {
            System.out.println("🚀 INICIANDO HOME CONTROLLER...");
            Platform.setImplicitExit(false); // 👈 clave para mantener app viva en bandeja
            setupDragAndDrop();
            loadIcons();
            setupAnimations();
            initializeServices();
            cargarImpresoraGuardada();
            refrescarImpresoras();
            System.out.println("✅ HomeController LISTO");
        } catch (Exception e) {
            System.err.println("❌ Error en initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        System.out.println("🎯 Stage principal asignado");

        // Al cerrar ventana → minimizar en bandeja
        primaryStage.setOnCloseRequest(e -> {
            e.consume();
            ocultarEnBandeja();
        });
    }

    private void initializeServices() {
        configManager = new ConfigManagerDB();
        hotkeyManager = new HotkeyManager(configManager, this::abrirCaja);
        hotkeyManager.startListening();
    }

    private void setupDragAndDrop() {
        root.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        root.setOnMouseDragged(e -> {
            if (primaryStage != null) {
                primaryStage.setX(e.getScreenX() - xOffset);
                primaryStage.setY(e.getScreenY() - yOffset);
            }
        });
    }

    private void loadIcons() {
        Icons.setImageIcons(imgLogo, "logos.png", 20);
        Icons.setImageIcons(imgUpdate, "update.png", 14);
        Icons.setImageIcons(imgPrint, "print.png", 35);
    }

    private void setupAnimations() {
        FadeTransition fadeIn = new FadeTransition(Duration.millis(800), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    // 🔴 Botón cerrar
    @FXML
    private void cerrarVentana() {
        ocultarEnBandeja();
    }

    // 🔵 Botón minimizar
    @FXML
    private void minimizarVentana() {
        if (primaryStage != null) {
            primaryStage.setIconified(true);
        }
    }

    // 🟡 Ocultar en bandeja (sin cerrar)
    private void ocultarEnBandeja() {
        try {
            if (!SystemTray.isSupported()) {
                System.out.println("⚠️ El SystemTray no es compatible con este sistema.");
                return;
            }

            Platform.runLater(() -> {
                if (primaryStage != null) {
                    primaryStage.setIconified(true);
                    primaryStage.hide(); // opcional, pero ayuda a liberar la UI
                }
            });

            SystemTray tray = SystemTray.getSystemTray();

            // Remover icono previo si existía
            if (trayIcon != null) {
                tray.remove(trayIcon);
                trayIcon = null;
            }

            // Cargar icono desde recursos
            BufferedImage image;
            try (InputStream is = getClass().getResourceAsStream("/images/logos.png")) {
                if (is == null) {
                    image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                } else {
                    image = ImageIO.read(is);
                }
            }

            trayIcon = new TrayIcon(image, "Mi aplicación - en segundo plano");
            trayIcon.setImageAutoSize(true);

            // 🖱️ Doble clic → mostrar ventana
            trayIcon.addActionListener(e -> Platform.runLater(this::mostrarDesdeBandeja));

            // 📋 Menú contextual
            PopupMenu menu = new PopupMenu();

            MenuItem abrir = new MenuItem("Abrir");
            abrir.addActionListener(e -> Platform.runLater(this::mostrarDesdeBandeja));
            menu.add(abrir);

            MenuItem salir = new MenuItem("Salir");
            salir.addActionListener(e -> {
                System.out.println("🚪 Cerrando aplicación desde menú de bandeja...");
                tray.remove(trayIcon);
                Platform.exit();
                System.exit(0);
            });
            menu.add(salir);

            trayIcon.setPopupMenu(menu);
            tray.add(trayIcon);

            trayIcon.displayMessage(
                    "Aplicación minimizada",
                    "Sigue ejecutándose en segundo plano.",
                    TrayIcon.MessageType.INFO);

            System.out.println("🟡 Aplicación oculta en bandeja del sistema.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 🟢 Restaurar desde bandeja (funciona en Windows 11)
    private void mostrarDesdeBandeja() {
        Platform.runLater(() -> {
            try {
                if (primaryStage != null) {
                    System.out.println("🟢 Restaurando ventana desde bandeja...");

                    primaryStage.setIconified(false);
                    primaryStage.show();
                    primaryStage.toFront();
                    primaryStage.requestFocus();

                    // Forzar al frente con truco temporal
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

    // --------------------- DEMÁS FUNCIONES ---------------------

    private void cargarImpresoraGuardada() {
        try {
            String impresoraGuardada = configManager.getImpresoraSeleccionada();
            if (impresoraGuardada != null && !impresoraGuardada.isEmpty()) {
                cmbImpresoras.setValue(impresoraGuardada);
            }
        } catch (Exception e) {
            System.err.println("❌ Error al cargar impresora: " + e.getMessage());
        }
    }

    @FXML
    private void refrescarImpresoras() {
        try {
            mostrarCarga();
            PauseTransition delay = new PauseTransition(Duration.seconds(1.2));
            delay.setOnFinished(e -> {
                cargarImpresorasReales();
                ocultarCarga();
                cargarImpresoraGuardada();
            });
            delay.play();
        } catch (Exception e) {
            System.err.println("❌ Error al refrescar impresoras: " + e.getMessage());
        }
    }

    private void mostrarCarga() {
        loadingCard.setVisible(true);
        loadingCard.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), loadingCard);
        fadeIn.setToValue(1);
        fadeIn.play();

        Circle spinnerCircle = (Circle) loadingCard.lookup(".spinner-circle");
        if (spinnerCircle != null) {
            RotateTransition rt = new RotateTransition(Duration.seconds(1.5), spinnerCircle);
            rt.setByAngle(360);
            rt.setCycleCount(FadeTransition.INDEFINITE);
            rt.play();
        }
    }

    private void ocultarCarga() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), loadingCard);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> loadingCard.setVisible(false));
        fadeOut.play();
    }

    private void cargarImpresorasReales() {
        ObservableList<String> impresoras = FXCollections.observableArrayList();
        Printer.getAllPrinters().forEach(p -> impresoras.add(p.getName()));
        cmbImpresoras.setItems(impresoras);
        cmbImpresoras.setPromptText(
                impresoras.isEmpty() ? "No se encontraron impresoras" : "Seleccione una impresora...");
    }

    @FXML
    private void seleccionarImpresora() {
        try {
            String seleccionada = cmbImpresoras.getValue();
            if (seleccionada == null || seleccionada.isEmpty()) {
                animarError(cmbImpresoras);
                AlertHelper.mostrar("Advertencia", "Por favor seleccione una impresora.",
                        AlertHelper.AlertType.WARNING);
                return;
            }
            configManager.guardarImpresoraSeleccionada(seleccionada);
            AlertHelper.mostrar("Listo", "Impresora predeterminada: " + seleccionada,
                    AlertHelper.AlertType.INFO);
        } catch (Exception e) {
            AlertHelper.mostrar("Error", "No se pudo guardar la impresora seleccionada.",
                    AlertHelper.AlertType.ERROR);
        }
    }

    private void animarError(javafx.scene.Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(100), node);
        tt.setFromX(0);
        tt.setByX(5);
        tt.setCycleCount(3);
        tt.setAutoReverse(true);
        tt.play();
    }

    @FXML
    private void actualizarContraseña() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fx/ActualizarPassword.fxml"));
            StackPane panel = loader.load();
            Stage modal = new Stage();
            modal.initOwner(primaryStage);
            modal.initStyle(StageStyle.UNDECORATED);
            modal.setScene(new Scene(panel));
            modal.showAndWait();
        } catch (Exception e) {
            AlertHelper.mostrar("Error", "No se pudo cargar la ventana.", AlertHelper.AlertType.ERROR);
        }
    }

   private void abrirCaja() {
    try {
        String impresoraSeleccionada = configManager.getImpresoraSeleccionada();
        if (impresoraSeleccionada == null || impresoraSeleccionada.isEmpty()) {
            System.out.println("⚠️ No hay impresora configurada.");
            return;
        }

        javax.print.PrintService impresora = null;
        for (javax.print.PrintService ps : javax.print.PrintServiceLookup.lookupPrintServices(null, null)) {
            if (ps.getName().equalsIgnoreCase(impresoraSeleccionada)) {
                impresora = ps;
                break;
            }
        }

        if (impresora == null) {
            System.out.println("❌ No se encontró la impresora configurada: " + impresoraSeleccionada);
            return;
        }

        // 🔸 Comando estándar ESC/POS para abrir cajón (PIN 2)
        byte[] openDrawerCmd = new byte[]{
            (byte) 27,   // ESC
            (byte) 112,  // p
            (byte) 0,    // pin 2
            (byte) 25,   // tiempo ON
            (byte) 250   // tiempo OFF
        };

        javax.print.DocPrintJob job = impresora.createPrintJob();
        javax.print.SimpleDoc doc = new javax.print.SimpleDoc(
                openDrawerCmd,
                javax.print.DocFlavor.BYTE_ARRAY.AUTOSENSE,
                null
        );
        job.print(doc, null);

        System.out.println("✅ Comando enviado a la impresora para abrir caja: " + impresora.getName());

    } catch (Exception e) {
        e.printStackTrace();
        System.err.println("❌ Error al intentar abrir la caja: " + e.getMessage());
    }
}


}
