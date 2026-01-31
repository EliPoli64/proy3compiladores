import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class AnalizadorSemantico {
    private final NodoArbol raiz;
    private final HashMap<String, ArrayList<Simbolo>> tablaSimbolos;
    private boolean hayErrores = false;
    private String ambitoActual = "Global";
    private String tipoRetornoFuncionActual = null;
    private final Set<String> ambitosFuncion = new HashSet<>(); // Para identificar qué ámbitos son funciones

    public AnalizadorSemantico(NodoArbol raiz, HashMap<String, ArrayList<Simbolo>> tablaSimbolos) {
        this.raiz = raiz;
        this.tablaSimbolos = tablaSimbolos;
        identificarAmbitosFuncion(); // Identificar qué ámbitos son funciones desde el inicio
    }

    public boolean analizar() {
        System.out.println("\nIniciando Análisis Semántico...\n");
        
        if (raiz != null) {
            procesarNodo(raiz);
        }
        
        System.out.println("\nAnálisis Semántico Finalizado.");
        return !hayErrores;
    }

    // Identifica todos los ámbitos que corresponden a funciones
    private void identificarAmbitosFuncion() {
        for (String ambito : tablaSimbolos.keySet()) {
            for (Simbolo simbolo : tablaSimbolos.get(ambito)) {
                if (simbolo.rol.equalsIgnoreCase("Funcion")) {
                    ambitosFuncion.add(ambito); // Este ámbito es una función
                }
            }
        }
    }

    // Verifica si un identificador está dentro de una función (no en Global)
    private boolean estaDentroDeFuncion() {
        return !ambitoActual.equals("Global") && ambitosFuncion.contains(ambitoActual);
    }

    // Busca un identificador respetando las reglas de ámbito
    private String buscarIdentificador(String nombre) {
        // 1. Primero buscar en el ámbito actual (si existe)
        if (tablaSimbolos.containsKey(ambitoActual)) {
            for (Simbolo s : tablaSimbolos.get(ambitoActual)) {
                if (s.nombre.equals(nombre)) {
                    return s.tipo;
                }
            }
        }

        // 2. Si estamos dentro de una función, NO buscamos en Global
        // Las variables globales no son visibles dentro de funciones
        if (estaDentroDeFuncion()) {
            return "error"; // Variable no encontrada en el ámbito de la función
        }

        // 3. Si estamos en Global o en un ámbito que no es función, buscar en Global
        if (!ambitoActual.equals("Global") && tablaSimbolos.containsKey("Global")) {
            for (Simbolo s : tablaSimbolos.get("Global")) {
                if (s.nombre.equals(nombre)) {
                    return s.tipo;
                }
            }
        }

        return "error"; // No se encontró en ningún ámbito permitido
    }
    public boolean hayErrores() {
        return hayErrores;
    }
    // Verifica si un identificador existe en el ámbito actual con reglas de visibilidad
    private boolean existeIdentificador(String nombre) {
        // 1. Buscar en ámbito actual
        if (tablaSimbolos.containsKey(ambitoActual)) {
            for (Simbolo s : tablaSimbolos.get(ambitoActual)) {
                if (s.nombre.equals(nombre)) {
                    return true;
                }
            }
        }

        // 2. Si estamos en una función, NO permitir acceder a variables globales
        if (estaDentroDeFuncion()) {
            return false; // Las funciones solo ven sus propias variables
        }

        // 3. Si no estamos en función, buscar en Global
        if (tablaSimbolos.containsKey("Global")) {
            for (Simbolo s : tablaSimbolos.get("Global")) {
                if (s.nombre.equals(nombre)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void procesarNodo(NodoArbol nodo) {
        if (nodo == null) return;
        
        String tipoNodo = nodo.getTipo().trim();
        
        manejarCambioAmbito(nodo, tipoNodo);
        validarIdentificador(nodo, tipoNodo);
        verificarDeclaracionAsignacion(nodo, tipoNodo);
        verificarAsignacionVariable(nodo, tipoNodo);
        verificarRetornoFuncion(nodo, tipoNodo);
        validarDeclaracionArreglo(nodo, tipoNodo);
        
        for (NodoArbol hijo : nodo.getHijos()) {
            procesarNodo(hijo);
        }
        
        // Restaurar ámbito si salimos de una función
        if ((tipoNodo.equals("funcion") || tipoNodo.equals("funcion_vacia")) && 
            !ambitoActual.equals("Global")) {
            ambitoActual = "Global";
            tipoRetornoFuncionActual = null;
        }
    }

    private void manejarCambioAmbito(NodoArbol nodo, String tipoNodo) {
        if (tipoNodo.equals("navidad")) {
            System.out.println(nodo.getHijos().size());
            if (nodo.getHijos().size() > 2) {
                ambitoActual = "navidad";
                tipoRetornoFuncionActual = obtenerTipoFuncion(ambitoActual);
                validarTipoRetornoFuncion();
            }
        } else if (tipoNodo.equals("funcion")) {
            if (nodo.getHijos().size() > 2) {
                NodoArbol idNode = nodo.getHijos().get(2);
                if (idNode.getTipo().trim().equals("IDENTIFIER")) {
                    ambitoActual = idNode.getLexema().trim();
                    tipoRetornoFuncionActual = obtenerTipoFuncion(ambitoActual);
                    validarTipoRetornoFuncion();
                }
            }
        } else if (tipoNodo.equals("funcion_vacia")) {
            if (nodo.getHijos().size() > 0) {
                NodoArbol idNode = nodo.getHijos().get(0);
                if (idNode.getTipo().trim().equals("IDENTIFIER")) {
                    ambitoActual = idNode.getLexema().trim();
                    tipoRetornoFuncionActual = "void";
                }
            }
        }
    }

    private void validarTipoRetornoFuncion() {
        String[] tiposValidos = {"void", "int", "float", "bool", "char"};
        boolean esValido = false;
        for (String tipo : tiposValidos) {
            if (tipo.equals(tipoRetornoFuncionActual)) {
                esValido = true;
                break;
            }
        }
        if (!esValido) {
            reportarError("El tipo de retorno de la función debe ser INT, FLOAT, BOOL, CHAR o VOID");
        }
    }

    private void validarIdentificador(NodoArbol nodo, String tipoNodo) {
        if (tipoNodo.equals("IDENTIFIER")) {
            String nombre = nodo.getLexema().trim();
            if (!existeIdentificador(nombre)) {
                String mensaje = estaDentroDeFuncion() 
                    ? "Variable '" + nombre + "' no declarada en la función '" + ambitoActual + "'"
                    : "Identificador '" + nombre + "' no declarado en ámbito '" + ambitoActual + "'";
                reportarError(mensaje);
            }
        }
    }

    private void verificarDeclaracionAsignacion(NodoArbol nodo, String tipoNodo) {
        if (!tipoNodo.equals("declaracionVariable_local_asign")) return;
        
        if (nodo.getHijos().size() > 4) {
            String tipoVariable = nodo.getHijos().get(1).getLexema();
            String tipoExpresion = determinarTipo(nodo.getHijos().get(4), ambitoActual);
            verificarTipos(tipoVariable, tipoExpresion, "Declaración con asignación");
        }
    }

    private void verificarAsignacionVariable(NodoArbol nodo, String tipoNodo) {
        if (!tipoNodo.equals("=")) return;
        
        if (nodo.getHijos().size() > 1) {
            String nombreVariable = nodo.getHijos().get(0).getLexema();
            String tipoVariable = buscarIdentificador(nombreVariable);
            String tipoExpresion = determinarTipo(nodo.getHijos().get(1), ambitoActual);
            verificarTipos(tipoVariable, tipoExpresion, "Asignación a variable '" + nombreVariable + "'");
        }
    }

    private void verificarRetornoFuncion(NodoArbol nodo, String tipoNodo) {
        if (!tipoNodo.equals("instruccion_return")) return;
        
        if (nodo.getHijos().size() > 1 && tipoRetornoFuncionActual != null) {
            String tipoRetorno = determinarTipo(nodo.getHijos().get(1), ambitoActual);
            verificarTipos(tipoRetornoFuncionActual, tipoRetorno, "Retorno de función");
        }
    }

    private void validarDeclaracionArreglo(NodoArbol nodo, String tipoNodo) {
        if (!tipoNodo.equals("declaracionArray_local_2d_init")) return;
        
        if (nodo.getHijos().size() > 4) {
            String tipoArreglo = nodo.getHijos().get(1).getTipo();
            if (!tipoArreglo.equals("INT") && !tipoArreglo.equals("CHAR")) {
                reportarError("El tipo del arreglo debe ser INT o CHAR");
            }
        }
    }

    private String determinarTipo(NodoArbol nodo, String ambito) {
        if (nodo == null) return "error";
        
        String tipoNodo = nodo.getTipo().trim();
        String lexema = nodo.getLexema().trim();

        switch (tipoNodo) {
            case "int_literal": return "int";
            case "float_literal": return "float";
            case "bool_literal": return "bool";
            case "string_literal": return "string";
            case "char_literal": return "char";
            case "IDENTIFIER": return buscarIdentificador(lexema);
            case "()": return procesarParentesis(nodo, ambito);
            default: return procesarOperadores(nodo, tipoNodo, ambito);
        }
    }

    private String procesarParentesis(NodoArbol nodo, String ambito) {
        return nodo.getHijos().size() > 1 ? determinarTipo(nodo.getHijos().get(1), ambito) : "error";
    }

    private String procesarOperadores(NodoArbol nodo, String tipoOperador, String ambito) {
        if (esOperadorAritmetico(tipoOperador) || esOperadorRelacional(tipoOperador)) {
            return evaluarOperacion(nodo, tipoOperador, ambito);
        }
        return "desconocido";
    }

    private String evaluarOperacion(NodoArbol nodo, String operador, String ambito) {
        if (nodo.getHijos().size() != 2) return "error";
        
        String tipoIzq = determinarTipo(nodo.getHijos().get(0), ambito);
        String tipoDer = determinarTipo(nodo.getHijos().get(1), ambito);
        
        if (tipoIzq.equals("error") || tipoDer.equals("error")) return "error";
        
        return esOperadorAritmetico(operador) 
            ? evaluarOperacionAritmetica(operador, tipoIzq, tipoDer)
            : evaluarOperacionRelacional(operador, tipoIzq, tipoDer);
    }

    private String evaluarOperacionAritmetica(String operador, String tipoIzq, String tipoDer) {
        if (operador.equals("+") && (tipoIzq.equals("string") || tipoDer.equals("string"))) {
            return "string";
        }
        
        if ((tipoIzq.equals("int") && tipoDer.equals("int")) || 
            (tipoIzq.equals("float") && tipoDer.equals("float"))) {
            return tipoIzq;
        }
        
        reportarError("Incompatibilidad de tipos en operación '" + operador + "': " + tipoIzq + " vs " + tipoDer);
        return "error";
    }

    private String evaluarOperacionRelacional(String operador, String tipoIzq, String tipoDer) {
        if ((tipoIzq.equals("int") && tipoDer.equals("int")) || 
            (tipoIzq.equals("float") && tipoDer.equals("float"))) {
            return "bool";
        }
        
        reportarError("Incompatibilidad de tipos en operación '" + operador + "': " + tipoIzq + " vs " + tipoDer);
        return "error";
    }

    private boolean esOperadorAritmetico(String tipo) {
        return tipo.equals("+") || tipo.equals("-") || tipo.equals("*") || 
               tipo.equals("/") || tipo.equals("%");
    }

    private boolean esOperadorRelacional(String tipo) {
        return tipo.equals(">") || tipo.equals(">=") || tipo.equals("<") || tipo.equals("<=");
    }

    private void verificarTipos(String tipoEsperado, String tipoObtenido, String contexto) {
        if (tipoEsperado.equals("error") || tipoObtenido.equals("error") || tipoObtenido.equals("desconocido")) return;
        
        boolean esCompatible = tipoEsperado.equals(tipoObtenido) || 
                              (tipoEsperado.equals("float") && tipoObtenido.equals("int"));
        
        if (!esCompatible) {
            reportarError(contexto + ": Se esperaba '" + tipoEsperado + "' pero se obtuvo '" + tipoObtenido + "'");
        }
    }

    private String obtenerTipoFuncion(String nombre) {
        for (Simbolo s : tablaSimbolos.getOrDefault("Global", new ArrayList<>())) {
            if (s.nombre.equals(nombre) && s.rol.equalsIgnoreCase("Funcion")) {
                return s.tipo;
            }
        }
        if (nombre.equals("navidad")) {
            return "void";
        }
        reportarError("Función '" + nombre + "' no encontrada en tabla de símbolos");
        return "error";
    }

    private void reportarError(String mensaje) {
        System.err.println("Error Semántico: " + mensaje);
        hayErrores = true;
    }
}