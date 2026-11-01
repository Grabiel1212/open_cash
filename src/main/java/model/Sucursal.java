package model;

public class Sucursal {
    private Long idSucursal;
    private String nombre;

    public Sucursal(Long idSucursal, String nombre) {
        this.idSucursal = idSucursal;
        this.nombre = nombre;
    }

    public Sucursal() {
    }

    public Long getIdSucursal() {
        return idSucursal;
    }

    public void setIdSucursal(Long idSucursal) {
        this.idSucursal = idSucursal;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

}
