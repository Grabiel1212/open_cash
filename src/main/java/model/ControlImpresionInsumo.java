
package model;

import java.time.LocalDateTime;

public class ControlImpresionInsumo {

    private Long idDetalle;
    private Long idControl;

    private String tipoInsumo;
    private String tamano;

    private Long cantidad;

    private LocalDateTime creadoEn;

    // Constructor vacío
    public ControlImpresionInsumo() {
    }

    // Constructor para registrar un insumo
    public ControlImpresionInsumo(
            Long idControl,
            String tipoInsumo,
            String tamano,
            Long cantidad) {

        this.idControl = idControl;
        this.tipoInsumo = tipoInsumo;
        this.tamano = tamano;
        this.cantidad = cantidad;
    }

    // Constructor completo
    public ControlImpresionInsumo(
            Long idDetalle,
            Long idControl,
            String tipoInsumo,
            String tamano,
            Long cantidad,
            LocalDateTime creadoEn) {

        this.idDetalle = idDetalle;
        this.idControl = idControl;
        this.tipoInsumo = tipoInsumo;
        this.tamano = tamano;
        this.cantidad = cantidad;
        this.creadoEn = creadoEn;
    }

    // Getters y setters

    public Long getIdDetalle() {
        return idDetalle;
    }

    public void setIdDetalle(Long idDetalle) {
        this.idDetalle = idDetalle;
    }

    public Long getIdControl() {
        return idControl;
    }

    public void setIdControl(Long idControl) {
        this.idControl = idControl;
    }

    public String getTipoInsumo() {
        return tipoInsumo;
    }

    public void setTipoInsumo(String tipoInsumo) {
        this.tipoInsumo = tipoInsumo;
    }

    public String getTamano() {
        return tamano;
    }

    public void setTamano(String tamano) {
        this.tamano = tamano;
    }

    public Long getCantidad() {
        return cantidad;
    }

    public void setCantidad(Long cantidad) {
        this.cantidad = cantidad;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
