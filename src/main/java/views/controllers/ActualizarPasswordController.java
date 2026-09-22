package views.controllers;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.stage.Stage;
import javafx.util.Duration;
import services.cash.ConfigManagerDB;
import views.icons.Icons;

public class ActualizarPasswordController {

    @FXML
    private StackPane root;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private TextField txtPasswordVisible;
    @FXML
    private Button btnActualizar, btnCerrar, btnTogglePassword;
    @FXML
    private ImageView imgLogo, imgPasswordIcon;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Label lblMensaje;
    @FXML
    private Label lblTitulo;
    @FXML
    private Label lblTextoCampo;

    private boolean passwordVisible = false;
    private Timeline progressTimeline;
    private boolean esCreacion = false;
    private Stage stageActual; // <-- NUEVO

    @FXML
    public void initialize() {
        Icons.setImageIcons(imgLogo, "icons/", "update.png", 20);
        Icons.setImageIcons(imgPasswordIcon, "icons/", "pass.png", 20);

        FadeTransition ft = new FadeTransition(Duration.millis(350), root);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();

        btnCerrar.setOnAction(e -> cerrar());
        btnActualizar.setOnAction(e -> actualizar());
        btnTogglePassword.setOnAction(e -> togglePasswordVisibility());

        setupButtonAnimation();
        progressIndicator.setVisible(false);

        configurarTextoBoton();
    }

    public void setStage(Stage stage) {
        this.stageActual = stage;
    }

    public boolean isModoCreacion() {
        return esCreacion;
    }

    private void configurarTextoBoton() {
        new Thread(() -> {
            ConfigManagerDB configManager = new ConfigManagerDB();
            boolean tienePin = configManager.tienePin();
            Platform.runLater(() -> {
                if (tienePin) {
                    btnActualizar.setText("Actualizar PIN");
                    lblTitulo.setText("Actualizar PIN");
                    lblTextoCampo.setText("Ingresa tu nuevo PIN:");
                    lblMensaje.setText("Ingresa el nuevo PIN para reemplazar el actual.");
                    btnCerrar.setVisible(true);
                    btnCerrar.setManaged(true);
                } else {
                    btnActualizar.setText("Crear PIN");
                    lblTitulo.setText("Crear PIN");
                    lblTextoCampo.setText("Crea tu PIN:");
                    lblMensaje.setText("Debes crear tu PIN para poder continuar.");
                    btnCerrar.setVisible(false);
                    btnCerrar.setManaged(false);
                }
            });
        }).start();
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            txtPasswordVisible.setText(txtPassword.getText());
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
            Icons.setImageIcons(imgPasswordIcon, "icons/", "pass.png", 20);
        } else {
            txtPassword.setText(txtPasswordVisible.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
            txtPasswordVisible.setVisible(false);
            txtPasswordVisible.setManaged(false);
            Icons.setImageIcons(imgPasswordIcon, "icons/", "pass.png", 20);
        }
    }

    private void cerrar() {
        ConfigManagerDB configManager = new ConfigManagerDB();

        // Si el empleado no tiene PIN, NO permitir cerrar
        if (!configManager.tienePin()) {
            mostrarAlerta(Alert.AlertType.WARNING,
                    "PIN obligatorio",
                    "Debes crear tu PIN para poder continuar.");
            return;
        }

        // Si ya tiene PIN, cerrar normalmente
        Platform.runLater(() -> {
            if (stageActual != null) {
                stageActual.close();
            } else {
                try {
                    Stage stage = (Stage) root.getScene().getWindow();
                    if (stage != null)
                        stage.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void actualizar() {
        String nueva = passwordVisible ? txtPasswordVisible.getText() : txtPassword.getText();

        if (nueva.isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Campo vacío", "Por favor, ingresa tu nueva contraseña.");
            return;
        }
        if (nueva.length() < 2) {
            mostrarAlerta(Alert.AlertType.WARNING, "Contraseña muy corta",
                    "La contraseña debe tener al menos 2 caracteres.");
            return;
        }

        mostrarAnimacionCarga();

        new Thread(() -> {
            try {
                ConfigManagerDB configManager = new ConfigManagerDB();
                boolean actualizado = configManager.actualizarPin(nueva);
                Thread.sleep(1500);

                Platform.runLater(() -> {
                    if (actualizado) {
                        mostrarAnimacionExito();
                        PauseTransition pause = new PauseTransition(Duration.seconds(1));
                        pause.setOnFinished(e -> cerrar());
                        pause.play();
                    } else {
                        progressIndicator.setVisible(false);
                        btnActualizar.setVisible(true);
                        mostrarAlerta(Alert.AlertType.ERROR, "Error",
                                "No se pudo actualizar el PIN. Verifica que haya un cajero autenticado.");
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    btnActualizar.setVisible(true);
                    mostrarAlerta(Alert.AlertType.ERROR, "Error",
                            "Error inesperado al actualizar el PIN.");
                });
            }
        }).start();
    }

    private void mostrarAnimacionCarga() {
        btnActualizar.setVisible(false);
        progressIndicator.setVisible(true);

        Timeline rotationTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progressIndicator.rotateProperty(), 0)),
                new KeyFrame(Duration.seconds(1), new KeyValue(progressIndicator.rotateProperty(), 360)));
        rotationTimeline.setCycleCount(Timeline.INDEFINITE);
        rotationTimeline.play();
        progressTimeline = rotationTimeline;
    }

    private void mostrarAnimacionExito() {
        if (progressTimeline != null) {
            progressTimeline.stop();
        }
        progressIndicator.setVisible(false);

        StackPane successPane = new StackPane();
        successPane.setStyle("-fx-background-color: transparent;");

        Circle circle = new Circle(20);
        circle.setFill(Color.web("#2ecc71"));
        circle.setStroke(Color.web("#27ae60"));
        circle.setStrokeWidth(2);

        Path checkmark = new Path();
        checkmark.setStroke(Color.WHITE);
        checkmark.setStrokeWidth(3);
        checkmark.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        checkmark.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        double startX = -10, startY = -5;
        double midX = -3, midY = 5;
        double endX = 10, endY = -8;

        MoveTo moveTo = new MoveTo(startX, startY);
        LineTo line1 = new LineTo(midX, midY);
        LineTo line2 = new LineTo(endX, endY);

        checkmark.getElements().addAll(moveTo, line1, line2);
        checkmark.setOpacity(0);

        successPane.getChildren().addAll(circle, checkmark);

        StackPane parent = (StackPane) btnActualizar.getParent();
        parent.getChildren().remove(btnActualizar);
        parent.getChildren().add(successPane);

        ScaleTransition circleScale = new ScaleTransition(Duration.millis(300), circle);
        circleScale.setFromX(0);
        circleScale.setFromY(0);
        circleScale.setToX(1);
        circleScale.setToY(1);
        circleScale.play();

        circleScale.setOnFinished(e -> {
            FadeTransition fadeCheck = new FadeTransition(Duration.millis(400), checkmark);
            fadeCheck.setFromValue(0);
            fadeCheck.setToValue(1);
            fadeCheck.play();

            Timeline drawTimeline = new Timeline(
                    new KeyFrame(Duration.millis(400),
                            new KeyValue(line1.xProperty(), midX),
                            new KeyValue(line1.yProperty(), midY)),
                    new KeyFrame(Duration.millis(800),
                            new KeyValue(line2.xProperty(), endX),
                            new KeyValue(line2.yProperty(), endY)));
            drawTimeline.play();
        });
    }

    private void setupButtonAnimation() {
        btnActualizar.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), btnActualizar);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
        });
        btnActualizar.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), btnActualizar);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        btnActualizar.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btnActualizar);
            st.setToX(0.95);
            st.setToY(0.95);
            st.play();
        });
        btnActualizar.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btnActualizar);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}