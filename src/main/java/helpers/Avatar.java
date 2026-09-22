package helpers;

import java.io.InputStream;
import java.util.List;
import java.util.Random;

import javafx.scene.image.Image;

public class Avatar {

    private static final List<String> AVATARES = List.of(
            "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Felix",
            "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Max",
            "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Luna",
            "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Leo",
            "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Mia");

    private static final Random random = new Random();

    public String getAvatarAleatorio() {
        int index = random.nextInt(AVATARES.size());
        return AVATARES.get(index);
    }

    // Método para obtener una imagen de avatar local (fallback)
    public Image getDefaultAvatar() {
        try (InputStream is = getClass().getResourceAsStream("/images/icons/user_default.png")) {
            if (is != null) {
                return new Image(is, 44, 44, true, true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Si no se encuentra, crear una imagen en blanco (transparente)
        return new Image(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }
}