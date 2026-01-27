
public class Simbolo {
    public String nombre;
    public String tipo;
    public String rol;
    public String ambito;

    public Simbolo(String nombre, String tipo, String rol, String ambito) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.rol = rol;
        this.ambito = ambito;
    }

    @Override
    public String toString() {
        return String.format("%-15s %-15s %-15s %-15s", nombre, tipo, rol, ambito);
    }
}
