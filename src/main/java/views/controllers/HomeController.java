package views.controllers;

import java.io.File;
import java.io.InputStream;

import helpers.AlertaHelper;
import helpers.Avatar;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
import services.navegador.apertura.KeyFacilMonitorService;
import services.navegador.apertura.VentaListenerService;
import services.navegador.impresion.ChromePrintService;
import services.tunnel.TunnelService; // 🔑 NUEVO: Importamos TunnelService
import views.controllers.config.ModoInteligentePrefs;
import views.controllers.config.SucursalPrefs;
import views.controllers.home.PrinterManager;
import views.controllers.home.TrayManager;
import views.icons.Icons;

public class HomeController {

        @FXML
        private AnchorPane root;
        @FXML
        private StackPane loadingCard;

        @FXML
        private ImageView imgLogo, imgUpdate, imgPrint, imgConfig;
        @FXML
        private ImageView imgInicio, imgPapelBond;

        @FXML
        private ComboBox<String> cmbImpresoras;
        @FXML
        private Button btnRefrescar, btnSeleccionar, btnActualizarContraseña;

        @FXML
        private Button btnInicio, btnPapelBond, btnConfig, btnSalirSidebar;

        @FXML
        private Text lblTitulo, lblEmpleado, lblSucursal;
        @FXML
        private ImageView imgFotoEmpleado;

        @FXML
        private Button btnSucursales;

        @FXML
        private ImageView imgSucursales;

        @FXML
        private Button btnAbrirNavegador;

        private Stage primaryStage;
        private double xOffset, yOffset;

        private ConfigManagerDB configManager;
        private HotkeyManager hotkeyManager;
        private PrinterManager printerManager;
        private TrayManager trayManager;

        private VentaListenerService ventaListenerService;
        private KeyFacilMonitorService keyFacilMonitor;
        private ChromePrintService chromePrintService;

        @FXML
        private StackPane contentArea;

        @FXML
        private VBox homeContent;

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

                actualizarBotonNavegador();
                actualizarBotonesModulos();

                System.out.println("✅ HomeController LISTO");
        }

        public void setEmpleadoActual(Empleado empleado) {
                if (empleado == null)
                        return;
                configManager.setEmpleadoActual(empleado);
                actualizarUIEmpleado();
        }

        private void verificarPinEmpleado() {
                if (configManager == null || configManager.getEmpleadoActual() == null)
                        return;
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
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fx/ActualizarPassword.fxml"));
                        StackPane panel = loader.load();
                        ActualizarPasswordController controller = loader.getController();

                        Stage modal = new Stage();
                        modal.initOwner(primaryStage);
                        modal.initStyle(StageStyle.UNDECORATED);
                        modal.initModality(Modality.APPLICATION_MODAL);

                        controller.setStage(modal);
                        modal.setScene(new Scene(panel));
                        modal.showAndWait();

                        configManager.recargarEmpleado();
                } catch (Exception e) {
                        e.printStackTrace();
                        AlertaHelper.mostrar("Error",
                                        "No se pudo cargar la ventana para crear el PIN.",
                                        AlertaHelper.AlertType.ERROR);
                }
        }

        private void actualizarUIEmpleado() {
                if (lblEmpleado != null) {
                        lblEmpleado.setText(configManager.getNombreCompletoEmpleado());
                }
                if (lblSucursal != null) {
                        Long idSel = SucursalPrefs.getId();
                        String nombreSel = SucursalPrefs.getNombre();

                        if (idSel != null && nombreSel != null && !nombreSel.isBlank()) {
                                lblSucursal.setText(nombreSel);
                        } else {
                                Sucursal suc = configManager.getSucursalActual();
                                lblSucursal.setText(suc != null ? suc.getNombre() : "Sin sucursal");
                        }
                }
                cargarFotoEmpleado(configManager.getEmpleadoActual());
        }

        private void cargarFotoEmpleado(Empleado empleado) {
                if (imgFotoEmpleado == null)
                        return;

                Image imagen = null;

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

                if (imagen == null || imagen.isError()) {
                        Avatar avatar = new Avatar();
                        try {
                                String url = avatar.getAvatarAleatorio();
                                imagen = new Image(url, 44, 44, true, true);
                                if (imagen.isError()) {
                                        imagen = avatar.getDefaultAvatar();
                                }
                        } catch (Exception e) {
                                imagen = avatar.getDefaultAvatar();
                        }
                }

                if (imagen == null) {
                        imagen = new Avatar().getDefaultAvatar();
                }

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

                hotkeyManager = new HotkeyManager(configManager, this::abrirCaja);
                hotkeyManager.startListening();

                // 🔑 NUEVO: Iniciar túnel si alguna de las dos opciones está activa
                boolean modoInteligente = ModoInteligentePrefs.isModoInteligenteActivo();
                boolean impresionInteligente = ModoInteligentePrefs.isImpresionInteligenteActiva();

                if (modoInteligente || impresionInteligente) {
                        System.out.println("🌐 Iniciando CDP Tunnel desde HomeController...");
                        TunnelService.iniciar();
                } else {
                        System.out.println("🌐 Modos inteligentes apagados → CDP Tunnel no iniciado");
                        TunnelService.detener();
                }

                if (modoInteligente) {
                        ventaListenerService = new VentaListenerService(this::abrirCaja);
                        keyFacilMonitor = new KeyFacilMonitorService(ventaListenerService);
                        keyFacilMonitor.start();
                        System.out.println("✅ Modo Inteligente ACTIVO → monitor iniciado");
                } else {
                        System.out.println("⚠️ Modo Inteligente DESACTIVADO → monitor NO iniciado");
                }

                if (impresionInteligente) {
                        chromePrintService = new ChromePrintService(configManager);
                        chromePrintService.start();
                        System.out.println("✅ Impresión Inteligente ACTIVA → ChromePrintService iniciado");
                } else {
                        System.out.println("⚠️ Impresión Inteligente DESACTIVADA → ChromePrintService NO iniciado");
                }

                System.out.println("✅ Servicios listos: Hotkey"
                                + (keyFacilMonitor != null ? " + Monitor KeyFacil" : " (sin monitor)")
                                + (chromePrintService != null ? " + PrintService" : " (sin print)"));
        }

        private void actualizarBotonNavegador() {
                if (btnAbrirNavegador == null)
                        return;
                boolean activo = ModoInteligentePrefs.isModoInteligenteActivo();
                btnAbrirNavegador.setVisible(activo);
                btnAbrirNavegador.setManaged(activo);
                System.out.println("🌐 Botón navegador " + (activo ? "VISIBLE" : "OCULTO"));
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
                Icons.setImageIcons(imgUpdate, "icons/", "update.png", 18);
                Icons.setImageIcons(imgPrint, "icons/", "print.png", 35);
                Icons.setImageIcons(imgConfig, "icons/", "engranaje.png", 18);
                Icons.setImageIcons(imgSucursales, "icons/", "sucursal.png", 18);

                Icons.setImageIcons(imgInicio, "icons/", "home.png", 18);
                Icons.setImageIcons(imgPapelBond, "icons/", "boleto.png", 18);

                Icons.setButtonIcons(btnAbrirNavegador, "icons/", "chrome.png", 20);
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
                if (primaryStage != null)
                        primaryStage.setIconified(true);
        }

        @FXML
        private void salir() {
                AlertaHelper.confirmar("Salir",
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
                        AlertaHelper.mostrar("Error", "No se pudo cargar la ventana.", AlertaHelper.AlertType.ERROR);
                }
        }

        @FXML
        private void abrirConfiguracion() {
                try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fx/configuracion.fxml"));
                        javafx.scene.Parent panel = loader.load();
                        ConfiguracionController controller = loader.getController();

                        if (controller != null) {
                                controller.setOnGuardado(() -> {
                                        aplicarModoInteligente();
                                        actualizarBotonNavegador();
                                        actualizarBotonesModulos();
                                        mostrarHome();
                                });
                                controller.setOnCerrar(this::mostrarHome);
                        }

                        contentArea.getChildren().setAll(panel);

                } catch (Exception e) {
                        e.printStackTrace();
                        AlertaHelper.mostrar("Error",
                                        "No se pudo abrir la configuración: " + e.getMessage(),
                                        AlertaHelper.AlertType.ERROR);
                }
        }

        @FXML
        private void abrirControlPapelBond() {
                mostrarSeccion("/fx/control_isumo/papel_bond.fxml", "Control de Papel Bond");
        }

        private void mostrarSeccion(String fxmlPath, String titulo) {
                try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                        javafx.scene.Parent panel = loader.load();

                        HBox header = new HBox(15);
                        header.setAlignment(Pos.CENTER_LEFT);
                        header.setStyle(
                                        "-fx-padding: 15 25; " +
                                                        "-fx-background-color: #f8fafc; " +
                                                        "-fx-border-color: transparent transparent #e2e8f0 transparent; "
                                                        +
                                                        "-fx-border-width: 0 0 1 0;");

                        Button btnVolver = new Button("← Volver");
                        btnVolver.getStyleClass().add("elegant-btn-secondary");
                        btnVolver.setPrefHeight(36);
                        btnVolver.setOnAction(e -> mostrarHome());

                        Label lblTituloSeccion = new Label(titulo);
                        lblTituloSeccion.setStyle(
                                        "-fx-font-size: 20px; -fx-font-weight: 800; " +
                                                        "-fx-text-fill: #1e293b; -fx-font-family: 'Inter','Segoe UI', sans-serif;");

                        header.getChildren().addAll(btnVolver, lblTituloSeccion);

                        VBox wrapper = new VBox(0);
                        VBox.setVgrow(panel, Priority.ALWAYS);
                        wrapper.getChildren().addAll(header, panel);
                        wrapper.setStyle("-fx-background-color: #f1f5f9;");

                        contentArea.getChildren().setAll(wrapper);

                } catch (Exception e) {
                        e.printStackTrace();
                        AlertaHelper.mostrar(
                                        "Módulo en desarrollo",
                                        "El módulo '" + titulo + "' aún no está disponible.\nDetalle: "
                                                        + e.getMessage(),
                                        AlertaHelper.AlertType.INFO);
                }
        }

        @FXML
        private void mostrarHome() {
                contentArea.getChildren().setAll(homeContent);
        }

        private void aplicarModoInteligente() {
                boolean activo = ModoInteligentePrefs.isModoInteligenteActivo();
                boolean printActivo = ModoInteligentePrefs.isImpresionInteligenteActiva();

                // 🔑 NUEVO: Controlar el túnel en caliente cuando cambian la config
                if (activo || printActivo) {
                        TunnelService.iniciar();
                } else {
                        TunnelService.detener();
                }

                if (activo && keyFacilMonitor == null) {
                        ventaListenerService = new VentaListenerService(this::abrirCaja);
                        keyFacilMonitor = new KeyFacilMonitorService(ventaListenerService);
                        keyFacilMonitor.start();
                        System.out.println("🟢 Modo Inteligente ACTIVADO en caliente");
                } else if (!activo && keyFacilMonitor != null) {
                        try {
                                keyFacilMonitor.stop();
                        } catch (Exception ex) {
                                System.err.println("Error deteniendo monitor: " + ex.getMessage());
                        }
                        keyFacilMonitor = null;
                        ventaListenerService = null;
                        System.out.println("🔴 Modo Inteligente DESACTIVADO en caliente");
                }

                if (printActivo && chromePrintService == null) {
                        try {
                                chromePrintService = new ChromePrintService(configManager);
                                chromePrintService.start();
                                System.out.println("🟢 Impresión Inteligente ACTIVADA en caliente");
                        } catch (Exception ex) {
                                System.err.println("Error arrancando ChromePrintService: " + ex.getMessage());
                                chromePrintService = null;
                        }
                } else if (!printActivo && chromePrintService != null) {
                        try {
                                chromePrintService.stop();
                        } catch (Exception ex) {
                                System.err.println("Error deteniendo ChromePrintService: " + ex.getMessage());
                        }
                        chromePrintService = null;
                        System.out.println("🔴 Impresión Inteligente DESACTIVADA en caliente");
                }
        }

        @FXML
        private void cerrarSesion() {
                AlertaHelper.confirmar("Cerrar Sesión",
                                "¿Está seguro de que desea cerrar sesión y volver al login?",
                                () -> {
                                        try {
                                                // 1. Detener todos los servicios activos
                                                shutdownServices();

                                                // 2. Limpiar el TrayManager si existe método de cierre.
                                                // ⚠️ Ajusta según tu TrayManager real:
                                                // if (trayManager != null) trayManager.dispose();
                                                // if (trayManager != null) trayManager.removeTray();

                                                // 3. Cargar la pantalla de login
                                                FXMLLoader loader = new FXMLLoader(
                                                                getClass().getResource("/fx/login.fxml"));
                                                javafx.scene.Parent loginRoot = loader.load();

                                                Stage loginStage = new Stage();
                                                loginStage.setScene(new Scene(loginRoot));
                                                loginStage.initStyle(StageStyle.UNDECORATED);
                                                loginStage.show();

                                                // 4. Cerrar la ventana actual del home
                                                if (primaryStage != null) {
                                                        primaryStage.close();
                                                }

                                                System.out.println("🔐 Sesión cerrada, regresando al login.");

                                        } catch (Exception e) {
                                                e.printStackTrace();
                                                AlertaHelper.mostrar("Error",
                                                                "No se pudo cerrar sesión.\nDetalle: " + e.getMessage(),
                                                                AlertaHelper.AlertType.ERROR);
                                        }
                                });
        }

        @FXML
        private void abrirSucursales() {
                try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fx/sucursales.fxml"));
                        javafx.scene.Parent panel = loader.load();
                        SucursalesController ctrl = loader.getController();

                        if (ctrl != null) {
                                ctrl.setOnSucursalSeleccionada(suc -> {
                                        System.out.println("Sucursal confirmada: " + suc.getNombre());
                                        actualizarUIEmpleado();
                                        mostrarHome();
                                });
                        }

                        HBox header = new HBox(15);
                        header.setAlignment(Pos.CENTER_LEFT);
                        header.setStyle(
                                        "-fx-padding: 15 25; " +
                                                        "-fx-background-color: #f8fafc; " +
                                                        "-fx-border-color: transparent transparent #e2e8f0 transparent; "
                                                        +
                                                        "-fx-border-width: 0 0 1 0;");

                        Button btnVolver = new Button("← Volver");
                        btnVolver.getStyleClass().add("elegant-btn-secondary");
                        btnVolver.setPrefHeight(36);
                        btnVolver.setOnAction(e -> mostrarHome());

                        Label lblTituloSeccion = new Label("Sucursales");
                        lblTituloSeccion.setStyle(
                                        "-fx-font-size: 20px; -fx-font-weight: 800; " +
                                                        "-fx-text-fill: #1e293b; " +
                                                        "-fx-font-family: 'Inter','Segoe UI', sans-serif;");

                        header.getChildren().addAll(btnVolver, lblTituloSeccion);

                        VBox wrapper = new VBox(0);
                        VBox.setVgrow(panel, Priority.ALWAYS);
                        wrapper.getChildren().addAll(header, panel);
                        wrapper.setStyle("-fx-background-color: #f1f5f9;");

                        contentArea.getChildren().setAll(wrapper);

                } catch (Exception e) {
                        e.printStackTrace();
                        AlertaHelper.mostrar("Error",
                                        "No se pudo abrir Sucursales: " + e.getMessage(),
                                        AlertaHelper.AlertType.ERROR);
                }
        }

        private void actualizarBotonesModulos() {
                boolean papelBondActivo = ModoInteligentePrefs.isModuloPapelBondActivo();

                btnPapelBond.setVisible(papelBondActivo);
                btnPapelBond.setManaged(papelBondActivo);

                System.out.println("🔧 Sidebar → Papel Bond: "
                                + (papelBondActivo ? "VISIBLE" : "OCULTO"));
        }

        private void abrirCaja() {
                printerManager.abrirCaja();
        }

        private void cargarImpresoraGuardada() {
                printerManager.cargarImpresoraGuardada(cmbImpresoras);
        }

        public void setPrimaryStage(Stage stage) {
                this.primaryStage = stage;
                trayManager = new TrayManager(primaryStage);
                trayManager.setHotkeyManager(hotkeyManager);
                trayManager.setShutdownCallback(this::shutdownServices);

                primaryStage.setOnCloseRequest(e -> {
                        e.consume();
                        if (!configManager.tienePin()) {
                                abrirVentanaCrearPin();
                        } else {
                                trayManager.ocultarEnBandeja();
                        }
                });

                iniciarListenerInstanciaUnica();
                verificarPinEmpleado();
        }

        private void iniciarListenerInstanciaUnica() {
                Thread t = new Thread(() -> {
                        try (java.net.ServerSocket ss = new java.net.ServerSocket(45821)) {
                                while (!ss.isClosed()) {
                                        try (java.net.Socket s = ss.accept()) {
                                                Platform.runLater(() -> {
                                                        if (primaryStage != null && primaryStage.isShowing()) {
                                                                primaryStage.show();
                                                                if (primaryStage.isIconified())
                                                                        primaryStage.setIconified(false);
                                                                primaryStage.toFront();
                                                                primaryStage.requestFocus();
                                                        }
                                                });
                                        }
                                }
                        } catch (Exception e) {
                                System.err.println("Listener de instancia única detenido: " + e.getMessage());
                        }
                }, "instance-listener");
                t.setDaemon(true);
                t.start();
        }

        public void shutdownServices() {
                try {
                        if (keyFacilMonitor != null)
                                keyFacilMonitor.stop();
                        if (chromePrintService != null)
                                chromePrintService.stop();
                        if (hotkeyManager != null)
                                hotkeyManager.stopListening();

                        // 🔑 NUEVO: Detener el túnel al cerrar la app
                        TunnelService.detener();
                } catch (Exception e) {
                        System.err.println("Error cerrando servicios: " + e.getMessage());
                }
        }

        @FXML
        private void abrirNavegador() {
                if (!ModoInteligentePrefs.isModoInteligenteActivo()) {
                        AlertaHelper.mostrar("Modo Inteligente desactivado",
                                        "Activa el Modo Inteligente en configuración para usar el navegador.",
                                        AlertaHelper.AlertType.WARNING);
                        return;
                }
                if (keyFacilMonitor == null) {
                        AlertaHelper.mostrar("Error", "El monitor aún no está listo.", AlertaHelper.AlertType.WARNING);
                        return;
                }
                keyFacilMonitor.abrirNavegadorKeyFacil();
        }
}