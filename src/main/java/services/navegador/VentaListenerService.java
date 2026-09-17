package services.navegador;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import helpers.MensajeHelper;
import javafx.application.Platform;

/**
 * Servicio que recibe un resumen de venta YA PROCESADO por el navegador.
 *
 * Reglas:
 * - Abre caja si la venta tiene AL MENOS UN pago en EFECTIVO.
 * - Si la venta es solo CULQUI / YAPE / PLIN / OPENPAY → NO abre.
 * - Cada venta se procesa UNA sola vez (dedup por claveUnica que envía JS).
 * - Thread-safe: bloquea procesamiento concurrente.
 */
public class VentaListenerService {

    private static final int MAX_HISTORIAL = 500;

    private final Runnable abrirCajaCallback;
    private final AtomicInteger contadorVentas = new AtomicInteger(0);

    private final Map<String, Boolean> ventasProcesadas = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_HISTORIAL;
                }
            });

    private final Object lock = new Object();

    public VentaListenerService(Runnable abrirCajaCallback) {
        this.abrirCajaCallback = abrirCajaCallback;
    }

    /**
     * Procesa el resumen de venta ya extraído por el JS del navegador.
     * Campos esperados: claveUnica, uuid, id, serie, numero, total, cliente,
     * responsable, pago, detallePagos, montosPago, esEfectivo.
     *
     * @return true si se abrió la caja (había al menos un pago efectivo).
     */
    public boolean procesarVenta(Map<?, ?> resumen) {

        if (resumen == null || resumen.isEmpty())
            return false;

        // 1) Clave única
        String clave = extraerClaveUnica(resumen);
        if (clave == null) {
            MensajeHelper.advertencia("Resumen sin claveUnica. Claves: " + resumen.keySet());
            return false;
        }

        // 2) Deduplicar
        synchronized (lock) {
            if (ventasProcesadas.containsKey(clave))
                return false;
            ventasProcesadas.put(clave, Boolean.TRUE);
        }

        int numero = contadorVentas.incrementAndGet();

        // 3) Leer campos
        String serie = str(resumen.get("serie"));
        String idNumero = str(resumen.get("numero"));
        String total = str(resumen.get("total"));
        String cliente = str(resumen.get("cliente"));
        String responsable = str(resumen.get("responsable"));
        String pago = str(resumen.get("pago"));
        boolean esEfectivo = toBool(resumen.get("esEfectivo"));

        // 4) Imprimir bloque
        imprimirBloque(numero, clave, serie, idNumero, total,
                cliente, responsable, pago, esEfectivo, resumen);

        // 5) Abrir caja solo si hay al menos un pago en efectivo
        if (esEfectivo) {
            dispararAperturaCaja(numero, clave);
            return true;
        } else {
            MensajeHelper.info("Venta #" + numero + " (" + pago
                    + ") → NO contiene efectivo. No se abre caja.");
            return false;
        }
    }

    public void reset() {
        synchronized (lock) {
            ventasProcesadas.clear();
            contadorVentas.set(0);
        }
    }

    // =====================================================
    // CLAVE ÚNICA (usa la que ya calculó el JS)
    // =====================================================
    private String extraerClaveUnica(Map<?, ?> r) {

        // El JS ya generó la claveUnica (UUID/ID/Fallback)
        Object clave = r.get("claveUnica");
        if (clave != null && !clave.toString().isBlank()
                && !"null".equals(clave.toString())) {
            return clave.toString();
        }

        // Compatibilidad por si algún JSON viejo llega sin claveUnica
        Object uuid = r.get("uuid");
        if (uuid != null && !uuid.toString().isBlank()
                && !"null".equals(uuid.toString())) {
            return "UUID:" + uuid;
        }

        Object id = r.get("id");
        if (id != null && !"null".equals(id.toString())) {
            return "ID:" + id;
        }

        return null;
    }

    // =====================================================
    // IMPRIMIR BLOQUE
    // =====================================================
    private void imprimirBloque(int numero, String clave,
            String serie, String idNumero,
            String total, String cliente,
            String responsable, String pago,
            boolean esEfectivo,
            Map<?, ?> resumen) {

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.printf("║  VENTA #%03d PROCESADA%n", numero);
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.printf("║  Comprobante : %s-%s%n", serie, idNumero);
        System.out.printf("║  Cliente     : %s%n", recortar(cliente, 40));
        System.out.printf("║  Total       : S/ %s%n", total);
        System.out.printf("║  Pago        : %s%n", recortar(pago, 40));

        Object detalleObj = resumen.get("detallePagos");
        if (detalleObj instanceof List<?> lista && !lista.isEmpty()) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < lista.size(); i++) {
                if (i > 0)
                    sb.append(", ");
                sb.append(lista.get(i));
            }
            sb.append("]");
            System.out.printf("║  Detalle     : %s%n", recortar(sb.toString(), 40));
        }

        Object montosObj = resumen.get("montosPago");
        if (montosObj instanceof List<?> montos && !montos.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < montos.size(); i++) {
                if (i > 0)
                    sb.append(" + ");
                sb.append("S/ ").append(montos.get(i));
            }
            System.out.printf("║  Montos      : %s%n", recortar(sb.toString(), 40));
        }

        System.out.printf("║  Responsable : %s%n", recortar(responsable, 40));
        System.out.printf("║  ¿Efectivo?  : %s%n",
                esEfectivo ? "SÍ ✅  → ABRIR CAJA" : "NO ❌  → NO abrir");
        System.out.printf("║  Clave única : %s%n", recortar(clave, 40));
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
    }

    // =====================================================
    // ABRIR CAJA
    // =====================================================
    private void dispararAperturaCaja(int numero, String clave) {
        MensajeHelper.info("💵 Venta #" + numero + " EFECTIVO → abriendo caja [" + clave + "]");
        if (abrirCajaCallback != null) {
            Platform.runLater(() -> {
                try {
                    abrirCajaCallback.run();
                } catch (Exception e) {
                    MensajeHelper.error("Error al abrir caja para venta #" + numero, e);
                }
            });
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================
    private String str(Object o) {
        return o == null ? "?" : o.toString();
    }

    private boolean toBool(Object o) {
        if (o == null)
            return false;
        if (o instanceof Boolean b)
            return b;
        return Boolean.parseBoolean(o.toString());
    }

    private String recortar(String s, int max) {
        if (s == null)
            return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}