package views.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import views.controllers.config.ModoInteligentePrefs;
import views.icons.Icons;

public class ConfiguracionController {

    @FXML
    private AnchorPane root;
    @FXML
    private HBox header;
    @FXML
    private Button btnCerrarX, btnCancelar, btnGuardar;
    @FXML
    private ToggleButton switchModoInteligente;
    @FXML
    private ToggleButton switchImpresionInteligente;

    @FXML
    private ImageView imgSettingHeader;
    @FXML
    private ImageView imgModoInteligente;
    @FXML
    private ImageView imgImpresionInteligente;

    private Stage stage;
    private double xOffset, yOffset;

    // Estado temporal (mientras el modal está abierto, no se guarda hasta pulsar
    // GUARDAR)
    private boolean modoInteligenteActivo;
    private boolean impresionInteligenteActiva;

    /** Bandera interna para evitar que los toggles se pisen entre sí. */
    private boolean ajustandoToggles = false;

    @FXML
    public void initialize() {

        // 🔹 Cargar iconos
        Icons.setImageIcons(imgSettingHeader, "icons/", "engranaje.png", 20);
        Icons.setImageIcons(imgModoInteligente, "icons/", "chrome.png", 22);
        Icons.setImageIcons(imgImpresionInteligente, "icons/", "boleto.png", 22);

        // 🔹 Leer estado guardado en disco
        modoInteligenteActivo = ModoInteligentePrefs.isModoInteligenteActivo();
        impresionInteligenteActiva = ModoInteligentePrefs.isImpresionInteligenteActiva();

        // 🔹 Si por algún motivo quedaron ambos activos guardados en disco,
        // dejamos ganar al Modo Inteligente (navegador) y apagamos el de impresión.
        if (modoInteligenteActivo && impresionInteligenteActiva) {
            impresionInteligenteActiva = false;
            ModoInteligentePrefs.setImpresionInteligenteActiva(false);
        }

        // 🔹 Reflejarlo en los switches
        switchModoInteligente.setSelected(modoInteligenteActivo);
        actualizarTexto(switchModoInteligente);

        switchImpresionInteligente.setSelected(impresionInteligenteActiva);
        actualizarTexto(switchImpresionInteligente);

        setupDrag();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /** Ya no es necesario este setter externo, pero lo dejo por compatibilidad. */
    public void setModoInteligenteActivo(boolean activo) {
        this.modoInteligenteActivo = activo;
        switchModoInteligente.setSelected(activo);
        actualizarTexto(switchModoInteligente);
    }

    public boolean isModoInteligenteActivo() {
        return switchModoInteligente.isSelected();
    }

    // =====================================================
    // TOGGLE 1: Modo Inteligente (navegador)
    // =====================================================
    @FXML
    private void toggleModoInteligente() {
        if (ajustandoToggles)
            return;

        // Si el usuario acaba de ACTIVAR el Modo Inteligente (navegador)
        // → apagamos Impresión Inteligente para que NO se abra la caja dos veces.
        if (switchModoInteligente.isSelected()) {
            ajustandoToggles = true;
            try {
                if (switchImpresionInteligente.isSelected()) {
                    switchImpresionInteligente.setSelected(false);
                    actualizarTexto(switchImpresionInteligente);
                }
            } finally {
                ajustandoToggles = false;
            }
        }

        actualizarTexto(switchModoInteligente);
    }

    // =====================================================
    // TOGGLE 2: Impresión Inteligente
    // =====================================================
    @FXML
    private void toggleImpresionInteligente() {
        if (ajustandoToggles)
            return;

        // Si el usuario acaba de ACTIVAR Impresión Inteligente
        // → apagamos Modo Inteligente (navegador) para evitar doble apertura de caja.
        if (switchImpresionInteligente.isSelected()) {
            ajustandoToggles = true;
            try {
                if (switchModoInteligente.isSelected()) {
                    switchModoInteligente.setSelected(false);
                    actualizarTexto(switchModoInteligente);
                }
            } finally {
                ajustandoToggles = false;
            }
        }

        actualizarTexto(switchImpresionInteligente);
    }

    private void actualizarTexto(ToggleButton tb) {
        tb.setText(tb.isSelected() ? "ON" : "OFF");
    }

    // =====================================================
    // GUARDAR
    // =====================================================
    @FXML
    private void guardar() {

        // 🔒 Regla de oro: nunca los dos activos.
        // Si por alguna razón ambos quedaron ON, priorizamos el último
        // que el usuario tocó. Como los toggles ya son excluyentes,
        // esto es defensa por si algo externo los modificó.
        boolean modo = switchModoInteligente.isSelected();
        boolean print = switchImpresionInteligente.isSelected();

        if (modo && print) {
            // No debería pasar, pero por seguridad:
            print = false;
            switchImpresionInteligente.setSelected(false);
            actualizarTexto(switchImpresionInteligente);
        }

        // 🔹 Persistir en disco
        ModoInteligentePrefs.setModoInteligenteActivo(modo);
        ModoInteligentePrefs.setImpresionInteligenteActiva(print);

        System.out.println("💾 Guardado:");
        System.out.println("   Modo Inteligente      = " + modo);
        System.out.println("   Impresión Inteligente = " + print);

        cerrar();
    }

    @FXML
    private void cerrar() {
        if (stage != null)
            stage.close();
    }

    private void setupDrag() {
        header.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        header.setOnMouseDragged(e -> {
            if (stage != null) {
                stage.setX(e.getScreenX() - xOffset);
                stage.setY(e.getScreenY() - yOffset);
            }
        });
    }
}