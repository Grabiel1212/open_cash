package views.alerts;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Helper para mostrar alertas personalizadas con diseño moderno.
 * Ejemplo:
 * AlertHelper.mostrar("Información", "Operación realizada correctamente",
 * AlertType.INFO);
 */
public class AlertHelper {

    public enum AlertType {
        INFO, SUCCESS, WARNING, ERROR
    }

    public static void mostrar(String titulo, String mensaje, AlertType tipo) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle(null);
        alert.setHeaderText(null);

        // Color del borde según tipo
        String colorBorde = switch (tipo) {
            case SUCCESS -> "#16a34a"; // Verde
            case WARNING -> "#facc15"; // Amarillo
            case ERROR -> "#dc2626"; // Rojo
            default -> "#3b82f6"; // Azul por defecto (INFO)
        };

        // --- Estilo del panel principal ---
        alert.getDialogPane().setStyle(String.format("""
                    -fx-background-color: white;
                    -fx-border-color: %s;
                    -fx-border-width: 2;
                    -fx-border-radius: 15;
                    -fx-background-radius: 15;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 3);
                    -fx-padding: 20;
                """, colorBorde));

        // --- Título y mensaje ---
        Label lblTitulo = new Label(obtenerIcono(tipo) + " " + titulo);
        lblTitulo.setStyle(String.format("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: %s;", colorBorde));

        Label lblMensaje = new Label(mensaje);
        lblMensaje.setStyle("-fx-font-size: 14px; -fx-text-fill: #444444;");
        lblMensaje.setWrapText(true);

        VBox contenido = new VBox(10, lblTitulo, lblMensaje);
        contenido.setAlignment(Pos.CENTER_LEFT);
        alert.getDialogPane().setContent(contenido);

        // --- Botón personalizado ---
        ButtonType okButton = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().add(okButton);

        Button ok = (Button) alert.getDialogPane().lookupButton(okButton);
        ok.setStyle(String.format("""
                    -fx-background-color: %s;
                    -fx-text-fill: white;
                    -fx-font-size: 13px;
                    -fx-font-weight: bold;
                    -fx-background-radius: 8;
                    -fx-padding: 6 18;
                """, colorBorde));

        ok.setOnMouseEntered(e -> ok.setStyle(String.format("""
                    -fx-background-color: derive(%s, -15%%);
                    -fx-text-fill: white;
                    -fx-font-size: 13px;
                    -fx-font-weight: bold;
                    -fx-background-radius: 8;
                    -fx-padding: 6 18;
                """, colorBorde)));

        ok.setOnMouseExited(e -> ok.setStyle(String.format("""
                    -fx-background-color: %s;
                    -fx-text-fill: white;
                    -fx-font-size: 13px;
                    -fx-font-weight: bold;
                    -fx-background-radius: 8;
                    -fx-padding: 6 18;
                """, colorBorde)));

        // --- Quitar la barra superior ---
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.getScene().setFill(Color.TRANSPARENT);

        alert.showAndWait();
    }

    private static String obtenerIcono(AlertType tipo) {
        return switch (tipo) {
            case SUCCESS -> "✅";
            case WARNING -> "⚠️";
            case ERROR -> "❌";
            default -> "💡";
        };
    }
}