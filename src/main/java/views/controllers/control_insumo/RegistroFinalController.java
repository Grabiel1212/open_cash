package views.controllers.control_insumo;

import helpers.AlertaHelper;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

import model.ControlImpresion;
import model.ControlImpresionInsumo;
import repository.ControlImpresionRepository;
import services.insumo.ControlInsumoService;
import services.sql.ControlmpresionService;
import views.MainApp;
import views.controllers.config.SucursalPrefs;

public class RegistroFinalController {

    /* ===== Persistencia del borrador ===== */
    private static final Preferences PREFS = Preferences.userNodeForPackage(RegistroFinalController.class);
    private static final String P = "registrofinal.";

    /* ===== Servicios ===== */
    private final ControlImpresionRepository controlRepo = new ControlmpresionService();

    /* ===== Ventana ===== */
    @FXML
    private VBox rootVentana;
    @FXML
    private HBox barraTitulo;
    @FXML
    private Button btnCerrar;

    /* ===== Selección de equipos ===== */
    @FXML
    private CheckBox chkRicoh;
    @FXML
    private CheckBox chkEpson;
    @FXML
    private VBox panelRicoh;
    @FXML
    private VBox panelEpson;
    @FXML
    private VBox contenidoRicoh;
    @FXML
    private VBox contenidoEpson;

    /* ===== Contadores ===== */
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

    /* ===== Hojas malogradas ===== */
    @FXML
    private TextField txtBondA4;
    @FXML
    private TextField txtBondA3;
    @FXML
    private TextField txtFotoA4;
    @FXML
    private TextField txtFotoA3;
    @FXML
    private TextField txtFotoA6;
    @FXML
    private TextField txtCartulinaA4;
    @FXML
    private TextField txtCartulinaA3;
    @FXML
    private TextField txtOpalinaA4;
    @FXML
    private TextField txtAdhesivoA4;
    @FXML
    private TextField txtVinilA4;
    @FXML
    private TextField txtTiendaBondA4;
    @FXML
    private TextField txtTiendaBondA3;

    /* ===== Acciones ===== */
    @FXML
    private Button btnGuardarTemporal;
    @FXML
    private Button btnGuardarFinal;
    @FXML
    private Label lblEstadoBorrador;

    /* ===== Estado interno ===== */
    private double offsetX;
    private double offsetY;
    private boolean arrastrando = false;
    private boolean hayCambiosSinGuardar = false;
    private PauseTransition toastTimer;

    // ================================================================
    public static void prepararEscenario(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
    }

    // ================================================================
    @FXML
    public void initialize() {
        aplicarFormatters();
        restaurarBorrador();
        aplicarListeners();
        aplicarEstadoRicoh(chkRicoh.isSelected());
        aplicarEstadoEpson(chkEpson.isSelected());
        actualizarTotales();
        Platform.runLater(this::configurarVentana);
    }

    private void aplicarFormatters() {
        for (TextField tf : mapaCampos().values()) {
            tf.setTextFormatter(formatterSoloDigitos());
        }
    }

    private void aplicarListeners() {
        for (TextField tf : mapaCampos().values()) {
            tf.textProperty().addListener((o, a, b) -> {
                hayCambiosSinGuardar = true;
                actualizarTotales();
            });
        }
        chkRicoh.selectedProperty().addListener((o, a, activo) -> {
            hayCambiosSinGuardar = true;
            aplicarEstadoRicoh(activo);
        });
        chkEpson.selectedProperty().addListener((o, a, activo) -> {
            hayCambiosSinGuardar = true;
            aplicarEstadoEpson(activo);
        });
    }

    // ================================================================
    private Map<String, TextField> mapaCampos() {
        Map<String, TextField> m = new LinkedHashMap<>();
        m.put("ricoh_bn", txtRicohBn);
        m.put("ricoh_color", txtRicohColor);
        m.put("epson_bn", txtEpsonBn);
        m.put("epson_color", txtEpsonColor);
        m.put("bond_a4", txtBondA4);
        m.put("bond_a3", txtBondA3);
        m.put("foto_a4", txtFotoA4);
        m.put("foto_a3", txtFotoA3);
        m.put("foto_a6", txtFotoA6);
        m.put("cart_a4", txtCartulinaA4);
        m.put("cart_a3", txtCartulinaA3);
        m.put("opal_a4", txtOpalinaA4);
        m.put("adh_a4", txtAdhesivoA4);
        m.put("vin_a4", txtVinilA4);
        m.put("tienda_a4", txtTiendaBondA4);
        m.put("tienda_a3", txtTiendaBondA3);
        return m;
    }

    // ================================================================
    private void configurarVentana() {
        Stage stage = ventana();
        if (stage == null)
            return;
        try {
            stage.initStyle(StageStyle.TRANSPARENT);
        } catch (IllegalStateException ignored) {
            /* ya visible */
        }
        if (rootVentana.getScene() != null) {
            rootVentana.getScene().setFill(Color.TRANSPARENT);
        }
        stage.setAlwaysOnTop(true);
        stage.setResizable(false);

        // 👇 Alt+F4 / cierre del SO → guardar borrador silenciosamente
        stage.setOnCloseRequest(ev -> {
            if (hayCambiosSinGuardar) {
                guardarBorradorSilencioso();
            }
        });
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
    // ESTADO DE LOS PANELES
    // ================================================================
    private void aplicarEstadoRicoh(boolean activo) {
        if (contenidoRicoh != null)
            contenidoRicoh.setDisable(!activo);
        if (panelRicoh != null) {
            panelRicoh.pseudoClassStateChanged(
                    javafx.css.PseudoClass.getPseudoClass("off"), !activo);
        }
        actualizarTotales();
    }

    private void aplicarEstadoEpson(boolean activo) {
        if (contenidoEpson != null)
            contenidoEpson.setDisable(!activo);
        if (panelEpson != null) {
            panelEpson.pseudoClassStateChanged(
                    javafx.css.PseudoClass.getPseudoClass("off"), !activo);
        }
        actualizarTotales();
    }

    // ================================================================
    // TOTALES
    // ================================================================
    private void actualizarTotales() {
        if (chkRicoh != null && chkRicoh.isSelected()) {
            long total = parseSeguro(txtRicohBn) + parseSeguro(txtRicohColor);
            lblRicohTotal.setText("Total: " + String.format("%,d", total));
        } else if (lblRicohTotal != null) {
            lblRicohTotal.setText("Total: —");
        }

        if (chkEpson != null && chkEpson.isSelected()) {
            long total = parseSeguro(txtEpsonBn) + parseSeguro(txtEpsonColor);
            lblEpsonTotal.setText("Total: " + String.format("%,d", total));
        } else if (lblEpsonTotal != null) {
            lblEpsonTotal.setText("Total: —");
        }
    }

    private long parseSeguro(TextField tf) {
        if (tf == null)
            return 0L;
        String t = tf.getText();
        if (t == null || t.trim().isEmpty())
            return 0L;
        try {
            return Long.parseLong(t.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ================================================================
    // GUARDAR TEMPORAL (borrador — botón de abajo)
    // ================================================================
    @FXML
    private void guardarTemporal() {
        if (!hayAlMenosUnEquipo()) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "Sin equipos seleccionados",
                    "Marca al menos un equipo (Ricoh o Epson) antes de guardar.");
            return;
        }

        try {
            for (Map.Entry<String, TextField> e : mapaCampos().entrySet()) {
                String v = e.getValue().getText();
                PREFS.put(P + e.getKey(), v == null ? "" : v);
            }
            PREFS.putBoolean(P + "ricoh_selected", chkRicoh.isSelected());
            PREFS.putBoolean(P + "epson_selected", chkEpson.isSelected());
            PREFS.putLong(P + "timestamp", System.currentTimeMillis());

            hayCambiosSinGuardar = false;
            mostrarToast("✓ Borrador guardado · " + horaActual(), 3);
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo guardar",
                    "Ocurrió un error al guardar el borrador:\n" + e.getMessage());
        }
    }

    /**
     * Guarda el borrador en Preferences SIN validar, SIN toast y SIN diálogo.
     * Se usa desde el botón X y desde Alt+F4 para no perder datos al cerrar.
     */
    private void guardarBorradorSilencioso() {
        try {
            for (Map.Entry<String, TextField> e : mapaCampos().entrySet()) {
                String v = e.getValue().getText();
                PREFS.put(P + e.getKey(), v == null ? "" : v);
            }
            PREFS.putBoolean(P + "ricoh_selected", chkRicoh.isSelected());
            PREFS.putBoolean(P + "epson_selected", chkEpson.isSelected());
            PREFS.putLong(P + "timestamp", System.currentTimeMillis());
            hayCambiosSinGuardar = false;
        } catch (Exception ignored) {
            // si falla el borrador, igual dejamos cerrar
        }
    }

    // ================================================================
    // GUARDAR FINAL (cierra turno en BD)
    // ================================================================
    @FXML
    private void guardarFinal() {
        if (!hayAlMenosUnEquipo()) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "Sin equipos seleccionados",
                    "Marca al menos un equipo (Ricoh o Epson) antes de guardar.");
            return;
        }

        ButtonType btSi = new ButtonType("Sí, cerrar turno", ButtonBar.ButtonData.OK_DONE);
        ButtonType btNo = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        Optional<ButtonType> r = AlertaHelper.confirmar(
                ventana(),
                "Cerrar turno",
                "¿Cerrar el turno y guardar el registro final?",
                "Esta acción no se puede deshacer.",
                btSi, btNo);

        if (r.isEmpty() || r.get() != btSi)
            return;

        // 🔒 Evitar doble ejecución (doble clic / ENTER repetido)
        btnGuardarFinal.setDisable(true);
        boolean exito = false;

        try {
            String resumen = persistirFinal();
            limpiarBorrador();
            hayCambiosSinGuardar = false;
            exito = true;
            mostrarAlerta(Alert.AlertType.INFORMATION, "Registro final guardado", resumen);
            cerrarVentana();
        } catch (IllegalArgumentException | IllegalStateException e) {
            mostrarAlerta(Alert.AlertType.WARNING, "No se pudo guardar", e.getMessage());
        } catch (Exception e) {
            mostrarAlerta(Alert.AlertType.ERROR, "No se pudo guardar",
                    "Ocurrió un error al guardar el registro final:\n\n" + e.getMessage());
        } finally {
            if (!exito) {
                Stage stage = ventana();
                if (stage != null && stage.isShowing()) {
                    btnGuardarFinal.setDisable(false);
                }
            }
        }
    }

    // ================================================================
    // PERSISTENCIA REAL — cierra el turno en BD
    // ================================================================
    private String persistirFinal() {

        // 1. Sucursal actual
        Long idSucursal = SucursalPrefs.getId();
        if (idSucursal == null) {
            throw new IllegalStateException(
                    "No hay sucursal seleccionada. Vuelve a iniciar sesión.");
        }

        // 2. Buscar el control ABIERTO
        ControlImpresion abierto = controlRepo.ObtenerControlAbiertoPorSucursal(idSucursal);
        if (abierto == null) {
            throw new IllegalStateException(
                    "No hay un control ABIERTO para esta sucursal.\n"
                            + "Primero debes registrar el conteo inicial.");
        }

        Long idControl = abierto.getIdControl();

        // 3. Calcular contadores finales
        long ricohTotal = parseSeguro(txtRicohBn) + parseSeguro(txtRicohColor);
        long epsonTotal = parseSeguro(txtEpsonBn);

        long finalRicoh = chkRicoh.isSelected()
                ? ricohTotal
                : abierto.getInicialRicoh();

        long finalEpson = chkEpson.isSelected()
                ? epsonTotal
                : abierto.getInicialEpson();

        // 4. Validar que no retrocedan
        if (finalRicoh < abierto.getInicialRicoh()) {
            throw new IllegalArgumentException(
                    "El contador final de Ricoh (" + finalRicoh
                            + ") es menor al inicial ("
                            + abierto.getInicialRicoh() + ").");
        }
        if (finalEpson < abierto.getInicialEpson()) {
            throw new IllegalArgumentException(
                    "El contador final de Epson (" + finalEpson
                            + ") es menor al inicial ("
                            + abierto.getInicialEpson() + ").");
        }

        // 5. Construir insumos (solo cantidad > 0)
        List<ControlImpresionInsumo> insumos = construirInsumos();

        // 6. Persistir cierre
        Boolean ok = controlRepo.CrearConteoFinal(
                idControl, finalRicoh, finalEpson, insumos);

        if (!Boolean.TRUE.equals(ok)) {
            throw new IllegalStateException(
                    "La base de datos rechazó el cierre.\n"
                            + "Verifica los contadores e intenta de nuevo.");
        }

        // 7. Limpiar estado + reprogramar alarma
        ControlInsumoService.getInstance().limpiar();
        MainApp.cancelarReaperturaRegistro();
        MainApp.programarAperturaAutomatica();

        // 8. Resumen
        return construirResumen(abierto, finalRicoh, finalEpson, insumos);
    }

    // ================================================================
    // INSUMOS — mapeo directo al esquema de la BD
    // ================================================================
    private List<ControlImpresionInsumo> construirInsumos() {
        List<ControlImpresionInsumo> lista = new ArrayList<>();

        agregarInsumo(lista, "BOND", "A4", txtBondA4);
        agregarInsumo(lista, "BOND", "A3", txtBondA3);
        agregarInsumo(lista, "FOTOGRAFICO", "A4", txtFotoA4);
        agregarInsumo(lista, "FOTOGRAFICO", "A3", txtFotoA3);
        agregarInsumo(lista, "FOTOGRAFICO", "A6", txtFotoA6);
        agregarInsumo(lista, "CARTULINA", "A4", txtCartulinaA4);
        agregarInsumo(lista, "CARTULINA", "A3", txtCartulinaA3);
        agregarInsumo(lista, "OPALINA", "A4", txtOpalinaA4);
        agregarInsumo(lista, "ADHESIVO_MATE", "A4", txtAdhesivoA4);
        agregarInsumo(lista, "VINIL", "A4", txtVinilA4);
        agregarInsumo(lista, "BOND_TIENDA", "A4", txtTiendaBondA4);
        agregarInsumo(lista, "BOND_TIENDA", "A3", txtTiendaBondA3);

        return lista;
    }

    private void agregarInsumo(List<ControlImpresionInsumo> lista,
            String tipo, String tamano, TextField tf) {
        long cantidad = parseSeguro(tf);
        if (cantidad <= 0)
            return;

        ControlImpresionInsumo insumo = new ControlImpresionInsumo();
        insumo.setTipoInsumo(tipo);
        insumo.setTamano(tamano);
        insumo.setCantidad(cantidad);
        lista.add(insumo);
    }

    // ================================================================
    // RESUMEN (para el alert final)
    // ================================================================
    private String construirResumen(ControlImpresion abierto,
            long finalRicoh,
            long finalEpson,
            List<ControlImpresionInsumo> insumos) {

        StringBuilder sb = new StringBuilder();
        sb.append("Control #").append(abierto.getIdControl()).append(" cerrado\n\n");

        sb.append("RICOH\n")
                .append("  Inicial: ").append(abierto.getInicialRicoh()).append("\n")
                .append("  Final:   ").append(finalRicoh).append("\n")
                .append("  Producido: ").append(finalRicoh - abierto.getInicialRicoh())
                .append("\n\n");

        sb.append("EPSON\n")
                .append("  Inicial: ").append(abierto.getInicialEpson()).append("\n")
                .append("  Final:   ").append(finalEpson).append("\n")
                .append("  Producido: ").append(finalEpson - abierto.getInicialEpson())
                .append("\n\n");

        if (insumos == null || insumos.isEmpty()) {
            sb.append("Hojas malogradas: sin registros");
        } else {
            sb.append("Hojas malogradas:\n");
            for (ControlImpresionInsumo i : insumos) {
                sb.append("  ").append(i.getTipoInsumo())
                        .append(" ").append(i.getTamano())
                        .append(": ").append(i.getCantidad()).append("\n");
            }
        }

        return sb.toString();
    }

    // ================================================================
    // BORRADOR (preferences)
    // ================================================================
    private void restaurarBorrador() {
        try {
            long ts = PREFS.getLong(P + "timestamp", 0);
            if (ts <= 0)
                return;

            for (Map.Entry<String, TextField> e : mapaCampos().entrySet()) {
                String v = PREFS.get(P + e.getKey(), "");
                if (v != null && !v.isEmpty())
                    e.getValue().setText(v);
            }
            chkRicoh.setSelected(PREFS.getBoolean(P + "ricoh_selected", true));
            chkEpson.setSelected(PREFS.getBoolean(P + "epson_selected", true));

            Platform.runLater(() -> mostrarToast("↻ Borrador restaurado · " + formatoFecha(ts), 5));
        } catch (Exception ignored) {
            /* primer arranque, sin borrador */
        }
    }

    private void limpiarBorrador() {
        try {
            for (String key : PREFS.keys()) {
                if (key.startsWith(P))
                    PREFS.remove(key);
            }
        } catch (Exception ignored) {
        }
    }

    // ================================================================
    // CIERRE — el botón X guarda borrador silencioso y cierra
    // ================================================================
    @FXML
    private void solicitarCierre() {
        guardarBorradorSilencioso();
        cerrarVentana();
    }

    private void cerrarVentana() {
        Stage stage = ventana();
        if (stage != null)
            stage.close();
    }

    // ================================================================
    // HELPERS
    // ================================================================
    private boolean hayAlMenosUnEquipo() {
        return (chkRicoh != null && chkRicoh.isSelected())
                || (chkEpson != null && chkEpson.isSelected());
    }

    private void mostrarToast(String mensaje, int segundos) {
        if (lblEstadoBorrador == null)
            return;
        if (toastTimer != null)
            toastTimer.stop();

        lblEstadoBorrador.setText(mensaje);
        lblEstadoBorrador.setVisible(true);
        lblEstadoBorrador.setManaged(true);

        toastTimer = new PauseTransition(Duration.seconds(segundos));
        toastTimer.setOnFinished(ev -> {
            if (lblEstadoBorrador == null)
                return;
            lblEstadoBorrador.setVisible(false);
            lblEstadoBorrador.setManaged(false);
        });
        toastTimer.play();
    }

    private String horaActual() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String formatoFecha(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }

    private TextFormatter<String> formatterSoloDigitos() {
        return new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null);
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        AlertaHelper.mostrar(ventana(), tipo, titulo, mensaje);
    }
}