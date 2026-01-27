
import java.util.ArrayList;
import java.util.HashMap;

public class AnalizadorSemantico {
    private NodoArbol raiz;
    private HashMap<String, ArrayList<Simbolo>> tablaSimbolos;

    public AnalizadorSemantico(NodoArbol raiz, HashMap<String, ArrayList<Simbolo>> tablaSimbolos) {
        this.raiz = raiz;
        this.tablaSimbolos = tablaSimbolos;
    }

    // Metodo publico que llama el main para iniciar el analisis semantico.
    public void analizar() {
        System.out.println("------------------------------------------------");
        System.out.println("Iniciando Analisis Semantico...");
        
        if (raiz != null) {
            // Inicialmente se toma como ambito el global.
            // Este metodo es el enargado de ir recursivamente 
            // nodo por nodo verificando concordancia semantica.
            verificarNodo(raiz, "Global");
        }
        System.out.println("Analisis Semantico Finalizado.");
        System.out.println("------------------------------------------------");
    }

    private void verificarNodo(NodoArbol nodo, String ambitoActual) {
        if (nodo == null) return;
        
        String tipo = nodo.getTipo().trim();

        // --------------------------- Verificacion del ambito de cada variable ---------------------------
        // Las variables de una funcion solo existen dentro de ella.
        // Por lo tanto, cuando entramos a una funcion, debemos cambiar el ambito actual.
        // El objetivo de esta seccion de codigo es cambiar el ambito actual, dependiendo de la funcion que se visita
        if (tipo.equals("funcion")) {
            if (nodo.getHijos().size() > 2) {
                NodoArbol idNode = nodo.getHijos().get(2);
                if (idNode.getTipo().trim().equals("IDENTIFIER")) {
                    ambitoActual = idNode.getLexema().trim();
                    tipoRetornoFuncionActual = obtenerTipoFuncion(ambitoActual); // Obtenemos el tipo de retorno de la funcion.
                    if (!(tipoRetornoFuncionActual.equals("void") || tipoRetornoFuncionActual.equals("int") || tipoRetornoFuncionActual.equals("float") || tipoRetornoFuncionActual.equals("bool") || tipoRetornoFuncionActual.equals("char"))) {
                        System.out.println("Error: El tipo de retorno de la funcion debe ser INT, FLOAT, BOOL, CHAR o VOID");
                    }
                }
            }
        } else if (tipo.equals("funcion_vacia")) { // Caso de funciones vacias
             if (nodo.getHijos().size() > 0) {
                NodoArbol idNode = nodo.getHijos().get(0);
                if (idNode.getTipo().trim().equals("IDENTIFIER")) {
                    ambitoActual = idNode.getLexema().trim();
                    tipoRetornoFuncionActual = "void"; // Asumimos void si no tiene tipo explicito
                    // Esta variable se utiliza mas abajo para verificar la compatibilidad de tipos en las sentencias return de una funcion.
                }
            }
        }

        // Verificacion de la existencia de cada variable 
        // Aca verificamos que la variable realmente exista en el ambito donde se utiliza (que se haya declarado ahi y no en otra funcion).
        if (tipo.equals("IDENTIFIER")) {
            validarExistencia(nodo.getLexema().trim(), ambitoActual);
        }

        // --------------------------- Aca ya pasamos a la verificacion de la compatibilidad de tipos en asignaciones ---------------------------
        if (tipo.equals("declaracionVariable_local_asign")) {
            //Primero analizamos la declaracion con asignacion.
             if (nodo.getHijos().size() > 4) {
                // El hijo en la posicion 1 del nodo es el tipo (int, float, bool, string, char) de la variable.
                 String tipoVariable = nodo.getHijos().get(1).getLexema(); 
                 //El hijo en la posicion 4 del nodo es la expresion.
                 // Esta expresion puede ser un literal, una variable, una operacion o una funcion. 
                 // Pero esto lo manejamos dentro del metodo obtenerTipo.
                 String tipoExpresion = obtenerTipo(nodo.getHijos().get(4), ambitoActual); 
                 // Finalmente verificamos la compatibilidad de tipos.
                 verificarCompatibilidad(tipoVariable, tipoExpresion, "Asignacion en declaracion");
             }
        }

        if (tipo.equals("=")) {
            // Aca manejamos la asignacion a una variable que ya existe.
            if (nodo.getHijos().size() > 1) {
                String nombreVariable = nodo.getHijos().get(0).getLexema();
                // obtenerTipoIdentificador se encarga de buscar el tipo de la variable en la tabla de simbolos.
                String tipoVariable = obtenerTipoIdentificador(nombreVariable, ambitoActual);
                // Luego obtenemos el tipo de la expresion (el hijo derecho del operador =).
                String tipoExpresion = obtenerTipo(nodo.getHijos().get(1), ambitoActual);
                // Finalmente verificamos la compatibilidad de tipos.
                verificarCompatibilidad(tipoVariable, tipoExpresion, "Asignacion a variable '" + nombreVariable + "'");
            }
        }

        // --------------------------- Verificacion de retorno de funcion ---------------------------
        if (tipo.equals("instruccion_return")) {
            // Estructura: RETURN expresion ENDL
            // El hijo 1 es la expresion
            if (nodo.getHijos().size() > 1 && tipoRetornoFuncionActual != null) {
                String tipoRetorno = obtenerTipo(nodo.getHijos().get(1), ambitoActual);
                // Aca se verifica la compatibilidad de tipos.
                verificarCompatibilidad(tipoRetornoFuncionActual, tipoRetorno, "Retorno de funcion '" + ambitoActual + "'");
            }
        }

        // --------------------------- Verificacion de asignacion INT o CHAR de arreglos ---------------------------
        if (tipo.equals("declaracionArray_local_2d_init")) {
            if (nodo.getHijos().size() > 4) {
                if (!(nodo.getHijos().get(1).getTipo().equals("INT") || nodo.getHijos().get(1).getTipo().equals("CHAR"))) {
                    System.out.println("Error: El tipo del arreglo debe ser INT o CHAR");
                }
            }
        }

        // Recorremos los hijos del nodo actual para continuar con el analisis semantico.
        // Esto es lo que permite que el analisis semantico sea recursivo.
        for (NodoArbol hijo : nodo.getHijos()) {
            verificarNodo(hijo, ambitoActual);
        }
    }

    private String obtenerTipo(NodoArbol nodo, String ambito) {
        if (nodo == null) return "error";
        String tipoNodo = nodo.getTipo().trim();
        String lexema = nodo.getLexema().trim();

        // Aca se manejan los tipos de los literales. 
        // Si el nodo es un literal, se devuelve su tipo correspondiente.
        if (tipoNodo.equals("int_literal")) return "int";
        if (tipoNodo.equals("float_literal")) return "float";
        if (tipoNodo.equals("bool_literal")) return "bool";
        if (tipoNodo.equals("string_literal")) return "string";
        if (tipoNodo.equals("char_literal")) return "char";
        
        // Si es un identificador entonces debemos buscar su tipo en la tabla de simbolos.
        if (tipoNodo.equals("IDENTIFIER")) {
            return obtenerTipoIdentificador(lexema, ambito);
        }

        // Si es un parentesis, entonces debemos buscar el tipo de la expresion que esta dentro del parentesis.
        if (tipoNodo.equals("()")) {
            if (nodo.getHijos().size() > 1) {
                return obtenerTipo(nodo.getHijos().get(1), ambito);
            }
        }

        // Si es un operador aritmetico, entonces debemos verificar la compatibilidad de tipos de los operandos.
        // Luego de verificar la compatibilidad de tipos, se le asigna un tipo a la expresion.
        if (tipoNodo.equals("+") || tipoNodo.equals("-") || tipoNodo.equals("*") || tipoNodo.equals("/") || tipoNodo.equals("%") || typeIsArithmetic(tipoNodo)) {
            if (nodo.getHijos().size() == 2) {
                String t1 = obtenerTipo(nodo.getHijos().get(0), ambito);
                String t2 = obtenerTipo(nodo.getHijos().get(1), ambito);
                
                if (t1.equals("error") || t2.equals("error")) return "error";

                if (t1.equals("int") && t2.equals("int")) return "int";
                if (t1.equals("float") && t2.equals("float")) return "float";
                if ((t1.equals("int") && t2.equals("float")) || (t1.equals("float") && t2.equals("int"))) {
                    System.err.println("Error Semantico: incompatibilidad de tipos en operacion '" + tipoNodo + "': " + t1 + " vs " + t2); 
                    return "error";
                }
                
                if (tipoNodo.equals("+")) {
                     if (t1.equals("string") || t2.equals("string")) return "string";
                }

                System.err.println("Error Semantico: Incompatibilidad de tipos en operacion '" + tipoNodo + "': " + t1 + " vs " + t2);
                return "error";
            }
        }

        // Si es un operador relacional, entonces debemos verificar la compatibilidad de tipos de los operandos.
        //Luego de verificar la compatibilidad tipos, se le asigna un tipo a la expresion.
        if (tipoNodo.equals(">") || tipoNodo.equals(">=") || tipoNodo.equals("<") || tipoNodo.equals("<=") || typeIsArithmetic(tipoNodo)) {
            if (nodo.getHijos().size() == 2) {
                String t1 = obtenerTipo(nodo.getHijos().get(0), ambito);
                String t2 = obtenerTipo(nodo.getHijos().get(1), ambito);
                
                if (t1.equals("error") || t2.equals("error")) return "error";

                if (t1.equals("int") && t2.equals("int")) return "bool";
                if (t1.equals("float") && t2.equals("float")) return "bool";

                System.err.println("Error Semantico: Incompatibilidad de tipos en operacion '" + tipoNodo + "': " + t1 + " vs " + t2);
                return "error";
            }
        }
        
        return "desconocido";
    }

    private boolean typeIsArithmetic(String tipo) {
         return false;
    }

    // Aca se busca el tipo de un identificador en la tabla de simbolos.
    // Primero se busca la variable en el ambito actual y si no se encuentra, se busca en el ambito global.
    private String obtenerTipoIdentificador(String nombre, String ambito) {
        if (tablaSimbolos.containsKey(ambito)) {
            for (Simbolo s : tablaSimbolos.get(ambito)) {
                if (s.nombre.equals(nombre)) return s.tipo;
            }
        }
        if (!ambito.equals("Global") && tablaSimbolos.containsKey("Global")) {
            for (Simbolo s : tablaSimbolos.get("Global")) {
                if (s.nombre.equals(nombre)) return s.tipo;
            }
        }
        return "error"; 
    }

    // Aca se verifica la compatibilidad de tipos.
    private void verificarCompatibilidad(String tipoVar, String tipoExpr, String contexto) {
        if (tipoVar.equals("error") || tipoExpr.equals("error") || tipoExpr.equals("desconocido")) return;

        boolean compatible = false;
        // Aca se manejan distintos casos de compatibilidad de tipos.
        // Si los tipos son iguales, entonces son compatibles.
        if (tipoVar.equals(tipoExpr)) compatible = true;
        // Si el tipo de la variable es float y el tipo de la expresion es int, entonces son compatibles.
        else if (tipoVar.equals("float") && tipoExpr.equals("int")) compatible = true; 
        // Si el tipo de la variable es string, entonces es compatible con cualquier tipo (hay que revisar esto).
        else if (tipoVar.equals("string")) compatible = true; 
        
        // Si el tipo de la variable es string y el tipo de la expresion no es string, entonces no son compatibles.
        if (tipoVar.equals("string") && !tipoExpr.equals("string")) compatible = false;

        if (!compatible) {
            System.err.println("Error Semantico: " + contexto + ". Se esperaba '" + tipoVar + "' pero se obtuvo '" + tipoExpr + "'.");
        }
    }

    // Aca se valida la existencia de un identificador dentro de un ambito.
    // En palabras basicas lo que se verifica es que el identificador haya sido declarado en el ambito en el que se vaya a usar.
    // Si no se encuentra en el ambito actual, se busca en el ambito global.
    private void validarExistencia(String nombre, String ambito) {
        boolean existe = false;

        // Se busca el identificador en el ambito actual.
        if (tablaSimbolos.containsKey(ambito)) {
            for (Simbolo s : tablaSimbolos.get(ambito)) {
                if (s.nombre.equals(nombre)) {
                    existe = true;
                    break;
                }
            }
        }

        // Si no se encuentra en el ambito actual, se busca en el ambito global.
        if (!existe && !ambito.equals("Global")) {
            if (tablaSimbolos.containsKey("Global")) {
                for (Simbolo s : tablaSimbolos.get("Global")) {
                    if (s.nombre.equals(nombre)) {
                        existe = true;
                        break;
                    }
                }
            }
        }

        if (!existe) {
            System.err.println("Error Semantico: El identificador '" + nombre + "' no esta declarado en el ambito '" + ambito + "' ni paso global.");
        }
    }

    private String tipoRetornoFuncionActual;
    // Buscamos el tipado de la funcion en la tabla de simbolos.
    private String obtenerTipoFuncion(String nombre) {
        if (tablaSimbolos.containsKey("Global")) {
            for (Simbolo s : tablaSimbolos.get("Global")) {
                if (s.nombre.equals(nombre) && s.rol.equalsIgnoreCase("Funcion")) {
                    return s.tipo;
                }
            }
        }
        return "error";
    }
}
