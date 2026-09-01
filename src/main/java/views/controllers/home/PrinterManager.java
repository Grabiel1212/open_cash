package views.controllers.home;

import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.print.Printer;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import services.cash.ConfigManagerDB;
import views.alerts.AlertHelper;

public class PrinterManager {

    private final ConfigManagerDB configManager;

    public PrinterManager(ConfigManagerDB configManager) {
        this.configManager = configManager;
    }

    public void cargarImpresoraGuardada(ComboBox<String> cmb) {
        try {
            String impresora = configManager.getImpresoraSeleccionada();
            if (impresora != null && !impresora.isEmpty()) {
                cmb.setValue(impresora);
            }
        } catch (Exception e) {
            System.err.println("❌ Error al cargar impresora: " + e.getMessage());
        }
    }

    public void refrescarImpresoras(ComboBox<String> cmb, StackPane loadingCard) {
        try {
            AnimationUtils.mostrarCarga(loadingCard);
            PauseTransition delay = new PauseTransition(Duration.seconds(1.2));
            delay.setOnFinished(e -> {
                cargarImpresorasReales(cmb);
                AnimationUtils.ocultarCarga(loadingCard);
                cargarImpresoraGuardada(cmb);
            });
            delay.play();
        } catch (Exception e) {
            System.err.println("❌ Error al refrescar impresoras: " + e.getMessage());
        }
    }

    private void cargarImpresorasReales(ComboBox<String> cmb) {
        ObservableList<String> impresoras = FXCollections.observableArrayList();
        Printer.getAllPrinters().forEach(p -> impresoras.add(p.getName()));
        cmb.setItems(impresoras);
        cmb.setPromptText(impresoras.isEmpty() ? "No se encontraron impresoras" : "Seleccione una impresora...");
    }

    public void seleccionarImpresora(ComboBox<String> cmb) {
        try {
            String seleccionada = cmb.getValue();
            if (seleccionada == null || seleccionada.isEmpty()) {
                AnimationUtils.animarError(cmb);
                AlertHelper.mostrar("Advertencia", "Por favor seleccione una impresora.",
                        AlertHelper.AlertType.WARNING);
                return;
            }
            configManager.guardarImpresoraSeleccionada(seleccionada);
            AlertHelper.mostrar("Listo", "Impresora predeterminada: " + seleccionada, AlertHelper.AlertType.INFO);
        } catch (Exception e) {
            AlertHelper.mostrar("Error", "No se pudo guardar la impresora seleccionada.", AlertHelper.AlertType.ERROR);
        }
    }

    public void abrirCaja() {
        try {
            String impresoraSeleccionada = configManager.getImpresoraSeleccionada();
            if (impresoraSeleccionada == null || impresoraSeleccionada.isEmpty()) {
                System.out.println("⚠️ No hay impresora configurada.");
                return;
            }

            PrintService impresora = null;
            for (PrintService ps : PrintServiceLookup.lookupPrintServices(null, null)) {
                if (ps.getName().equalsIgnoreCase(impresoraSeleccionada)) {
                    impresora = ps;
                    break;
                }
            }

            if (impresora == null) {
                System.out.println("❌ No se encontró la impresora configurada: " + impresoraSeleccionada);
                return;
            }

            // Comando ESC/POS para abrir cajón
            byte[] openDrawerCmd = new byte[] {
                    (byte) 27, // ESC
                    (byte) 112, // p
                    (byte) 0, // PIN 2
                    (byte) 25, // tiempo ON
                    (byte) 250 // tiempo OFF
            };

            javax.print.DocPrintJob job = impresora.createPrintJob();
            SimpleDoc doc = new SimpleDoc(openDrawerCmd, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
            job.print(doc, null);

            System.out.println("✅ Comando enviado a la impresora para abrir caja: " + impresora.getName());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Error al intentar abrir la caja: " + e.getMessage());
        }
    }
}