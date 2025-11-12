package model;


public class Cajero {
    private Integer idCajero;
    private String nombre;
    private String pin;
    private Integer idUser;

    public Cajero() {
    }

    public Cajero(Integer idCajero, String nombre, String pin, Integer idUser) {
        this.idCajero = idCajero;
        this.nombre = nombre;
        this.pin = pin;
        this.idUser = idUser;
    }

    public Cajero(String pin, int idUser) {
        this.pin = pin;
        this.idUser = idUser;
    }

    public Integer getIdCajero() {
        return idCajero;
    }

    public void setIdCajero(Integer idCajero) {
        this.idCajero = idCajero;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public Integer getIdUser() {
        return idUser;
    }

    public void setIdUser(Integer idUser) {
        this.idUser = idUser;
    }

}
