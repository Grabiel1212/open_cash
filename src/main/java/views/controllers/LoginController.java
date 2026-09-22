
package views.controllers;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.Empleado;
import services.sql.EmpleadoService;
import services.text_vos.TextPeechService;
import views.alerts.AlertHelper;
import views.icons.Icons;

public class LoginController {

        // =====================================================
        // IMÁGENES
        // =====================================================

        @FXML
        private ImageView iconUser;

        @FXML
        private ImageView btnCerrar;

        @FXML
        private ImageView btnMinimizar;

        // =====================================================
        // BOTONES DE VENTANA
        // =====================================================

        @FXML
        private StackPane btnCerrarBox;

        @FXML
        private StackPane btnMinimizarBox;

        @FXML
        private ImageView imgFondo;

        // =====================================================
        // DNI
        // =====================================================

        @FXML
        private TextField txtDni;

        // =====================================================
        // BOTÓN LOGIN
        // =====================================================

        @FXML
        private Button btnLogin;

        // =====================================================
        // SERVICIO EMPLEADO
        // =====================================================

        private final EmpleadoService empleadoService = new EmpleadoService();

        // =====================================================
        // EMPLEADO ACTUAL
        // =====================================================

        private Empleado empleadoActual;

        // =====================================================
        // INITIALIZE
        // =====================================================

        @FXML
        public void initialize() {

                // =========================================
                // CARGAR FONDO LOGIN
                // =========================================

                Image fondo = new Image(
                                getClass().getResourceAsStream(
                                                "/images/icons/fondo_login.jpg"));

                imgFondo.setImage(fondo);

                // -------------------------------------------------
                // CARGAR ICONO USUARIO
                // -------------------------------------------------

                Icons.setImageIcons(
                                iconUser, "icons/",
                                "user_libro.jpg",
                                80);

                // -------------------------------------------------
                // CARGAR BOTÓN CERRAR
                // -------------------------------------------------

                Icons.setImageIcons(
                                btnCerrar, "icons/",
                                "cancel.png",
                                50);

                // -------------------------------------------------
                // CARGAR BOTÓN MINIMIZAR
                // -------------------------------------------------

                Icons.setImageIcons(
                                btnMinimizar,
                                "icons/",
                                "minimizar.png",
                                50);

                // =================================================
                // BOTÓN CERRAR
                // =================================================

                btnCerrarBox.setOnMouseClicked(e -> {

                        Stage stage = (Stage) btnCerrar
                                        .getScene()
                                        .getWindow();

                        stage.close();
                });

                // =================================================
                // BOTÓN MINIMIZAR
                // =================================================

                btnMinimizarBox.setOnMouseClicked(e -> {

                        Stage stage = (Stage) btnMinimizar
                                        .getScene()
                                        .getWindow();

                        stage.setIconified(true);
                });

                // =================================================
                // SOLO NÚMEROS EN DNI
                // =================================================

                txtDni.textProperty().addListener(
                                (observable, oldValue, newValue) -> {

                                        // Eliminar cualquier carácter que no sea número
                                        if (!newValue.matches("\\d*")) {

                                                txtDni.setText(
                                                                newValue.replaceAll("[^\\d]", ""));
                                        }

                                        // Máximo 8 dígitos
                                        if (txtDni.getText().length() > 8) {

                                                txtDni.setText(
                                                                txtDni.getText()
                                                                                .substring(0, 8));
                                        }
                                });
        }

        // =====================================================
        // INICIAR SESIÓN
        // =====================================================

        @FXML
        private void iniciarSesion() {

                // -------------------------------------------------
                // OBTENER DNI
                // -------------------------------------------------

                String dni = txtDni.getText().trim();

                // =================================================
                // VALIDAR DNI VACÍO
                // =================================================

                if (dni.isEmpty()) {

                        AlertHelper.mostrar(
                                        "DNI requerido",
                                        "Por favor, ingrese su número de DNI.",
                                        AlertHelper.AlertType.WARNING);

                        txtDni.requestFocus();

                        return;
                }

                // =================================================
                // VALIDAR 8 DÍGITOS
                // =================================================

                if (dni.length() != 8) {

                        AlertHelper.mostrar(
                                        "DNI incorrecto",
                                        "El DNI debe tener exactamente 8 dígitos.",
                                        AlertHelper.AlertType.WARNING);

                        txtDni.requestFocus();

                        return;
                }

                try {

                        // =================================================
                        // BUSCAR EMPLEADO EN POSTGRESQL
                        // =================================================

                        Empleado empleado = empleadoService.loginEmpleado(dni);

                        // =================================================
                        // EMPLEADO NO ENCONTRADO
                        // =================================================

                        if (empleado == null) {

                                AlertHelper.mostrar(
                                                "Acceso denegado",
                                                "El DNI no está registrado o el empleado está inactivo.",
                                                AlertHelper.AlertType.ERROR);

                                txtDni.clear();

                                txtDni.requestFocus();

                                return;
                        }

                        // =================================================
                        // GUARDAR EMPLEADO ACTUAL
                        // =================================================

                        empleadoActual = empleado;

                        System.out.println(
                                        "========================================");

                        System.out.println(
                                        "LOGIN CORRECTO");

                        System.out.println(
                                        "ID: " + empleado.getIdEmpleado());

                        System.out.println(
                                        "Nombre: " + empleado.getNombre());

                        System.out.println(
                                        "Apellido: " + empleado.getApellido());

                        System.out.println(
                                        "DNI: " + empleado.getDni());

                        System.out.println(
                                        "========================================");

                        // =================================================
                        // ABRIR HOME
                        // =================================================

                        cargarHome();

                        // =================================================
                        // REPRODUCIR BIENVENIDA CON PIPER
                        // =================================================

                        reproducirAudioBienvenida(empleado);

                } catch (Exception e) {

                        e.printStackTrace();

                        AlertHelper.mostrar(
                                        "Error de conexión",
                                        "No se pudo conectar con la base de datos.",
                                        AlertHelper.AlertType.ERROR);
                }
        }

        // =====================================================
        // AUDIO DE BIENVENIDA
        // =====================================================

        private void reproducirAudioBienvenida(Empleado empleado) {

                try {

                        // =================================================
                        // OBTENER DATOS DEL EMPLEADO
                        // =================================================

                        Long idEmpleado = empleado.getIdEmpleado();

                        String nombre = empleado.getNombre();

                        // =================================================
                        // CONSTRUIR MENSAJE
                        // =================================================

                        String texto = "Bienvenido, "
                                        + nombre
                                        ;

                        // =================================================
                        // EJECUTAR AUDIO EN SEGUNDO PLANO
                        // =================================================

                        Thread hiloAudio = new Thread(() -> {

                                try {

                                        // =============================================
                                        // INTENTAR REPRODUCIR AUDIO EXISTENTE
                                        // =============================================

                                        boolean audioExiste = TextPeechService.reproducirAudioEmpleado(
                                                        idEmpleado);

                                        // =============================================
                                        // SI NO EXISTE, GENERAR AUDIO
                                        // =============================================

                                        if (!audioExiste) {

                                                System.out.println(
                                                                "🎤 Generando audio por primera vez para empleado ID: "
                                                                                + idEmpleado);

                                                TextPeechService.generarAudioEmpleado(
                                                                idEmpleado,
                                                                texto);

                                                // =========================================
                                                // REPRODUCIR AUDIO RECIÉN GENERADO
                                                // =========================================

                                                TextPeechService.reproducirAudioEmpleado(
                                                                idEmpleado);
                                        }

                                } catch (Exception e) {

                                        System.out.println(
                                                        "❌ Error en hilo de audio:");

                                        e.printStackTrace();
                                }

                        });

                        // Evita que el hilo impida cerrar la aplicación
                        hiloAudio.setDaemon(true);

                        // Nombre del hilo
                        hiloAudio.setName(
                                        "Audio-Bienvenida-Empleado-" + idEmpleado);

                        // Iniciar hilo
                        hiloAudio.start();

                } catch (Exception e) {

                        System.out.println(
                                        "❌ Error al reproducir bienvenida:");

                        e.printStackTrace();
                }
        }

        // =====================================================
        // CARGAR HOME
        // =====================================================

        private void cargarHome() {
                try {
                        // =================================================
                        // CARGAR FXML
                        // =================================================
                        FXMLLoader loader = new FXMLLoader(
                                        getClass().getResource("/fx/home.fxml"));

                        Parent root = loader.load();

                        // =================================================
                        // OBTENER CONTROLLER DEL HOME
                        // =================================================
                        HomeController homeController = loader.getController();

                        // =================================================
                        // PASAR EMPLEADO ACTUAL AL HOME
                        // =================================================
                        homeController.setEmpleadoActual(empleadoActual);

                        // =================================================
                        // CREAR STAGE
                        // =================================================
                        Stage homeStage = new Stage();

                        // =================================================
                        // CREAR SCENE
                        // =================================================
                        Scene scene = new Scene(root);
                        homeStage.setScene(scene);

                        // =================================================
                        // VENTANA SIN DECORACIÓN
                        // =================================================
                        homeStage.initStyle(StageStyle.UNDECORATED);

                        // =================================================
                        // PASAR STAGE AL HOME
                        // IMPORTANTE PARA MINIMIZAR/CERRAR/RESTAURAR
                        // =================================================
                        homeController.setPrimaryStage(homeStage);

                        // =================================================
                        // MOSTRAR HOME
                        // =================================================
                        homeStage.show();

                        // =================================================
                        // CERRAR LOGIN
                        // =================================================
                        Stage loginStage = (Stage) btnLogin
                                        .getScene()
                                        .getWindow();

                        loginStage.close();

                } catch (IOException e) {
                        e.printStackTrace();

                        AlertHelper.mostrar(
                                        "Error",
                                        "No se pudo cargar la ventana principal.",
                                        AlertHelper.AlertType.ERROR);
                }
        }
}
