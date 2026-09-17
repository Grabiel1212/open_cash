package views.controllers;

import java.io.File;
import java.io.InputStream;

import helpers.Avatar;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import model.Empleado;
import model.Sucursal;
import services.cash.ConfigManagerDB;
import services.cash.HotkeyManager;
import services.navegador.KeyFacilMonitorService;
import services.navegador.VentaListenerService;
import views.alerts.AlertHelper;
import views.controllers.home.PrinterManager;
import views.controllers.home.TrayManager;
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
        private Text lblTitulo, lblEmpleado, lblSucursal;
        @FXML
        private ImageView imgFotoEmpleado;

        @FXML
        private Button btnAbrirNavegador;

        private Stage primaryStage;
        private double xOffset, yOffset;

        private ConfigManagerDB configManager;
        private HotkeyManager hotkeyManager;
        private PrinterManager printerManager;
        private TrayManager trayManager;

        // 🔽 NUEVOS CAMPOS
        private VentaListenerService ventaListenerService;
        private KeyFacilMonitorService keyFacilMonitor;

        @FXML
        public void initialize() {
                System.out.println("🚀 INICIANDO HOME CONTROLLER...");
                Platform.setImplicitExit(false);

                setupDragAndDrop();
                loadIcons();
                setupAnimations();
                initializeServices();
                cargarImpresoraGuardada();
                refrescarImpresoras();

                // ❌ ELIMINADO: verificarPinEmpleado();
                System.out.println("✅ HomeController LISTO");
        }

        public void setEmpleadoActual(Empleado empleado) {
                if (empleado == null)
                        return;
                configManager.setEmpleadoActual(empleado);
                actualizarUIEmpleado();
        }

        private void verificarPinEmpleado() {
                if (configManager == null || configManager.getEmpleadoActual() == null) {
                        return;
                }
                new Thread(() -> {
                        boolean tienePin = configManager.tienePin();
                        Platform.runLater(() -> {
                                if (!tienePin) {
                                        System.out.println(
                                                        "⚠️ El empleado no tiene PIN. Abriendo ventana para crearlo...");
                                        abrirVentanaCrearPin();
                                } else {
                                        System.out.println("✅ El empleado ya tiene PIN.");
                                }
                        });
                }).start();
        }

        private void abrirVentanaCrearPin() {
                try {
                        FXMLLoader loader = new FXMLLoader(
                                        getClass().getResource("/fx/ActualizarPassword.fxml"));
                        StackPane panel = loader.load();
                        ActualizarPasswordController controller = loader.getController();

                        Stage modal = new Stage();
                        modal.initOwner(primaryStage);
                        modal.initStyle(StageStyle.UNDECORATED);
                        modal.initModality(Modality.APPLICATION_MODAL);

                        controller.setStage(modal);
                        modal.setScene(new Scene(panel));
                        modal.showAndWait();

                        // Recargar empleado para actualizar el estado del PIN
                        configManager.recargarEmpleado();

                } catch (Exception e) {
                        e.printStackTrace();
                        AlertHelper.mostrar("Error",
                                        "No se pudo cargar la ventana para crear el PIN.",
                                        AlertHelper.AlertType.ERROR);
                }
        }

        private void actualizarUIEmpleado() {
                if (lblEmpleado != null) {
                        lblEmpleado.setText(configManager.getNombreCompletoEmpleado());
                }
                if (lblSucursal != null) {
                        Sucursal suc = configManager.getSucursalActual();
                        lblSucursal.setText(suc != null ? suc.getNombre() : "Sin sucursal");
                }
                cargarFotoEmpleado(configManager.getEmpleadoActual());
        }

        private void cargarFotoEmpleado(Empleado empleado) {
                if (imgFotoEmpleado == null)
                        return;

                Image imagen = null;

                // 1. Intentar cargar foto personal del empleado
                if (empleado != null && empleado.getFoto() != null && !empleado.getFoto().isEmpty()) {
                        try {
                                File file = new File(empleado.getFoto());
                                if (file.exists()) {
                                        imagen = new Image(file.toURI().toString(), 44, 44, true, true);
                                } else {
                                        try (InputStream is = getClass().getResourceAsStream(empleado.getFoto())) {
                                                if (is != null) {
                                                        imagen = new Image(is, 44, 44, true, true);
                                                }
                                        }
                                }
                        } catch (Exception e) {
                                System.err.println("Error cargando foto del empleado: " + e.getMessage());
                        }
                }

                // 2. Si no hay foto o falló, intentar avatar aleatorio de DiceBear
                if (imagen == null || imagen.isError()) {
                        Avatar avatar = new Avatar();
                        try {
                                String url = avatar.getAvatarAleatorio();
                                imagen = new Image(url, 44, 44, true, true);
                                // Si falla, usar el avatar por defecto local
                                if (imagen.isError()) {
                                        imagen = avatar.getDefaultAvatar();
                                }
                        } catch (Exception e) {
                                // Cualquier error, usar el default
                                imagen = avatar.getDefaultAvatar();
                        }
                }

                // 3. Fallback extremo (nunca debería ser null)
                if (imagen == null) {
                        imagen = new Avatar().getDefaultAvatar();
                }

                // Asignar la imagen y aplicar el clip circular
                imgFotoEmpleado.setImage(imagen);
                aplicarClipCircular();
        }

        private void aplicarClipCircular() {
                double radius = Math.min(imgFotoEmpleado.getFitWidth(), imgFotoEmpleado.getFitHeight()) / 2;
                Circle clip = new Circle(radius, radius, radius);
                imgFotoEmpleado.setClip(clip);
        }

        private void initializeServices() {
                configManager = new ConfigManagerDB();
                actualizarUIEmpleado();
                printerManager = new PrinterManager(configManager);

                // =====================================================
                // HOTKEY (Ctrl+PIN+Win)
                // =====================================================
                hotkeyManager = new HotkeyManager(configManager, this::abrirCaja);
                hotkeyManager.startListening();

                // =====================================================
                // MONITOR KEYFACIL (CDP, sin lag) — arranca al entrar al Home
                // =====================================================
                ventaListenerService = new VentaListenerService(this::abrirCaja);
                keyFacilMonitor = new KeyFacilMonitorService(ventaListenerService);
                keyFacilMonitor.start();

                System.out.println("✅ Servicios listos: Hotkey + Monitor KeyFacil");
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

                Icons.setImageIcons(imgLogo, "logo/", "libro_logo.png", 35);
                Icons.setImageIcons(imgUpdate, "icons/", "update.png", 14);
                Icons.setImageIcons(imgPrint, "icons/", "print.png", 35);
        }

        private void setupAnimations() {
                FadeTransition fadeIn = new FadeTransition(Duration.millis(800), root);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                fadeIn.play();
        }

        @FXML
        private void cerrarVentana() {
                if (!configManager.tienePin()) {
                        abrirVentanaCrearPin();
                        return;
                }
                trayManager.ocultarEnBandeja();
        }

        @FXML
        private void minimizarVentana() {
                if (primaryStage != null) {
                        primaryStage.setIconified(true);
                }
        }

        // ════════════════════════════════════════════════════════════════
        // NUEVO MÉTODO: SALIR DE LA APLICACIÓN
        // ════════════════════════════════════════════════════════════════
        @FXML
        private void salir() {
                AlertHelper.confirmar(
                                "Salir",
                                "¿Está seguro de que desea salir de la aplicación?",
                                () -> Platform.exit());
        }

        @FXML
        private void refrescarImpresoras() {
                printerManager.refrescarImpresoras(cmbImpresoras, loadingCard);
        }

        @FXML
        private void seleccionarImpresora() {
                printerManager.seleccionarImpresora(cmbImpresoras);
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
                printerManager.abrirCaja();
        }

        private void cargarImpresoraGuardada() {
                printerManager.cargarImpresoraGuardada(cmbImpresoras);
        }

        // Verificación SOLO aquí (una sola vez)
        public void setPrimaryStage(Stage stage) {
                this.primaryStage = stage;
                trayManager = new TrayManager(primaryStage);
                trayManager.setHotkeyManager(hotkeyManager);

                // 🔽 Inyectar shutdown compartido
                trayManager.setShutdownCallback(this::shutdownServices);

                primaryStage.setOnCloseRequest(e -> {
                        e.consume();
                        if (!configManager.tienePin()) {
                                abrirVentanaCrearPin();
                        } else {
                                trayManager.ocultarEnBandeja();
                        }
                });

                verificarPinEmpleado();
        }

        public void shutdownServices() {
                try {
                        if (keyFacilMonitor != null)
                                keyFacilMonitor.stop();
                        if (hotkeyManager != null)
                                hotkeyManager.stopListening();
                } catch (Exception e) {
                        System.err.println("Error cerrando servicios: " + e.getMessage());
                }
        }

        @FXML
        private void abrirNavegador() {
                try {
                        if (keyFacilMonitor == null) {
                                AlertHelper.mostrar("Error",
                                                "El monitor de KeyFacil aún no está listo.",
                                                AlertHelper.AlertType.WARNING);
                                return;
                        }

                        // Lanzar en background para no bloquear la UI
                        keyFacilMonitor.abrirNavegadorKeyFacil();

                } catch (Exception e) {
                        e.printStackTrace();
                        AlertHelper.mostrar("Error",
                                        "No se pudo abrir el navegador: " + e.getMessage(),
                                        AlertHelper.AlertType.ERROR);
                }
        }
}