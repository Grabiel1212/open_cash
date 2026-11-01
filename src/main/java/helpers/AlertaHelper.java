package helpers;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class AlertaHelper {

    private AlertaHelper() {
    }

    // ============================================================
    // Tipos de alerta
    // ============================================================
    public enum AlertType {
        INFO, WARNING, ERROR, SUCCESS, CONFIRM
    }

    // ============================================================
    // API legacy — usada por RegistroInsumoController,
    // RegistroFinalController y PapelBondController
    // ============================================================
    public static void mostrar(Window owner, javafx.scene.control.Alert.AlertType tipo,
            String titulo, String mensaje) {
        mostrar(owner, titulo, mensaje, mapear(tipo));
    }

    public static void info(Window owner, String titulo, String msg) {
        mostrar(owner, titulo, msg, AlertType.INFO);
    }

    public static void warn(Window owner, String titulo, String msg) {
        mostrar(owner, titulo, msg, AlertType.WARNING);
    }

    public static void error(Window owner, String titulo, String msg) {
        mostrar(owner, titulo, msg, AlertType.ERROR);
    }

    public static Optional<ButtonType> confirmar(Window owner, String titulo, String header,
            String contenido, ButtonType... botones) {
        return dialogConfirmar(owner, titulo, header, contenido, botones);
    }

    // ============================================================
    // API nueva — usada por HomeController
    // ============================================================
    public static void mostrar(String titulo, String mensaje, AlertType tipo) {
        mostrar((Window) null, titulo, mensaje, tipo);
    }

    public static void mostrar(Window owner, String titulo, String mensaje, AlertType tipo) {
        dialogMensaje(owner, titulo, null, mensaje, tipo);
    }

    public static void info(String titulo, String mensaje) {
        mostrar(titulo, mensaje, AlertType.INFO);
    }

    public static void warn(String titulo, String mensaje) {
        mostrar(titulo, mensaje, AlertType.WARNING);
    }

    public static void error(String titulo, String mensaje) {
        mostrar(titulo, mensaje, AlertType.ERROR);
    }

    public static void success(String titulo, String mensaje) {
        mostrar(titulo, mensaje, AlertType.SUCCESS);
    }

    public static void confirmar(String titulo, String mensaje, Runnable onOk) {
        boolean ok = confirmarSimple(null, titulo, mensaje);
        if (ok && onOk != null)
            onOk.run();
    }

    public static boolean confirmarSimple(Window owner, String titulo, String mensaje) {
        ButtonType si = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Optional<ButtonType> r = dialogConfirmar(owner, titulo, null, mensaje, si, no);
        return r.isPresent() && r.get() == si;
    }

    // ============================================================
    // Implementación del diálogo
    // ============================================================
    private static void dialogMensaje(Window owner, String titulo, String header,
            String contenido, AlertType tipo) {
        AtomicReference<ButtonType> res = new AtomicReference<>(null);
        ButtonType ok = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        showDialog(owner, titulo, header, contenido, tipo, new ButtonType[] { ok }, res);
    }

    private static Optional<ButtonType> dialogConfirmar(Window owner, String titulo, String header,
            String contenido, ButtonType... botones) {
        AtomicReference<ButtonType> res = new AtomicReference<>(null);
        ButtonType[] ordenados = ordenarBotones(botones);
        showDialog(owner, titulo, header, contenido, AlertType.CONFIRM, ordenados, res);
        return Optional.ofNullable(res.get());
    }

    private static void showDialog(Window owner, String titulo, String header, String contenido,
            AlertType tipo, ButtonType[] botones,
            AtomicReference<ButtonType> resultado) {

        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null)
            stage.initOwner(owner);

        Estilo e = estilo(tipo);

        // ── Icono circular ─────────────────────────────────
        Circle circulo = new Circle(28);
        circulo.setFill(e.color.deriveColor(0, 1, 1, 0.15));
        Text iconTxt = new Text(e.icono);
        iconTxt.setFill(e.color);
        iconTxt.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        StackPane icono = new StackPane(circulo, iconTxt);
        icono.setPrefSize(56, 56);
        icono.setMinSize(56, 56);
        icono.setMaxSize(56, 56);

        // ── Título + header opcional ───────────────────────
        Label lblTitulo = new Label(titulo == null ? "" : titulo);
        lblTitulo.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        lblTitulo.setTextFill(Color.web("#0f172a"));
        lblTitulo.setWrapText(true);

        VBox textoBox = new VBox(3);
        textoBox.setAlignment(Pos.CENTER_LEFT);
        textoBox.getChildren().add(lblTitulo);

        if (header != null && !header.isBlank()) {
            Label lblHeader = new Label(header);
            lblHeader.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12.5));
            lblHeader.setTextFill(Color.web("#64748b"));
            lblHeader.setWrapText(true);
            textoBox.getChildren().add(lblHeader);
        }
        HBox.setHgrow(textoBox, Priority.ALWAYS);

        HBox topRow = new HBox(14, icono, textoBox);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // ── Contenido ──────────────────────────────────────
        Label lblContenido = new Label(contenido == null ? "" : contenido);
        lblContenido.setFont(Font.font("Segoe UI", 13.5));
        lblContenido.setTextFill(Color.web("#334155"));
        lblContenido.setWrapText(true);
        lblContenido.setTextAlignment(TextAlignment.LEFT);
        lblContenido.setMaxWidth(380);

        // ── Botones ────────────────────────────────────────
        HBox botonesRow = new HBox(10);
        botonesRow.setAlignment(Pos.CENTER_RIGHT);

        for (ButtonType bt : botones) {
            boolean primario = esPrimario(bt);
            Button btn = crearBoton(bt.getText(), primario);
            btn.setOnAction(ev -> {
                resultado.set(bt);
                stage.close();
            });
            botonesRow.getChildren().add(btn);
        }

        // ── Card ───────────────────────────────────────────
        VBox card = new VBox(16, topRow, lblContenido, botonesRow);
        card.setPadding(new Insets(24, 26, 22, 26));
        card.setStyle(
                "-fx-background-color: white;" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-radius: 16;" +
                        "-fx-border-color: #e2e8f0;" +
                        "-fx-border-width: 1;");
        card.setPrefWidth(440);
        card.setMinWidth(440);
        card.setMaxWidth(440);

        DropShadow sombra = new DropShadow();
        sombra.setColor(Color.color(0, 0, 0, 0.22));
        sombra.setRadius(28);
        sombra.setOffsetY(8);
        card.setEffect(sombra);

        StackPane root = new StackPane(card);
        root.setPadding(new Insets(28));
        root.setStyle("-fx-background-color: transparent;");

        // ── Arrastre ───────────────────────────────────────
        final double[] off = new double[2];
        card.setOnMousePressed(ev -> {
            off[0] = ev.getScreenX() - stage.getX();
            off[1] = ev.getScreenY() - stage.getY();
        });
        card.setOnMouseDragged(ev -> {
            stage.setX(ev.getScreenX() - off[0]);
            stage.setY(ev.getScreenY() - off[1]);
        });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);

        // ── Fade-in ────────────────────────────────────────
        card.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(180), card);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        // ── ESC = cerrar (equivale a cancelar) ─────────────
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE)
                stage.close();
        });

        stage.setOnShown(ev -> {
            centrar(stage, owner);
            stage.setAlwaysOnTop(true);
            stage.toFront();
            stage.requestFocus();
        });

        stage.showAndWait();
    }

    // ============================================================
    // Estilos
    // ============================================================
    private static final class Estilo {
        final Color color;
        final String icono;

        Estilo(Color color, String icono) {
            this.color = color;
            this.icono = icono;
        }
    }

    private static Estilo estilo(AlertType tipo) {
        if (tipo == null)
            tipo = AlertType.INFO;
        switch (tipo) {
            case WARNING:
                return new Estilo(Color.web("#f59e0b"), "!");
            case ERROR:
                return new Estilo(Color.web("#dc2626"), "✕");
            case SUCCESS:
                return new Estilo(Color.web("#10b981"), "✓");
            case CONFIRM:
                return new Estilo(Color.web("#8b5cf6"), "?");
            case INFO:
            default:
                return new Estilo(Color.web("#3b82f6"), "i");
        }
    }

    private static AlertType mapear(javafx.scene.control.Alert.AlertType t) {
        if (t == null)
            return AlertType.INFO;
        switch (t) {
            case ERROR:
                return AlertType.ERROR;
            case WARNING:
                return AlertType.WARNING;
            case CONFIRMATION:
                return AlertType.CONFIRM;
            case INFORMATION:
            case NONE:
            default:
                return AlertType.INFO;
        }
    }

    // ── Botones bonitos con hover ─────────────────────────
    private static Button crearBoton(String texto, boolean primario) {
        Button b = new Button(texto);
        b.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 13));
        b.setPadding(new Insets(9, 22, 9, 22));
        b.setCursor(Cursor.HAND);
        b.setPrefHeight(36);
        b.setMinHeight(36);
        estiloBoton(b, primario, false);
        b.setOnMouseEntered(e -> estiloBoton(b, primario, true));
        b.setOnMouseExited(e -> estiloBoton(b, primario, false));
        return b;
    }

    private static void estiloBoton(Button b, boolean primario, boolean hover) {
        String bg, fg;
        if (primario) {
            bg = hover ? "#2563eb" : "#3b82f6";
            fg = "white";
        } else {
            bg = hover ? "#e2e8f0" : "#f1f5f9";
            fg = hover ? "#0f172a" : "#334155";
        }
        b.setStyle(
                "-fx-background-color: " + bg + ";" +
                        "-fx-text-fill: " + fg + ";" +
                        "-fx-background-radius: 8;" +
                        "-fx-cursor: hand;");
    }

    // ── Orden de botones (estilo Windows) ─────────────────
    private static ButtonType[] ordenarBotones(ButtonType[] original) {
        if (original == null || original.length <= 1)
            return original;

        List<ButtonType> cancel = new ArrayList<>();
        List<ButtonType> primary = new ArrayList<>();

        for (ButtonType b : original) {
            ButtonBar.ButtonData d = b.getButtonData();
            if (d == ButtonBar.ButtonData.CANCEL_CLOSE || d == ButtonBar.ButtonData.NO) {
                cancel.add(b);
            } else {
                primary.add(b);
            }
        }

        ButtonType[] out = new ButtonType[original.length];
        int i = 0;
        for (ButtonType b : cancel)
            out[i++] = b;
        for (ButtonType b : primary)
            out[i++] = b;
        return out;
    }

    private static boolean esPrimario(ButtonType bt) {
        ButtonBar.ButtonData d = bt.getButtonData();
        return d == ButtonBar.ButtonData.OK_DONE
                || d == ButtonBar.ButtonData.YES
                || d == ButtonBar.ButtonData.APPLY
                || d == ButtonBar.ButtonData.FINISH;
    }

    private static void centrar(Stage stage, Window owner) {
        if (owner != null && owner.isShowing()) {
            stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
        } else {
            Rectangle2D vb = Screen.getPrimary().getVisualBounds();
            stage.setX(vb.getMinX() + (vb.getWidth() - stage.getWidth()) / 2);
            stage.setY(vb.getMinY() + (vb.getHeight() - stage.getHeight()) / 2);
        }
    }
}