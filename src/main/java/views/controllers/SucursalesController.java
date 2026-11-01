package views.controllers;

import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import model.Sucursal;
import services.sql.SucursalService;
import views.controllers.config.SucursalPrefs;

public class SucursalesController {

    @FXML
    private FlowPane flowSucursales;
    @FXML
    private StackPane loadingOverlay;
    @FXML
    private StackPane emptyOverlay;
    @FXML
    private Label lblSucursalSeleccionada;
    @FXML
    private Button btnConfirmar;
    @FXML
    private Button btnRecargar;

    private final SucursalService sucursalService = new SucursalService();

    private Sucursal sucursalSeleccionada;
    private Consumer<Sucursal> onSucursalSeleccionada; // callback opcional al Home

    @FXML
    public void initialize() {
        cargarSucursales();
    }

    /** Callback opcional: se ejecuta al presionar CONFIRMAR. */
    public void setOnSucursalSeleccionada(Consumer<Sucursal> callback) {
        this.onSucursalSeleccionada = callback;
    }

    public Sucursal getSucursalSeleccionada() {
        return sucursalSeleccionada;
    }

    // ══════════════════════════════════════════════════════
    // CARGA DE DATOS
    // ══════════════════════════════════════════════════════
    @FXML
    private void recargarSucursales() {
        sucursalSeleccionada = null;
        actualizarFooter();
        cargarSucursales();
    }

    private void cargarSucursales() {
        mostrarCargando(true);
        flowSucursales.getChildren().clear();

        new Thread(() -> {
            List<Sucursal> lista = sucursalService.ListSucursales();
            Platform.runLater(() -> {
                renderizarCards(lista);
                mostrarCargando(false);
            });
        }, "sucursales-loader").start();
    }

    private void renderizarCards(List<Sucursal> lista) {
        flowSucursales.getChildren().clear();

        if (lista == null || lista.isEmpty()) {
            emptyOverlay.setVisible(true);
            emptyOverlay.setManaged(true);
            return;
        }
        emptyOverlay.setVisible(false);
        emptyOverlay.setManaged(false);

        // 🔽 RESTAURAR: si hay una guardada y no hay selección en memoria, la
        // recuperamos
        if (sucursalSeleccionada == null) {
            Long savedId = SucursalPrefs.getId();
            if (savedId != null) {
                for (Sucursal s : lista) {
                    if (savedId.equals(s.getIdSucursal())) {
                        sucursalSeleccionada = s;
                        System.out.println("♻️ Sucursal restaurada: " + s.getNombre());
                        break;
                    }
                }
            }
        }

        for (Sucursal s : lista) {
            flowSucursales.getChildren().add(crearCard(s));
        }

        // 🔽 Refrescar footer (por si la card se pintó marcada como seleccionada)
        actualizarFooter();
    }

    // ══════════════════════════════════════════════════════
    // CREACIÓN DE CADA CARD
    // ══════════════════════════════════════════════════════
    private VBox crearCard(Sucursal sucursal) {

        // Icono circular
        Circle circle = new Circle(28);
        circle.setFill(Color.web("#eff6ff"));
        circle.setStroke(Color.web("#bfdbfe"));
        circle.setStrokeWidth(1.4);

        Text icon = new Text("🏬");
        icon.setStyle("-fx-font-size: 22px;");

        StackPane iconWrap = new StackPane(circle, icon);
        iconWrap.setPrefSize(64, 64);
        iconWrap.setMaxSize(64, 64);

        // Nombre
        Label nombre = new Label(sucursal.getNombre());
        nombre.setWrapText(true);
        nombre.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; " +
                "-fx-text-fill: #1e293b; " +
                "-fx-font-family: 'Inter','Segoe UI', sans-serif;");

        // ID
        Label idLbl = new Label("ID: " + sucursal.getIdSucursal());
        idLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; " +
                "-fx-font-family: 'Inter','Segoe UI', sans-serif;");

        // Estado
        Circle dot = new Circle(4, Color.web("#22c55e"));
        Label estadoLbl = new Label("Activa");
        estadoLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #22c55e; " +
                "-fx-font-weight: 700; " +
                "-fx-font-family: 'Inter','Segoe UI', sans-serif;");
        HBox estadoBox = new HBox(6, dot, estadoLbl);
        estadoBox.setAlignment(Pos.CENTER_LEFT);

        // Check indicador de selección (oculto por defecto)
        StackPane checkWrap = new StackPane();
        Circle checkCircle = new Circle(12, Color.web("#2563eb"));
        Text checkMark = new Text("✓");
        checkMark.setStyle("-fx-font-size: 12px; -fx-fill: white; -fx-font-weight: 900;");
        checkWrap.getChildren().addAll(checkCircle, checkMark);
        checkWrap.setVisible(false);
        checkWrap.setMaxSize(24, 24);
        checkWrap.setPrefSize(24, 24);

        // Header de la card: icono + check
        HBox top = new HBox(iconWrap);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(top, javafx.scene.layout.Priority.ALWAYS);

        HBox topRow = new HBox(top, checkWrap);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setSpacing(10);

        // Contenido
        VBox content = new VBox(6, nombre, idLbl, estadoBox);
        content.setAlignment(Pos.TOP_LEFT);

        // Card completa
        VBox card = new VBox(14, topRow, content);
        card.getStyleClass().add("sucursal-card");
        card.setPrefWidth(230);
        card.setPrefHeight(190);
        card.setCursor(Cursor.HAND);

        // Selección visual
        card.setOnMouseClicked(e -> seleccionarCard(card, checkWrap, sucursal));

        // Si ya había una seleccionada, mantenerla
        if (sucursalSeleccionada != null
                && sucursalSeleccionada.getIdSucursal().equals(sucursal.getIdSucursal())) {
            card.getStyleClass().add("sucursal-card-selected");
            checkWrap.setVisible(true);
        }

        return card;
    }

    // ══════════════════════════════════════════════════════
    // SELECCIÓN
    // ══════════════════════════════════════════════════════
    private void seleccionarCard(VBox card, StackPane check, Sucursal sucursal) {
        // Quitar selección de todas las cards
        for (var node : flowSucursales.getChildren()) {
            if (node instanceof VBox v) {
                v.getStyleClass().remove("sucursal-card-selected");
                // ocultar todos los checks
                if (v.getChildren().size() > 0 && v.getChildren().get(0) instanceof HBox topRow) {
                    for (var child : topRow.getChildren()) {
                        if (child instanceof StackPane sp && sp.getChildren().size() == 2
                                && sp.getChildren().get(1) instanceof Text t
                                && "✓".equals(t.getText())) {
                            sp.setVisible(false);
                        }
                    }
                }
            }
        }

        card.getStyleClass().add("sucursal-card-selected");
        check.setVisible(true);
        sucursalSeleccionada = sucursal;

        actualizarFooter();
    }

    private void actualizarFooter() {
        if (sucursalSeleccionada == null) {
            lblSucursalSeleccionada.setText("— Ninguna —");
            lblSucursalSeleccionada.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; " +
                    "-fx-text-fill: #94a3b8; -fx-font-family: 'Inter','Segoe UI', sans-serif;");
            btnConfirmar.setDisable(true);
        } else {
            lblSucursalSeleccionada.setText(sucursalSeleccionada.getNombre()
                    + "   (ID: " + sucursalSeleccionada.getIdSucursal() + ")");
            lblSucursalSeleccionada.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; " +
                    "-fx-text-fill: #1e293b; -fx-font-family: 'Inter','Segoe UI', sans-serif;");
            btnConfirmar.setDisable(false);
        }
    }

    // ══════════════════════════════════════════════════════
    // CONFIRMAR
    // ══════════════════════════════════════════════════════
    @FXML
    private void confirmarSeleccion() {
        if (sucursalSeleccionada == null)
            return;

        // 🔽 PERSISTIR
        SucursalPrefs.guardar(
                sucursalSeleccionada.getIdSucursal(),
                sucursalSeleccionada.getNombre());

        System.out.println("💾 Sucursal guardada: " + sucursalSeleccionada.getNombre());

        if (onSucursalSeleccionada != null) {
            onSucursalSeleccionada.accept(sucursalSeleccionada);
        }
    }

    // ══════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════
    private void mostrarCargando(boolean cargando) {
        loadingOverlay.setVisible(cargando);
        btnRecargar.setDisable(cargando);
    }
}