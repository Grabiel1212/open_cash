package services.navegador.apertura;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;

import helpers.MensajeHelper;
import services.tunnel.TunnelConfig;

/**
 * Monitor de ventas KeyFácil (page-level).
 *
 * Toda la configuración/conexión CDP se delega a TunnelConfig
 * (services.tunnel),
 * compartida con ChromePrintService. La lógica del monitor NO cambia.
 *
 * Se mantienen aquí:
 * - GSON → KeyFacilSession lo referencia como KeyFacilMonitorService.GSON
 * - appStartTime() → KeyFacilSession lo usa para filtrar logs viejos
 */
public class KeyFacilMonitorService {

    /** Delegado: KeyFacilSession usa KeyFacilMonitorService.GSON. */
    static final Gson GSON = TunnelConfig.GSON;

    private static final long APP_START_TIME = System.currentTimeMillis();

    static long appStartTime() {
        return APP_START_TIME;
    }

    private final VentaListenerService ventaListener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    private final Map<String, KeyFacilSession> sessions = new ConcurrentHashMap<>();

    public KeyFacilMonitorService(VentaListenerService ventaListener) {
        this.ventaListener = ventaListener;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start() {
        if (!running.compareAndSet(false, true))
            return;
        worker = new Thread(this::buclePrincipal, "KeyFacilMonitor");
        worker.setDaemon(true);
        worker.start();
        MensajeHelper.info("🟢 Monitor KeyFacil iniciado (page-level, puerto "
                + TunnelConfig.CDP_PROXY_PORT + ")");
    }

    public void stop() {
        if (!running.compareAndSet(true, false))
            return;
        if (worker != null)
            worker.interrupt();
        for (KeyFacilSession s : sessions.values())
            s.close();
        sessions.clear();
        MensajeHelper.info("🛑 Monitor KeyFacil detenido");
    }

    private void buclePrincipal() {
        try {
            if (!TunnelConfig.disponible()) {
                MensajeHelper.error("El puente CDP no responde en " + TunnelConfig.CDP_BASE, null);
                running.set(false);
                return;
            }

            System.out.println("🟢 Conectado a cdp-tunnel → " + TunnelConfig.CDP_BASE + " (page-level)");

            boolean avisoVacio = false;

            while (running.get()) {
                List<Map<String, Object>> targets = TunnelConfig.obtenerTargets();

                if (targets.isEmpty()) {
                    if (!avisoVacio) {
                        MensajeHelper.advertencia(
                                "El túnel responde pero no hay pestañas de KeyFacil.\n" +
                                        "Abre " + TunnelConfig.URL_NUEVA_VENTA + " en Chrome.");
                        avisoVacio = true;
                    }
                } else {
                    avisoVacio = false;
                }

                // 1) Descartar sesiones muertas o cuya pestaña ya no existe
                Set<String> idsActuales = new HashSet<>();
                for (Map<String, Object> t : targets) {
                    Object id = t.get("id");
                    if (id != null)
                        idsActuales.add(id.toString());
                }

                Iterator<Map.Entry<String, KeyFacilSession>> it = sessions.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, KeyFacilSession> e = it.next();
                    if (!idsActuales.contains(e.getKey()) || !e.getValue().isAlive()) {
                        e.getValue().close();
                        it.remove();
                        System.out.println("🔴 Sesión cerrada: " + e.getKey());
                    }
                }

                // 2) Crear sesión para cada pestaña nueva de KeyFacil
                for (Map<String, Object> t : targets) {
                    Object type = t.get("type");
                    Object urlObj = t.get("url");
                    Object idObj = t.get("id");
                    Object wsObj = t.get("webSocketDebuggerUrl");

                    if (type == null || urlObj == null || idObj == null || wsObj == null)
                        continue;
                    if (!"page".equals(type.toString()))
                        continue;
                    if (!urlObj.toString().contains(TunnelConfig.KEYFACIL_HOST))
                        continue;

                    String id = idObj.toString();
                    if (sessions.containsKey(id))
                        continue;

                    try {
                        KeyFacilSession session = new KeyFacilSession(
                                id, wsObj.toString(), ventaListener);
                        sessions.put(id, session);
                        session.start();
                        System.out.println("🆕 Sesión creada: " + id.substring(0, 8)
                                + " → " + urlObj);
                    } catch (Exception e) {
                        System.err.println("❌ No se pudo crear sesión " + id.substring(0, 8)
                                + ": " + e.getMessage());
                    }
                }

                Thread.sleep(1000);
            }

        } catch (InterruptedException ie) {
            // cierre limpio
        } catch (Exception e) {
            MensajeHelper.error("Error en monitor: " + e.getMessage(), e);
        } finally {
            running.set(false);
            for (KeyFacilSession s : sessions.values())
                s.close();
            sessions.clear();
        }
    }

    public void abrirNavegadorKeyFacil() {
        MensajeHelper.info(
                "Abre esta URL en tu Chrome:\n" +
                        TunnelConfig.URL_NUEVA_VENTA + "\n\n" +
                        "El monitor se enganchará automáticamente.");
    }
}