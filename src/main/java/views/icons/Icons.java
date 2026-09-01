package views.icons;

import helpers.MensajeHelper;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class Icons {

    public static void setButtonIcons(Button button, String url, String iconName, double tamaño) {
        try {
            String path = "/images/" + url + iconName;

            var resource = Icons.class.getResource(path);
            if (resource == null) {
                throw new IllegalArgumentException("No se encontró el recurso: " + path);
            }

            // Cargar imagen
            Image icon = new Image(resource.toExternalForm(), false);

            // Crear ImageView preservando proporciones
            ImageView iconView = new ImageView(icon);
            iconView.setPreserveRatio(true); // 🔹 no se deforma
            iconView.setSmooth(true); // 🔹 mejor calidad al escalar
            iconView.setCache(true); // 🔹 mejora rendimiento

            // Aplicar tamaño deseado (límite máximo)
            iconView.setFitWidth(tamaño);
            iconView.setFitHeight(tamaño);

            // Asignar al botón
            button.setGraphic(iconView);

        } catch (Exception e) {
            MensajeHelper.info("No se pudo cargar el icono: " + iconName + " --> " + e.getMessage());
        }
    }

    public static void setImageToImageView(ImageView imageView, String url, String iconName, double tamaño) {
        try {
            String path = "/images/" + url + iconName;

            var resource = Icons.class.getResource(path);
            if (resource == null) {
                throw new IllegalArgumentException("No se encontró el recurso: " + path);
            }

            Image icon = new Image(resource.toExternalForm(), false);

            imageView.setImage(icon);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setCache(true);
            imageView.setFitWidth(tamaño);
            imageView.setFitHeight(tamaño);

        } catch (Exception e) {
            System.out.println("No se pudo cargar la imagen del logo: " + iconName + " --> " + e.getMessage());
        }
    }

    public static void setImageIcons(ImageView view, String url, String iconName, double size) {
        try {
            // Ruta absoluta dentro de resources
            String path = "/images/" + url + iconName;
            var resource = Icons.class.getResource(path);

            if (resource == null) {
                throw new IllegalArgumentException("No se encontró el recurso: " + path);
            }

            // Cargar imagen y aplicar propiedades
            Image icon = new Image(resource.toExternalForm(), size, size, true, true);
            view.setImage(icon);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setCache(true);

            // Asegurar que sea visible
            view.setVisible(true);
            view.setManaged(true);

            System.out.println("✅ Cargado: " + path);

        } catch (Exception e) {
            System.err.println("⚠️ Error cargando icono '" + iconName + "': " + e.getMessage());
        }
    }

}
