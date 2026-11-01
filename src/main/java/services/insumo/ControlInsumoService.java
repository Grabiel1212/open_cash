package services.insumo;

public class ControlInsumoService {

    private static final ControlInsumoService INSTANCE = new ControlInsumoService();

    private Long ricohBn;
    private Long ricohColor;
    private Long epsonBn;
    private Long epsonColor;

    /** ID del registro creado en BD (control_impresion.id) — solo en memoria. */
    private Long idControlImpresion;

    private ControlInsumoService() {
    }

    public static ControlInsumoService getInstance() {
        return INSTANCE;
    }

    // ============================================================
    // CONSULTAS
    // ============================================================

    public synchronized boolean tieneRicoh() {
        return ricohBn != null || ricohColor != null;
    }

    public synchronized boolean tieneEpson() {
        return epsonBn != null || epsonColor != null;
    }

    public synchronized boolean hayAlgunContador() {
        return tieneRicoh() || tieneEpson();
    }

    public synchronized long getRicohBn() {
        return ricohBn == null ? 0L : ricohBn;
    }

    public synchronized long getRicohColor() {
        return ricohColor == null ? 0L : ricohColor;
    }

    public synchronized long getRicohTotal() {
        return getRicohBn() + getRicohColor();
    }

    public synchronized long getEpsonBn() {
        return epsonBn == null ? 0L : epsonBn;
    }

    public synchronized long getEpsonColor() {
        return epsonColor == null ? 0L : epsonColor;
    }

    public synchronized long getEpsonTotal() {
        return getEpsonBn() + getEpsonColor();
    }

    // ============================================================
    // REGISTRO EN MEMORIA
    // ============================================================

    public synchronized void registrarRicoh(long bn, long color) {
        if (bn < 0 || color < 0)
            throw new IllegalArgumentException("El contador no puede ser negativo.");
        if (tieneRicoh())
            throw new IllegalStateException("El contador de la Ricoh ya fue registrado.");
        ricohBn = bn;
        ricohColor = color;
    }

    public synchronized void registrarEpson(long bn, long color) {
        if (bn < 0 || color < 0)
            throw new IllegalArgumentException("El contador no puede ser negativo.");
        if (tieneEpson())
            throw new IllegalStateException("El contador de la Epson ya fue registrado.");
        epsonBn = bn;
        epsonColor = color;
    }

    // ============================================================
    // ID DEL REGISTRO EN BD (en memoria)
    // ============================================================

    public synchronized Long getIdControlImpresion() {
        return idControlImpresion;
    }

    public synchronized void setIdControlImpresion(Long id) {
        this.idControlImpresion = id;
    }

    public synchronized boolean tieneRegistroEnBD() {
        return idControlImpresion != null;
    }

    // ============================================================
    // 🆕 LIMPIAR TODO EL ESTADO (al cerrar el turno)
    // ============================================================

    /**
     * Reinicia todos los contadores y el ID de control en memoria.
     * Se llama después de un cierre exitoso (CrearConteoFinal OK)
     * para que el siguiente ciclo empiece desde cero.
     */
    public synchronized void limpiar() {
        ricohBn = null;
        ricohColor = null;
        epsonBn = null;
        epsonColor = null;
        idControlImpresion = null;
    }
}