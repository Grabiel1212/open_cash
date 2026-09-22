package services.navegador;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import helpers.MensajeHelper;

/**
 * Servicio que recibe un resumen de venta YA PROCESADO por el navegador.
 *
 * Reglas:
 * - Abre caja si la venta tiene AL MENOS UN pago en EFECTIVO.
 * - Si la venta es solo CULQUI / YAPE / PLIN / OPENPAY → NO abre.
 * - Cada venta se procesa UNA sola vez (dedup por claveUnica).
 * - Thread-safe: bloquea procesamiento concurrente.
 *
 * FIX 2026-A: la apertura de caja ya NO usa Platform.runLater().
 * Cuando la app se oculta en bandeja (Stage.hide()), el hilo de JavaFX
 * puede dejar de procesar runLater(), por lo que la caja nunca se abría.
 * PrinterManager.abrirCaja() NO toca JavaFX → usamos un executor propio.
 *
 * FIX 2026-B: el ERP a veces envía SIEMPRE la misma fecha
 * (2026-09-17T05:00:00Z),
 * lo que hacía que la clave fallback FB:... colisionara entre ventas distintas
 * con igual total. Ahora reforzamos la clave FB con un hash de los pagos y
 * productos, y logueamos cuando se descarta un duplicado.
 */
public class VentaListenerService {

    private static final int MAX_HISTORIAL = 500;

    /**
     * Executor dedicado: NO depende del hilo de JavaFX.
     * Funciona con la app visible o minimizada en la bandeja.
     */
    private static final ExecutorService CAJA_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AbrirCaja");
        t.setDaemon(true);
        return t;
    });

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

        // 2) Deduplicar (con log para que un descarte NUNCA sea silencioso)
        synchronized (lock) {
            if (ventasProcesadas.containsKey(clave)) {
                System.out.println("⏭️ Venta descartada por dedup: " + clave);
                return false;
            }
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
    // CLAVE ÚNICA
    // =====================================================
    private String extraerClaveUnica(Map<?, ?> r) {

        // El processor ya generó una clave fuerte (incluye hash del JSON si es FB:).
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

    /**
     * Hash estable del contenido de pagos y montos.
     * Evita que dos ventas con el mismo total pero distinto detalle colisionen.
     */
    private String hashContenido(Map<?, ?> r) {
        StringBuilder sb = new StringBuilder();

        Object det = r.get("detallePagos");
        if (det instanceof List<?> l) {
            for (Object o : l) {
                sb.append(o).append(';');
            }
        }

        Object mon = r.get("montosPago");
        if (mon instanceof List<?> l) {
            for (Object o : l) {
                sb.append(o).append(';');
            }
        }

        if (sb.length() == 0)
            return "0";

        return Integer.toHexString(sb.toString().hashCode());
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
        System.out.flush();
    }

    // =====================================================
    // ABRIR CAJA (sin Platform.runLater)
    // =====================================================
    private void dispararAperturaCaja(int numero, String clave) {
        System.out.println("💵 Venta #" + numero + " EFECTIVO → abriendo caja [" + clave + "]");

        if (abrirCajaCallback == null) {
            System.out.println("⚠️ abrirCajaCallback es null. No se puede abrir caja.");
            return;
        }

        CAJA_EXECUTOR.submit(() -> {
            try {
                abrirCajaCallback.run();
                System.out.println("✅ Caja abierta correctamente (venta #" + numero + ")");
            } catch (Exception e) {
                System.err.println("❌ Error abriendo caja venta #" + numero + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
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