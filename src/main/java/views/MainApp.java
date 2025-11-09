package views;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fx/login.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("Login - OpenCash");
        stage.setScene(scene);

        // 🔹 Ventana sin barra del sistema
        stage.initStyle(StageStyle.UNDECORATED);

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
