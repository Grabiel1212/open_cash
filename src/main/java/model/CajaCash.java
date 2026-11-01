package model;

public class CajaCash {

    private Integer id_caja_cash;
    private Integer id_empleado;
    private String pin;

    public CajaCash(Integer id_caja_cash, Integer id_empleado, String pin) {
        this.id_caja_cash = id_caja_cash;
        this.id_empleado = id_empleado;
        this.pin = pin;
    }

    public CajaCash() {
    }

    public Integer getId_caja_cash() {
        return id_caja_cash;
    }

    public void setId_caja_cash(Integer id_caja_cash) {
        this.id_caja_cash = id_caja_cash;
    }

    public Integer getId_empleado() {
        return id_empleado;
    }

    public void setId_empleado(Integer id_empleado) {
        this.id_empleado = id_empleado;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

}
