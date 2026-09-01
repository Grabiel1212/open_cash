package views.controllers.home;

import javafx.animation.FadeTransition;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public final class AnimationUtils {

    private AnimationUtils() {
    }

    public static void mostrarCarga(StackPane loadingCard) {
        loadingCard.setVisible(true);
        loadingCard.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(400), loadingCard);
        fadeIn.setToValue(1);
        fadeIn.play();

        Circle spinner = (Circle) loadingCard.lookup(".spinner-circle");
        if (spinner != null) {
            RotateTransition rt = new RotateTransition(Duration.seconds(1.5), spinner);
            rt.setByAngle(360);
            rt.setCycleCount(FadeTransition.INDEFINITE);
            rt.play();
        }
    }

    public static void ocultarCarga(StackPane loadingCard) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), loadingCard);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> loadingCard.setVisible(false));
        fadeOut.play();
    }

    public static void animarError(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(100), node);
        tt.setFromX(0);
        tt.setByX(5);
        tt.setCycleCount(3);
        tt.setAutoReverse(true);
        tt.play();
    }
}