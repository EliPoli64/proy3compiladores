import java.util.ArrayList;

public class NodoArbol {
  String tipo;
  ArrayList<NodoArbol> hijos;
  String lexema;

  public NodoArbol() {
    this.tipo = "";
    this.hijos = new ArrayList<NodoArbol>();
    this.lexema = "";
  }
  public NodoArbol(String lexema) {
    this.tipo = "";
    this.hijos = new ArrayList<NodoArbol>();
    this.lexema = lexema;
  }
  public NodoArbol(String tipo, String lexema) {
    this.tipo = tipo;
    this.lexema = lexema;
    this.hijos = new ArrayList<NodoArbol>();
  }
  public void agregarHijo(NodoArbol hijo) {
    if (hijo != null) {
        this.hijos.add(hijo);
    }
  }
  public String getTipo() {
    return this.tipo;
  }
  public ArrayList<NodoArbol> getHijos() {
    return this.hijos;
  }
  public String getLexema() {
    return this.lexema;
  }
  public void setLexema(String lexema) {
    this.lexema = lexema;
  }
  public void setTipo(String tipo) {
    this.tipo = tipo;
  }

  public String getArbolString() {
    StringBuilder sb = new StringBuilder();
    getArbolStringHelper(sb);
    return sb.toString();
  }

  private void getArbolStringHelper(StringBuilder sb) {
    if (this.hijos.size() == 0) {
      sb.append(this.tipo).append(" ");
    } else {
      sb.append("(").append(this.tipo).append(" ");
      for (NodoArbol hijo : this.hijos) {
        if (hijo != null) {
            hijo.getArbolStringHelper(sb);
        }
      }
      sb.append(") ");
    }
  }

  public String getArbolPrettyString() {
    StringBuilder sb = new StringBuilder();
    getArbolPrettyStringHelper(sb, "", true);
    return sb.toString();
  }

  private void getArbolPrettyStringHelper(StringBuilder sb, String prefix, boolean isLast) {
    String connector = isLast ? "└── " : "├── ";
    sb.append(prefix).append(connector).append(this.tipo);
    if (!this.lexema.isEmpty()) {
      sb.append(" [").append(this.lexema).append("]");
    }
    sb.append("\n");
    
    String newPrefix = prefix + (isLast ? "    " : "│   ");
    
    for (int i = 0; i < this.hijos.size(); i++) {
      NodoArbol hijo = this.hijos.get(i);
      if (hijo != null) {
        boolean isLastChild = (i == this.hijos.size() - 1);
        hijo.getArbolPrettyStringHelper(sb, newPrefix, isLastChild);
      }
    }
  }

}