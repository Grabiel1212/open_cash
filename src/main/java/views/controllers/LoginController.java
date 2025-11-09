package views.controllers;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.media.AudioClip;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.Usuario;
import services.UsuarioService;
import views.alerts.AlertHelper;
import views.icons.Icons;

public class LoginController {

    @FXML
    private ImageView imgLogo;

    @FXML
    private ImageView iconUser;

    @FXML
    private ImageView btnCerrar;

    @FXML
    private ImageView btnMinimizar;

    @FXML
    private StackPane btnCerrarBox;

    @FXML
    private StackPane btnMinimizarBox;

    @FXML
    private ImageView iconVerPassword;

    @FXML
    private TextField txtUsuario;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private TextField txtPasswordVisible;

    @FXML
    private Button btnLogin;

    private boolean mostrandoPassword = false;

    private final UsuarioService usuarioService = new UsuarioService();

    @FXML
    public void initialize() {
        // Cargar íconos
        Icons.setImageIcons(imgLogo, "logos.png", 250);
        Icons.setImageIcons(iconUser, "user.png", 80);
        Icons.setImageIcons(btnCerrar, "cancel.png", 50);
        Icons.setImageIcons(btnMinimizar, "mini.png", 50);
        Icons.setImageIcons(iconVerPassword, "pass.png", 25);

        txtPasswordVisible.setManaged(false);
        txtPasswordVisible.setVisible(false);

        // Eventos de botones
        btnCerrarBox.setOnMouseClicked(e -> ((Stage) btnCerrar.getScene().getWindow()).close());
        btnMinimizarBox.setOnMouseClicked(e -> ((Stage) btnMinimizar.getScene().getWindow()).setIconified(true));

        iconVerPassword.setOnMouseClicked(e -> togglePasswordVisibility());
    }

    private void togglePasswordVisibility() {
        if (mostrandoPassword) {
            txtPassword.setText(txtPasswordVisible.getText());
            txtPasswordVisible.setManaged(false);
            txtPasswordVisible.setVisible(false);
            txtPassword.setManaged(true);
            txtPassword.setVisible(true);
            mostrandoPassword = false;
        } else {
            txtPasswordVisible.setText(txtPassword.getText());
            txtPassword.setManaged(false);
            txtPassword.setVisible(false);
            txtPasswordVisible.setManaged(true);
            txtPasswordVisible.setVisible(true);
            mostrandoPassword = true;
        }
    }

    @FXML
    private void iniciarSesion() {
        String usuario = txtUsuario.getText().trim();
        String password = mostrandoPassword ? txtPasswordVisible.getText() : txtPassword.getText().trim();

        if (usuario.isEmpty() || password.isEmpty()) {
            AlertHelper.mostrar("Campos vacíos", "Por favor ingrese su usuario y contraseña.",
                    AlertHelper.AlertType.WARNING);
            return;
        }

        // 🔹 Crear objeto usuario con datos del formulario
        Usuario user = new Usuario(usuario, password);

        try {
            // 🔹 Validar usuario desde base de datos
            Usuario encontrado = usuarioService.validarUsuario(user);

            if (encontrado != null) {
                reproducirAudioBienvenida();
                cargarHome();
            } else {
                AlertHelper.mostrar("Error", "Usuario o contraseña incorrectos.", AlertHelper.AlertType.ERROR);
            }
        } catch (Exception e) {
            AlertHelper.mostrar("Error de conexión", "No se pudo conectar con la base de datos.",
                    AlertHelper.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void reproducirAudioBienvenida() {
        try {
            String path = getClass().getResource("/audio/bienvenido.wav").toString();
            AudioClip audio = new AudioClip(path);
            audio.play();
        } catch (Exception e) {
            System.out.println("⚠️ No se pudo reproducir el audio: " + e.getMessage());
        }
    }

    private void cargarHome() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fx/home.fxml"));
            Parent root = loader.load();

            HomeController homeController = loader.getController();

            Stage homeStage = new Stage();
            homeStage.setScene(new Scene(root));
            homeStage.initStyle(StageStyle.UNDECORATED);

            // 🔥 Asegurar que el stage esté completamente inicializado
            homeStage.setOnShown(e -> {
                homeController.setPrimaryStage(homeStage);
            });

            homeStage.show();

            // Cerrar login
            ((Stage) btnLogin.getScene().getWindow()).close();

        } catch (IOException e) {
            AlertHelper.mostrar("Error", "No se pudo cargar la ventana principal.", AlertHelper.AlertType.ERROR);
        }
    }
}