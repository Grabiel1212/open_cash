package views;

import java.io.IOException;
import java.net.ServerSocket;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainApp extends Application {

    // =========================================================
    // PUERTO EXCLUSIVO PARA OPEN CASH
    // =========================================================
    private static final int PUERTO_INSTANCIA = 45821;

    private static ServerSocket serverSocket;

    // =========================================================
    // INICIAR APLICACIÓN
    // =========================================================
    @Override
    public void start(Stage stage) throws Exception {

        var url = getClass().getResource("/fx/login.fxml");

        if (url == null) {
            throw new RuntimeException(
                    "NO SE ENCONTRÓ EL FXML: /fx/login.fxml\n" +
                            "Verifica que exista en src/main/resources/fx/");
        }

        FXMLLoader fxmlLoader = new FXMLLoader(url);

        Scene scene = new Scene(fxmlLoader.load());

        stage.setTitle("Login - OpenCash");
        stage.setScene(scene);

        var iconStream = getClass().getResourceAsStream(
                "/images/logo/libro_logo.png");

        if (iconStream != null) {
            Image icon = new Image(iconStream);
            stage.getIcons().add(icon);
        }

        stage.initStyle(StageStyle.UNDECORATED);
        stage.show();
    }

    // =========================================================
    // MAIN
    // =========================================================
    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PUERTO_INSTANCIA);
        } catch (IOException e) {
            // Ya hay una instancia: enviarle una señal para que se muestre
            try (java.net.Socket s = new java.net.Socket("127.0.0.1", PUERTO_INSTANCIA)) {
                s.getOutputStream().write(1);
                s.getOutputStream().flush();
            } catch (IOException ignored) {
            }
            System.out.println("OpenCash ya está ejecutándose. Mostrando ventana...");
            return;
        }
        launch(args);
    }

    // =========================================================
    // CERRAR APLICACIÓN
    // =========================================================
    @Override
    public void stop() {

        // Liberar el puerto al cerrar OpenCash
        if (serverSocket != null && !serverSocket.isClosed()) {

            try {

                serverSocket.close();

            } catch (IOException ignored) {

                // No hacer nada
            }
        }
    }
}