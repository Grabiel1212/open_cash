package views.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import services.tunnel.TunnelService;
import views.controllers.config.ModoInteligentePrefs;
import views.icons.Icons;

public class ConfiguracionController {

    @FXML
    private AnchorPane root;
    @FXML
    private HBox header;
    @FXML
    private Button btnCerrarX, btnCancelar, btnGuardar, btnVolver;

    @FXML
    private ToggleButton switchModoInteligente;
    @FXML
    private ToggleButton switchImpresionInteligente;
    @FXML
    private ToggleButton switchModuloPapelBond;

    @FXML
    private ImageView imgSettingHeader;
    @FXML
    private ImageView imgModoInteligente;
    @FXML
    private ImageView imgImpresionInteligente;
    @FXML
    private ImageView imgModuloPapelBond;

    private Stage stage;
    private double xOffset, yOffset;

    private Runnable onGuardado;
    private Runnable onCerrar;

    public void setOnGuardado(Runnable r) {
        this.onGuardado = r;
    }

    public void setOnCerrar(Runnable r) {
        this.onCerrar = r;
    }

    private boolean ajustandoToggles = false;

    @FXML
    public void initialize() {
        // Iconos
        Icons.setImageIcons(imgSettingHeader, "icons/", "engranaje.png", 20);
        Icons.setImageIcons(imgModoInteligente, "icons/", "chrome.png", 22);
        Icons.setImageIcons(imgImpresionInteligente, "icons/", "boleto.png", 22);
        Icons.setImageIcons(imgModuloPapelBond, "icons/", "boleto.png", 24);

        // Leer estado real desde prefs
        boolean modoActivo = ModoInteligentePrefs.isModoInteligenteActivo();
        boolean printActivo = ModoInteligentePrefs.isImpresionInteligenteActiva();
        boolean papelActivo = ModoInteligentePrefs.isModuloPapelBondActivo();

        // Regla: Modo Inteligente y Impresión Inteligente nunca ambos
        if (modoActivo && printActivo) {
            printActivo = false;
            ModoInteligentePrefs.setImpresionInteligenteActiva(false);
        }

        switchModoInteligente.setSelected(modoActivo);
        actualizarTexto(switchModoInteligente);

        switchImpresionInteligente.setSelected(printActivo);
        actualizarTexto(switchImpresionInteligente);

        switchModuloPapelBond.setSelected(papelActivo);
        actualizarTexto(switchModuloPapelBond);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setModoInteligenteActivo(boolean activo) {
        switchModoInteligente.setSelected(activo);
        actualizarTexto(switchModoInteligente);
    }

    public boolean isModoInteligenteActivo() {
        return switchModoInteligente.isSelected();
    }

    // =====================================================
    // TOGGLES
    // =====================================================
    @FXML
    private void toggleModoInteligente() {
        if (ajustandoToggles)
            return;
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

    @FXML
    private void toggleImpresionInteligente() {
        if (ajustandoToggles)
            return;
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

    @FXML
    private void toggleModuloPapelBond() {
        actualizarTexto(switchModuloPapelBond);
        System.out.println("🔧 [UI] Módulo Papel Bond → " + switchModuloPapelBond.isSelected());
    }

    private void actualizarTexto(ToggleButton tb) {
        tb.setText(tb.isSelected() ? "ON" : "OFF");
    }

    // =====================================================
    // GUARDAR
    // =====================================================
    @FXML
    private void guardar() {

        boolean modo = switchModoInteligente.isSelected();
        boolean print = switchImpresionInteligente.isSelected();
        boolean papel = switchModuloPapelBond.isSelected();

        // Evitar que ambos modos estén activos
        // Sincronizar CDP Tunnel según los dos modos
        if (modo || print) {
            TunnelService.iniciar();
        } else {
            TunnelService.detener();
        }
        // Guardar preferencias
        ModoInteligentePrefs.setModoInteligenteActivo(modo);
        ModoInteligentePrefs.setImpresionInteligenteActiva(print);
        ModoInteligentePrefs.setModuloPapelBondActivo(papel);

        // =========================================
        // SINCRONIZAR CDP TUNNEL
        // =========================================

        boolean tunnelDebeEstarActivo = modo || print;

        if (tunnelDebeEstarActivo) {

            System.out.println("CDP Tunnel: ENCENDIENDO...");

            TunnelService.iniciar();

        } else {

            System.out.println("CDP Tunnel: APAGANDO...");

            TunnelService.detener();
        }

        System.out.println("Guardado:");
        System.out.println("Modo Inteligente = " + modo);
        System.out.println("Impresión Inteligente = " + print);
        System.out.println("Módulo Papel Bond = " + papel);
        System.out.println("CDP Tunnel = " + tunnelDebeEstarActivo);

        if (onGuardado != null) {
            onGuardado.run();
        }

        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    private void cerrar() {
        if (stage != null) {
            stage.close();
        } else if (onCerrar != null) {
            onCerrar.run();
        }
    }
}