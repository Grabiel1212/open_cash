package views.controllers.control_insumo;

import helpers.AlertaHelper;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import services.insumo.AlertaContadorService;
import services.insumo.ControlInsumoService;
import services.sql.ControlmpresionService;
import views.MainApp;
import views.controllers.config.SucursalPrefs;
import views.icons.Icons;

public class RegistroInsumoController {

    /* --- Ventana --- */
    @FXML
    private StackPane rootStack;
    @FXML
    private VBox rootVentana;
    @FXML
    private HBox barraTitulo;
    @FXML
    private Button btnCerrar;

    /* --- Formulario --- */
    @FXML
    private CheckBox chkRicoh;
    @FXML
    private CheckBox chkEpson;
    @FXML
    private VBox panelRicoh;
    @FXML
    private VBox panelEpson;

    @FXML
    private TextField txtRicohBn;
    @FXML
    private TextField txtRicohColor;
    @FXML
    private TextField txtEpsonBn;
    @FXML
    private TextField txtEpsonColor;

    @FXML
    private Label lblRicohTotal;
    @FXML
    private Label lblEpsonTotal;
    @FXML
    private Button btnGuardar;
    @FXML
    private Button btnExtender;

    /* --- Guía + overlay --- */
    @FXML
    private StackPane contenedorImg;
    @FXML
    private ImageView imgGuia;
    @FXML
    private StackPane overlayGuia;
    @FXML
    private StackPane contenedorImgGrande;
    @FXML
    private ImageView imgGuiaGrande;

    /* --- Silenciar --- */
    @FXML
    private Button btnSilenciar;

    /* --- Servicios --- */
    private final ControlInsumoService controlInsumoService = ControlInsumoService.getInstance();
    private final AlertaContadorService alertaContador = new AlertaContadorService();
    private final ControlmpresionService controlImpresionService = new ControlmpresionService();

    private boolean cierreBloqueado = false;
    private boolean alarmaSilenciada = false;

    /* --- Arrastre de ventana --- */
    private double offsetX;
    private double offsetY;
    private boolean arrastrando = false;

    private static final int IMG_ANCHO_CARGA = 900;
    private static final int MINUTOS_SNOOZE = 5;

    // ================================================================
    // prepararEscenario()
    // ================================================================
    public static void prepararEscenario(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
    }

    // ================================================================
    // INIT
    // ================================================================
    @FXML
    public void initialize() {

        txtRicohBn.setTextFormatter(formatterSoloDigitos());
        txtRicohColor.setTextFormatter(formatterSoloDigitos());
        txtEpsonBn.setTextFormatter(formatterSoloDigitos());
        txtEpsonColor.setTextFormatter(formatterSoloDigitos());

        Icons.setImageIcons(imgGuia, "icons/", "guia.png", IMG_ANCHO_CARGA);
        Icons.setImageIcons(imgGuiaGrande, "icons/", "guia.png", IMG_ANCHO_CARGA);

        chkRicoh.selectedProperty().addListener((obs, old, sel) -> {
            panelRicoh.setDisable(!sel);
            if (sel) {
                txtRicohBn.requestFocus();
            } else {
                txtRicohBn.clear();
                txtRicohColor.clear();
                actualizarTotales();
            }
        });
        chkEpson.selectedProperty().addListener((obs, old, sel) -> {
            panelEpson.setDisable(!sel);
            if (sel) {
                txtEpsonBn.requestFocus();
            } else {
                txtEpsonBn.clear();
                txtEpsonColor.clear();
                actualizarTotales();
            }
        });

        txtRicohBn.textProperty().addListener((o, a, b) -> actualizarTotales());
        txtRicohColor.textProperty().addListener((o, a, b) -> actualizarTotales());
        txtEpsonBn.textProperty().addListener((o, a, b) -> actualizarTotales());
        txtEpsonColor.textProperty().addListener((o, a, b) -> actualizarTotales());

        txtRicohBn.setOnAction(e -> guardarLectura());
        txtRicohColor.setOnAction(e -> guardarLectura());
        txtEpsonBn.setOnAction(e -> guardarLectura());
        txtEpsonColor.setOnAction(e -> guardarLectura());

        boolean algoGuardado = false;

        if (controlInsumoService.tieneRicoh()) {
            chkRicoh.setSelected(true);
            chkRicoh.setDisable(true);
            panelRicoh.setDisable(false);
            txtRicohBn.setText(Long.toString(controlInsumoService.getRicohBn()));
            txtRicohColor.setText(Long.toString(controlInsumoService.getRicohColor()));
            txtRicohBn.setDisable(true);
            txtRicohColor.setDisable(true);
            algoGuardado = true;
        }
        if (controlInsumoService.tieneEpson()) {
            chkEpson.setSelected(true);
            chkEpson.setDisable(true);
            panelEpson.setDisable(false);
            txtEpsonBn.setText(Long.toString(controlInsumoService.getEpsonBn()));
            txtEpsonColor.setText(Long.toString(controlInsumoService.getEpsonColor()));
            txtEpsonBn.setDisable(true);
            txtEpsonColor.setDisable(true);
            algoGuardado = true;
        }

        actualizarTotales();

        if (controlInsumoService.tieneRicoh() && controlInsumoService.tieneEpson()) {
            btnGuardar.setDisable(true);
            cierreBloqueado = false;
        } else if (!algoGuardado) {
            alertaContador.iniciar();
            cierreBloqueado = true;
        } else {
            cierreBloqueado = false;
        }

        actualizarVisibilidadExtender();

        Platform.runLater(this::configurarVentana);
    }

    /**
     * Visibilidad de los botones de la barra inferior y de la cabecera.
     * - btnExtender: visible mientras haya bloqueo (silenciado o no)
     * - btnSilenciar: visible solo si hay bloqueo Y la alarma sigue sonando
     */
    private void actualizarVisibilidadExtender() {
        // [Extender 5 min] visible mientras haya bloqueo
        btnExtender.setVisible(cierreBloqueado);
        btnExtender.setManaged(cierreBloqueado);

        // [Silenciar] visible solo si hay bloqueo Y el bucle sigue sonando
        boolean alarmaSonando = cierreBloqueado && !alarmaSilenciada;
        btnSilenciar.setVisible(alarmaSonando);
        btnSilenciar.setManaged(alarmaSonando);
    }

    // ================================================================
    // VENTANA + BINDINGS DE IMAGEN
    // ================================================================
    private void configurarVentana() {
        Stage stage = ventana();
        if (stage == null)
            return;

        if (rootVentana.getScene() != null) {
            rootVentana.getScene().setFill(Color.TRANSPARENT);
            instalarAtajos(rootVentana.getScene());
        }

        stage.setAlwaysOnTop(true);
        stage.setResizable(false);

        // X de la cabecera, Alt+F4 y cualquier cierre del SO → suspender 5 min si hay
        // bloqueo
        stage.setOnCloseRequest(event -> {
            if (cierreBloqueado) {
                event.consume();
                Platform.runLater(this::extenderCincoMinutos);
            }
        });

        imgGuia.fitWidthProperty().bind(
                Bindings.createDoubleBinding(
                        () -> Math.max(0, contenedorImg.getWidth() - 8),
                        contenedorImg.widthProperty()));
        imgGuia.fitHeightProperty().bind(
                Bindings.createDoubleBinding(
                        () -> Math.max(0, contenedorImg.getHeight() - 8),
                        contenedorImg.heightProperty()));
        imgGuiaGrande.fitWidthProperty().bind(
                Bindings.createDoubleBinding(
                        () -> Math.max(0, contenedorImgGrande.getWidth() - 8),
                        contenedorImgGrande.widthProperty()));
        imgGuiaGrande.fitHeightProperty().bind(
                Bindings.createDoubleBinding(
                        () -> Math.max(0, contenedorImgGrande.getHeight() - 8),
                        contenedorImgGrande.heightProperty()));

        if (cierreBloqueado) {
            stage.toFront();
            stage.requestFocus();
            chkRicoh.requestFocus();
        }
    }

    private void instalarAtajos(Scene scene) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.ESCAPE),
                this::cerrarOverlay);
    }

    private Stage ventana() {
        if (rootVentana == null || rootVentana.getScene() == null)
            return null;
        return (Stage) rootVentana.getScene().getWindow();
    }

    // ================================================================
    // ARRASTRE
    // ================================================================
    @FXML
    private void iniciarArrastre(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || e.getTarget() instanceof Button) {
            arrastrando = false;
            return;
        }
        Stage stage = ventana();
        if (stage == null) {
            arrastrando = false;
            return;
        }
        offsetX = e.getScreenX() - stage.getX();
        offsetY = e.getScreenY() - stage.getY();
        arrastrando = true;
    }

    @FXML
    private void arrastrarVentana(MouseEvent e) {
        if (!arrastrando)
            return;
        Stage stage = ventana();
        if (stage == null)
            return;
        stage.setX(e.getScreenX() - offsetX);
        stage.setY(e.getScreenY() - offsetY);
    }

    @FXML
    private void terminarArrastre(MouseEvent e) {
        arrastrando = false;
    }

    // ================================================================
    // OVERLAY
    // ================================================================
    @FXML
    private void clicImagenGuia(MouseEvent e) {
        if (e.getClickCount() >= 2)
            abrirOverlay();
    }

    private void abrirOverlay() {
        if (imgGuiaGrande.getImage() == null && imgGuia.getImage() != null) {
            imgGuiaGrande.setImage(imgGuia.getImage());
        }
        overlayGuia.setManaged(true);
        overlayGuia.setVisible(true);
        overlayGuia.toFront();
    }

    @FXML
    private void cerrarOverlay() {
        if (overlayGuia.isVisible()) {
            overlayGuia.setVisible(false);
            overlayGuia.setManaged(false);
        }
    }

    // ================================================================
    // SILENCIAR MANUAL
    // Solo calla el audio. NO cierra, NO desbloquea, NO programa snooze.
    // ================================================================
    @FXML
    private void silenciarAlarma() {
        if (alarmaSilenciada) {
            return;
        }
        alarmaSilenciada = true;

        // Detener el bucle SIN reproducir despedida
        alertaContador.silencioManual();

        // Recalcular botones: [Silenciar] se oculta, [Extender] y [Guardar] siguen
        actualizarVisibilidadExtender();
    }

    // ================================================================
    // SNOOZE: Extender 5 minutos
    // También se invoca desde X / Alt+F4 cuando hay bloqueo.
    // ================================================================
    @FXML
    private void extenderCincoMinutos() {
        // Siempre reproduce «Registro suspendido» (aunque estuviera silenciado)
        alertaContador.detenerConSuspension();

        cierreBloqueado = false;
        alarmaSilenciada = false;

        MainApp.programarReaperturaRegistro(MINUTOS_SNOOZE);

        cerrarVentana();
    }

    // ================================================================
    // X DE LA CABECERA → mismo comportamiento que Extender 5 min
    // ================================================================
    @FXML
    private void solicitarCierre() {
        if (cierreBloqueado) {
            // Suspendemos 5 min (igual que btnExtender)
            extenderCincoMinutos();
            return;
        }
        cerrarVentana();
    }

    private void cerrarVentana() {
        // Defensivo: si aún sonaba la alarma, apagarla antes de destruir el Stage.
        try {
            alertaContador.detener();
        } catch (Exception ignored) {
        }
        Stage stage = ventana();
        if (stage != null)
            stage.close();
    }

    // ================================================================
    // TOTALES
    // ================================================================
    private void actualizarTotales() {
        lblRicohTotal.setText("Total: " + String.format("%,d",
                parseSeguro(txtRicohBn) + parseSeguro(txtRicohColor)));
        lblEpsonTotal.setText("Total: " + String.format("%,d",
                parseSeguro(txtEpsonBn) + parseSeguro(txtEpsonColor)));
    }

    private long parseSeguro(TextField tf) {
        if (tf == null)
            return 0L;
        String t = tf.getText();
        if (t == null)
            return 0L;
        t = t.trim();
        if (t.isEmpty())
            return 0L;
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ================================================================
    // GUARDAR
    // ================================================================
    @FXML
    private void guardarLectura() {

        boolean quiereRicoh = chkRicoh.isSelected() && !controlInsumoService.tieneRicoh();
        boolean quiereEpson = chkEpson.isSelected() && !controlInsumoService.tieneEpson();

        if (!quiereRicoh && !quiereEpson) {
            // Ambas máquinas ya estaban registradas → solo cerramos.
            if (controlInsumoService.hayAlgunContador()) {
                cerrarVentana();
                return;
            }
            mostrarAlerta(Alert.AlertType.WARNING, "Sin selección",
                    "Selecciona al menos una máquina.");
            return;
        }

        long[] valRicoh = null;
        long[] valEpson = null;

        if (quiereRicoh) {
            valRicoh = validarMaquina(txtRicohBn, txtRicohColor, "Ricoh");
            if (valRicoh == null)
                return;
        }
        if (quiereEpson) {
            valEpson = validarMaquina(txtEpsonBn, txtEpsonColor, "Epson");
            if (valEpson == null)
                return;
        }

        // ----- 1) Validar sucursal ANTES de tocar memoria
        boolean necesitaPersistir = !controlInsumoService.tieneRegistroEnBD();
        Long idSucursal = null;
        if (necesitaPersistir) {
            idSucursal = SucursalPrefs.getId();
            if (idSucursal == null || idSucursal <= 0) {
                mostrarAlerta(Alert.AlertType.ERROR, "Sucursal no configurada",
                        "No hay una sucursal activa.\n" +
                                "Selecciona una sucursal antes de guardar el conteo inicial.");
                return;
            }
        }

        btnGuardar.setDisable(true);
        boolean exito = false;

        try {
            // ----- 2) Registrar en memoria
            if (valRicoh != null)
                controlInsumoService.registrarRicoh(valRicoh[0], valRicoh[1]);
            if (valEpson != null)
                controlInsumoService.registrarEpson(valEpson[0], valEpson[1]);

            // ----- 3) Crear el registro en BD una sola vez y guardar el ID
            if (necesitaPersistir) {
                long ricohTotal = controlInsumoService.getRicohTotal();
                long epsonTotal = controlInsumoService.getEpsonTotal();

                Long idControl = controlImpresionService.CrearConteoInicial(
                        idSucursal, ricohTotal, epsonTotal);

                if (idControl == null) {
                    throw new IllegalStateException(
                            "No se pudo crear el conteo inicial en la base de datos.");
                }

                controlInsumoService.setIdControlImpresion(idControl);

                System.out.println("✅ Conteo inicial creado  id=" + idControl
                        + "  sucursal=" + idSucursal
                        + "  ricoh=" + ricohTotal
                        + "  epson=" + epsonTotal);
            }

            // ----- 4) Cortar alarma + reproducir agradecimiento SIEMPRE
            // (aunque el usuario hubiera silenciado antes)
            cierreBloqueado = false;
            alarmaSilenciada = false;
            alertaContador.detenerConAgradecimiento();
            MainApp.cancelarReaperturaRegistro();

            // ----- 5) Mensaje de confirmación
            StringBuilder sb = new StringBuilder("Se registró correctamente:\n");
            if (valRicoh != null) {
                sb.append("• Ricoh  — B/N: ").append(String.format("%,d", valRicoh[0]))
                        .append(" · Color: ").append(String.format("%,d", valRicoh[1]))
                        .append("  (total ").append(String.format("%,d", valRicoh[0] + valRicoh[1]))
                        .append(")\n");
            }
            if (valEpson != null) {
                sb.append("• Epson — General: ").append(String.format("%,d", valEpson[0] + valEpson[1]))
                        .append("\n");
            }

            Long idMostrar = controlInsumoService.getIdControlImpresion();
            sb.append("\nID de control: ")
                    .append(idMostrar != null ? idMostrar.toString() : "—");

            exito = true;
            mostrarAlerta(Alert.AlertType.INFORMATION, "Lectura registrada", sb.toString());
            cerrarVentana();

        } catch (IllegalArgumentException | IllegalStateException e) {
            mostrarAlerta(Alert.AlertType.WARNING, "No se pudo registrar", e.getMessage());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Error inesperado",
                    "Ocurrió un error al guardar la lectura:\n\n" + e.getMessage());
        } finally {
            if (!exito) {
                Stage stage = ventana();
                if (stage != null && stage.isShowing()) {
                    btnGuardar.setDisable(false);
                }
            }
        }
    }

    private long[] validarMaquina(TextField bn, TextField color, String nombre) {
        String tBn = bn.getText() == null ? "" : bn.getText().trim();
        String tCo = color.getText() == null ? "" : color.getText().trim();

        if (tBn.isEmpty() && tCo.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Falta " + nombre,
                    "Introduce al menos el contador B/N o el de Color de " + nombre + ".");
            bn.requestFocus();
            return null;
        }

        try {
            long vBn = tBn.isEmpty() ? 0L : Long.parseLong(tBn);
            long vCo = tCo.isEmpty() ? 0L : Long.parseLong(tCo);
            return new long[] { vBn, vCo };
        } catch (NumberFormatException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Valor inválido",
                    "El número de " + nombre + " es demasiado grande.");
            return null;
        }
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private TextFormatter<String> formatterSoloDigitos() {
        return new TextFormatter<>(change -> {
            String t = change.getControlNewText();
            return t.matches("\\d*") ? change : null;
        });
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        AlertaHelper.mostrar(ventana(), tipo, titulo, mensaje);
    }
}