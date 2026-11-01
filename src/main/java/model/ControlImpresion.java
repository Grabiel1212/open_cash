
package model;

import java.time.LocalDateTime;

public class ControlImpresion {

    private Long idControl;
    private Long idSucursal;

    private LocalDateTime fechaHoraInicio;

    private Long inicialRicoh;
    private Long inicialEpson;

    private LocalDateTime fechaHoraFinal;

    private Long finalRicoh;
    private Long finalEpson;

    private String estado;
    private String observacion;

    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    // Constructor vacío
    public ControlImpresion() {
    }

    // Constructor para registrar la apertura
    public ControlImpresion(
            Long idSucursal,
            Long inicialRicoh,
            Long inicialEpson,
            String observacion) {

        this.idSucursal = idSucursal;
        this.inicialRicoh = inicialRicoh;
        this.inicialEpson = inicialEpson;
        this.observacion = observacion;
        this.estado = "ABIERTO";
    }

    // Constructor completo
    public ControlImpresion(
            Long idControl,
            Long idSucursal,
            LocalDateTime fechaHoraInicio,
            Long inicialRicoh,
            Long inicialEpson,
            LocalDateTime fechaHoraFinal,
            Long finalRicoh,
            Long finalEpson,
            String estado,
            String observacion,
            LocalDateTime creadoEn,
            LocalDateTime actualizadoEn) {

        this.idControl = idControl;
        this.idSucursal = idSucursal;
        this.fechaHoraInicio = fechaHoraInicio;
        this.inicialRicoh = inicialRicoh;
        this.inicialEpson = inicialEpson;
        this.fechaHoraFinal = fechaHoraFinal;
        this.finalRicoh = finalRicoh;
        this.finalEpson = finalEpson;
        this.estado = estado;
        this.observacion = observacion;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    // Getters y setters

    public Long getIdControl() {
        return idControl;
    }

    public void setIdControl(Long idControl) {
        this.idControl = idControl;
    }

    public Long getIdSucursal() {
        return idSucursal;
    }

    public void setIdSucursal(Long idSucursal) {
        this.idSucursal = idSucursal;
    }

    public LocalDateTime getFechaHoraInicio() {
        return fechaHoraInicio;
    }

    public void setFechaHoraInicio(LocalDateTime fechaHoraInicio) {
        this.fechaHoraInicio = fechaHoraInicio;
    }

    public Long getInicialRicoh() {
        return inicialRicoh;
    }

    public void setInicialRicoh(Long inicialRicoh) {
        this.inicialRicoh = inicialRicoh;
    }

    public Long getInicialEpson() {
        return inicialEpson;
    }

    public void setInicialEpson(Long inicialEpson) {
        this.inicialEpson = inicialEpson;
    }

    public LocalDateTime getFechaHoraFinal() {
        return fechaHoraFinal;
    }

    public void setFechaHoraFinal(LocalDateTime fechaHoraFinal) {
        this.fechaHoraFinal = fechaHoraFinal;
    }

    public Long getFinalRicoh() {
        return finalRicoh;
    }

    public void setFinalRicoh(Long finalRicoh) {
        this.finalRicoh = finalRicoh;
    }

    public Long getFinalEpson() {
        return finalEpson;
    }

    public void setFinalEpson(Long finalEpson) {
        this.finalEpson = finalEpson;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }

    // Cantidad de impresiones realizadas por cada impresora

    public Long getTotalRicoh() {
        if (finalRicoh == null) {
            return null;
        }

        return finalRicoh - inicialRicoh;
    }

    public Long getTotalEpson() {
        if (finalEpson == null) {
            return null;
        }

        return finalEpson - inicialEpson;
    }

    // Total general de impresiones

    public Long getTotalImpresiones() {
        Long ricoh = getTotalRicoh();
        Long epson = getTotalEpson();

        if (ricoh == null || epson == null) {
            return null;
        }

        return ricoh + epson;
    }

    // Verificar si el control sigue abierto

    public boolean estaAbierto() {
        return "ABIERTO".equals(estado);
    }

    // Verificar si el control está finalizado

    public boolean estaFinalizado() {
        return "FINALIZADO".equals(estado);
    }
}
