package views.controllers.control_insumo;

import java.io.IOException;

import helpers.AlertaHelper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import repository.ControlImpresionRepository;
import services.sql.ControlmpresionService;
import views.controllers.config.SucursalPrefs;

public class PapelBondController {

    @FXML
    private Button btnRegistrarInicio;
    @FXML
    private Button btnRegistrarCierre;
    @FXML
    private Label lblEstado;

    private final ControlImpresionRepository controlService = new ControlmpresionService();

    // ================================================================
    // INIT
    // ================================================================
    @FXML
    public void initialize() {
        actualizarEstadoBotones();
    }

    // ================================================================
    // Estado de botones
    // ================================================================
    private void actualizarEstadoBotones() {

        Long idSucursal = SucursalPrefs.getId();

        if (idSucursal == null) {
            btnRegistrarInicio.setDisable(true);
            btnRegistrarCierre.setDisable(true);
            if (lblEstado != null) {
                lblEstado.setText("⚠ No hay sucursal seleccionada");
                lblEstado.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 13px;");
            }
            return;
        }

        boolean abierto = controlService.ExisteAbiertoPorSucursal(idSucursal);

        btnRegistrarInicio.setDisable(abierto);
        btnRegistrarCierre.setDisable(!abierto);

        if (lblEstado != null) {
            if (abierto) {
                lblEstado.setText(
                        "⚠ Ya existe un control ABIERTO en esta sucursal.\n"
                                + "Registra el cierre para poder crear uno nuevo.");
                lblEstado.setStyle("-fx-text-fill: #b45309; -fx-font-size: 13px;");
            } else {
                lblEstado.setText("✔ Sin controles abiertos. Puedes registrar un nuevo inicio.");
                lblEstado.setStyle("-fx-text-fill: #059669; -fx-font-size: 13px;");
            }
        }
    }

    // ================================================================
    // REGISTRAR INICIO → registro.fxml
    // ================================================================
    @FXML
    private void registrarInicio() {

        Long idSucursal = SucursalPrefs.getId();

        if (idSucursal == null) {
            mostrarError("Sucursal no configurada",
                    "Debes seleccionar una sucursal primero.");
            return;
        }

        if (controlService.ExisteAbiertoPorSucursal(idSucursal)) {
            mostrarError("Control ya abierto",
                    "Ya existe un control ABIERTO para esta sucursal.\n\n"
                            + "Debes registrar el cierre antes de crear uno nuevo.");
            actualizarEstadoBotones();
            return;
        }

        abrirRegistro(
                "/fx/control_isumo/registro.fxml",
                "Registro inicial del contador");

        actualizarEstadoBotones();
    }

    // ================================================================
    // REGISTRAR CIERRE → registro_final.fxml
    // ================================================================
    @FXML
    private void registrarCierre() {

        Long idSucursal = SucursalPrefs.getId();

        if (idSucursal == null) {
            mostrarError("Sucursal no configurada",
                    "Debes seleccionar una sucursal primero.");
            return;
        }

        if (!controlService.ExisteAbiertoPorSucursal(idSucursal)) {
            mostrarError("Sin control abierto",
                    "No existe un control ABIERTO en esta sucursal.\n\n"
                            + "Primero debes registrar el conteo inicial.");
            actualizarEstadoBotones();
            return;
        }

        abrirRegistro(
                "/fx/control_isumo/registro_final.fxml",
                "Registro final del contador");

        actualizarEstadoBotones();
    }

    // ================================================================
    // Abre un FXML como ventana modal por encima del padre
    // ================================================================
    private void abrirRegistro(String fxmlPath, String titulo) {
        try {
            var url = getClass().getResource(fxmlPath);
            if (url == null) {
                mostrarError("Recurso no encontrado",
                        "No se encontró el archivo:\n" + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(url);
            Scene scene = new Scene(loader.load());
            scene.setFill(Color.TRANSPARENT);

            Stage modal = new Stage();
            modal.setTitle(titulo);
            modal.initStyle(StageStyle.TRANSPARENT);
            modal.initModality(Modality.APPLICATION_MODAL);

            Stage owner = ventana();
            if (owner != null) {
                modal.initOwner(owner);
            }

            modal.setScene(scene);

            modal.setOnShown(ev -> {
                modal.setAlwaysOnTop(true);
                modal.toFront();
                modal.requestFocus();
            });

            modal.showAndWait();

        } catch (IOException e) {
            // Log a consola (sin depender de MensajeHelper)
            System.err.println("❌ Error abriendo '" + titulo + "': " + e.getMessage());
            e.printStackTrace();

            mostrarError("No se pudo abrir la ventana",
                    "No se pudo abrir la ventana de registro.\n\n" + e.getMessage());
        }
    }

    // ================================================================
    // Helpers
    // ================================================================
    private Stage ventana() {
        if (btnRegistrarInicio == null || btnRegistrarInicio.getScene() == null)
            return null;
        return (Stage) btnRegistrarInicio.getScene().getWindow();
    }

    private void mostrarError(String titulo, String mensaje) {
        AlertaHelper.error(ventana(), titulo, mensaje);
    }
}