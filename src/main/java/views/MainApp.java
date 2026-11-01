package views;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import repository.ControlImpresionRepository;
import services.insumo.AlertaContadorService;
import services.sql.ControlmpresionService;
import services.text_vos.TextPeechService;
// import services.tunnel.TunnelService; // ❌ Ya no se usa aquí
import views.controllers.config.CierrePendientePrefs;
import views.controllers.config.ModoInteligentePrefs;
import views.controllers.config.SucursalPrefs;

public class MainApp extends Application {

    private static final int PUERTO_INSTANCIA = 45821;
    private static ServerSocket serverSocket;

    private static MainApp instancia;
    private static final ControlImpresionRepository CONTROL_REPO = new ControlmpresionService();

    private Stage loginStage;
    private Stage registroStage;
    private PauseTransition esperaRegistro;
    private PauseTransition reaperturaRegistro;

    private volatile boolean cierreSolicitadoPorUsuario = false;

    @Override
    public void start(Stage stage) throws Exception {

        instancia = this;

        // ❌ SE ELIMINÓ configurarTunnelSegunModos(); de aquí

        var urlLogin = getClass().getResource("/fx/login.fxml");
        if (urlLogin == null) {
            throw new RuntimeException("No se encontró el FXML: /fx/login.fxml");
        }

        FXMLLoader loaderLogin = new FXMLLoader(urlLogin);
        Scene sceneLogin = new Scene(loaderLogin.load());

        loginStage = stage;
        loginStage.setTitle("Login - OpenCash");
        loginStage.setScene(sceneLogin);
        loginStage.initStyle(StageStyle.UNDECORATED);

        cargarIcono(loginStage);
        loginStage.show();

        loginStage.setOnCloseRequest(event -> {
            if (!puedeCerrarAplicacion()) {
                event.consume();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdownSistema));

        Platform.runLater(this::verificarControlAbiertoAlArrancar);

        Platform.setImplicitExit(false);

        programarAperturaAutomatica();
    }

    // ❌ SE ELIMINÓ EL MÉTODO configurarTunnelSegunModos() COMPLETO

    private boolean hayControlAbierto() {
        Long idSucursal = SucursalPrefs.getId();
        if (idSucursal == null) {
            return false;
        }
        return CONTROL_REPO.ExisteAbiertoPorSucursal(idSucursal);
    }

    private boolean puedeCerrarAplicacion() {
        Long idSucursal = SucursalPrefs.getId();
        if (idSucursal == null) {
            return true;
        }
        if (!CONTROL_REPO.ExisteAbiertoPorSucursal(idSucursal)) {
            return true;
        }
        alertarCierreBloqueado(idSucursal);
        return false;
    }

    private void alertarCierreBloqueado(Long idSucursal) {
        new Thread(() -> {
            try {
                AlertaContadorService.reproducirAlarmaUnaVez();
                TextPeechService.hablar("Atención. Conteo abierto. Registre el cierre final.");
            } catch (Exception ignored) {
            }
        }, "alerta-cierre-bloqueado").start();

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Cierre bloqueado");
        alert.setHeaderText("Hay un conteo ABIERTO sin finalizar");
        alert.setContentText("Debe registrar el conteo final antes de salir.\n\n¿Registrar el cierre ahora?");

        ButtonType btSi = new ButtonType("Registrar cierre ahora", ButtonBar.ButtonData.OK_DONE);
        ButtonType btNo = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btSi, btNo);

        if (loginStage != null) {
            alert.initOwner(loginStage);
        }
        alert.setOnShown(ev -> {
            if (alert.getDialogPane().getScene() != null
                    && alert.getDialogPane().getScene().getWindow() instanceof Stage s) {
                s.setAlwaysOnTop(true);
                s.toFront();
            }
        });

        Optional<ButtonType> r = alert.showAndWait();
        if (r.isPresent() && r.get() == btSi) {
            cierreSolicitadoPorUsuario = true;
            abrirRegistroFinalForzado();
        }
    }

    private void abrirRegistroFinalForzado() {
        try {
            var url = getClass().getResource("/fx/control_isumo/registro_final.fxml");
            if (url == null) {
                System.err.println("No se encontró registro_final.fxml");
                cierreSolicitadoPorUsuario = false;
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Scene scene = new Scene(loader.load());

            Stage modal = new Stage();
            modal.setTitle("Cierre obligatorio del conteo");
            modal.initStyle(StageStyle.TRANSPARENT);
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setScene(scene);
            modal.setAlwaysOnTop(true);
            cargarIcono(modal);

            modal.setOnHidden(e -> verificarCierreTrasRegistroFinal(20));

            modal.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            cierreSolicitadoPorUsuario = false;
        }
    }

    private void verificarCierreTrasRegistroFinal(int intentosRestantes) {
        Long idSucursal = SucursalPrefs.getId();
        boolean yaCerrado = idSucursal != null && !CONTROL_REPO.ExisteAbiertoPorSucursal(idSucursal);

        if (yaCerrado) {
            CierrePendientePrefs.limpiar();
            System.out.println("✅ Conteo cerrado correctamente");

            if (cierreSolicitadoPorUsuario) {
                cierreSolicitadoPorUsuario = false;
                System.out.println("🚪 El usuario pidió cerrar → saliendo de la app");
                salirDeLaAplicacion();
            } else {
                System.out.println("ℹ️ Conteo cerrado desde el arranque → la app sigue abierta");
            }
            return;
        }

        if (intentosRestantes <= 0) {
            System.out.println("⚠️ Timeout esperando el cierre del conteo → la app sigue abierta");
            cierreSolicitadoPorUsuario = false;
            return;
        }

        PauseTransition reintento = new PauseTransition(Duration.millis(400));
        reintento.setOnFinished(ev -> verificarCierreTrasRegistroFinal(intentosRestantes - 1));
        reintento.play();
    }

    private void salirDeLaAplicacion() {
        if (loginStage != null) {
            loginStage.setOnCloseRequest(null);
        }
        Platform.exit();
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            System.exit(0);
        }, "exit-forzado").start();
    }

    private void onShutdownSistema() {
        try {
            Long idSucursal = SucursalPrefs.getId();
            if (idSucursal == null) {
                return;
            }

            if (CONTROL_REPO.ExisteAbiertoPorSucursal(idSucursal)) {
                CierrePendientePrefs.marcar(idSucursal);
                System.out.println("⚠️ Apagado con control ABIERTO → marcado pendiente");

                Thread audio = new Thread(() -> {
                    try {
                        AlertaContadorService.reproducirAlarmaUnaVez();
                        TextPeechService.hablar("Atención. Conteo abierto.");
                    } catch (Exception ignored) {
                    }
                }, "audio-apagado");
                audio.setDaemon(true);
                audio.start();
                audio.join(8000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void verificarControlAbiertoAlArrancar() {
        Long idSucursal = SucursalPrefs.getId();
        if (idSucursal == null) {
            return;
        }

        boolean hayAbierto = CONTROL_REPO.ExisteAbiertoPorSucursal(idSucursal);
        boolean hayFlagPendiente = CierrePendientePrefs.getIdSucursalPendiente() != null;

        if (hayAbierto) {
            System.out.println("⚠️ Conteo ABIERTO detectado al arrancar");
            new Thread(() -> {
                try {
                    AlertaContadorService.reproducirAlarmaUnaVez();
                    TextPeechService.hablar("Atención. Conteo abierto. Registre el cierre final.");
                } catch (Exception ignored) {
                }
            }, "alerta-inicio").start();

            Platform.runLater(this::abrirRegistroFinalForzado);
        }

        if (hayFlagPendiente) {
            CierrePendientePrefs.limpiar();
        }
    }

    public static void programarAperturaAutomatica() {
        if (instancia == null) {
            return;
        }
        if (instancia.esperaRegistro != null) {
            instancia.esperaRegistro.stop();
            instancia.esperaRegistro = null;
        }
        if (!ModoInteligentePrefs.isModuloPapelBondActivo()) {
            System.out.println("⚠️ Papel Bond DESACTIVADO → registro NO programado");
            return;
        }
        System.out.println("⏱ Papel Bond ACTIVO → registro programado en 60s");
        instancia.esperaRegistro = new PauseTransition(Duration.seconds(60));
        instancia.esperaRegistro.setOnFinished(event -> instancia.abrirRegistro());
        instancia.esperaRegistro.play();
    }

    private void abrirRegistro() {
        if (!ModoInteligentePrefs.isModuloPapelBondActivo()) {
            System.out.println("⚠️ Papel Bond desactivado al momento de abrir → registro cancelado");
            return;
        }
        if (hayControlAbierto()) {
            System.out.println("🔒 Ya existe un control ABIERTO → no se lanza el registro inicial");
            return;
        }
        if (registroStage != null && registroStage.isShowing()) {
            return;
        }
        try {
            var urlRegistro = getClass().getResource("/fx/control_isumo/registro.fxml");
            if (urlRegistro == null) {
                System.err.println("No se encontró el FXML: /fx/control_isumo/registro.fxml");
                return;
            }
            FXMLLoader loaderRegistro = new FXMLLoader(urlRegistro);
            Scene sceneRegistro = new Scene(loaderRegistro.load());
            registroStage = new Stage();
            registroStage.setTitle("Registro inicial del contador");
            registroStage.setScene(sceneRegistro);
            registroStage.initStyle(StageStyle.UNDECORATED);
            cargarIcono(registroStage);
            registroStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void programarReaperturaRegistro(int minutos) {
        if (instancia == null) {
            System.err.println("⚠️ MainApp no inicializado → snooze ignorado");
            return;
        }
        if (instancia.reaperturaRegistro != null) {
            instancia.reaperturaRegistro.stop();
        }
        System.out.println("💤 Snooze: reapertura del registro programada en " + minutos + " min");
        instancia.reaperturaRegistro = new PauseTransition(Duration.minutes(minutos));
        instancia.reaperturaRegistro.setOnFinished(e -> {
            if (!ModoInteligentePrefs.isModuloPapelBondActivo()) {
                System.out.println("⚠️ Papel Bond desactivado → reapertura cancelada");
                return;
            }
            if (instancia.hayControlAbierto()) {
                System.out.println("🔒 Ya existe un control ABIERTO → snooze cancelado");
                return;
            }
            System.out.println("🔔 Reabriendo registro y alarma tras snooze");
            instancia.abrirRegistro();
        });
        instancia.reaperturaRegistro.play();
    }

    public static void cancelarReaperturaRegistro() {
        if (instancia == null) {
            return;
        }
        if (instancia.reaperturaRegistro != null) {
            System.out.println("⏹ Reapertura del registro cancelada (ya hay conteo guardado)");
            instancia.reaperturaRegistro.stop();
            instancia.reaperturaRegistro = null;
        }
    }

    private void cargarIcono(Stage stage) {
        var iconStream = getClass().getResourceAsStream("/images/logo/libro_logo.png");
        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        }
    }

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PUERTO_INSTANCIA);
        } catch (IOException e) {
            try (Socket socket = new Socket("127.0.0.1", PUERTO_INSTANCIA)) {
                socket.getOutputStream().write(1);
                socket.getOutputStream().flush();
            } catch (IOException ignored) {
            }
            System.out.println("OpenCash ya está ejecutándose.");
            return;
        }
        launch(args);
    }

    @Override
    public void stop() {
        cierreSolicitadoPorUsuario = false;
        if (esperaRegistro != null) {
            esperaRegistro.stop();
            esperaRegistro = null;
        }
        if (reaperturaRegistro != null) {
            reaperturaRegistro.stop();
            reaperturaRegistro = null;
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}