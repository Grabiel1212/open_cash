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

    @FXML
    public void initialize() {

        // 🔹 Cargar iconos
        Icons.setImageIcons(imgSettingHeader, "icons/", "engranaje.png", 20);
        Icons.setImageIcons(imgModoInteligente, "icons/", "chrome.png", 22);
        Icons.setImageIcons(imgImpresionInteligente, "icons/", "boleto.png", 22);

        // 🔹 Leer estado guardado en disco
        modoInteligenteActivo = ModoInteligentePrefs.isModoInteligenteActivo();
        impresionInteligenteActiva = ModoInteligentePrefs.isImpresionInteligenteActiva();

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

    @FXML
    private void toggleModoInteligente() {
        actualizarTexto(switchModoInteligente);
    }

    private void actualizarTexto(ToggleButton tb) {
        tb.setText(tb.isSelected() ? "ON" : "OFF");
    }

    @FXML
    private void guardar() {
        // 🔹 Persistir en disco
        ModoInteligentePrefs.setModoInteligenteActivo(switchModoInteligente.isSelected());
        ModoInteligentePrefs.setImpresionInteligenteActiva(switchImpresionInteligente.isSelected());

        System.out.println("💾 Guardado:");
        System.out.println("   Modo Inteligente      = " + switchModoInteligente.isSelected());
        System.out.println("   Impresión Inteligente = " + switchImpresionInteligente.isSelected());

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