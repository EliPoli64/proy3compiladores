import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

public class GeneradorCodigoIntermedio {
    private StringBuilder codigoIntermedio;
    private StringBuilder codigoData;
    private int tempCounter;
    private int labelCounter;
    private int stringCounter;
    private Stack<String> breakLabels;
    private HashMap<String, String> variables;
    private HashMap<String, String> stringConstants;
    private boolean navidadEncontrado = false;
    private int tempCount = 0;

    private String nuevoTemporal() {
        return "t" + (tempCount++);
    }
    
    public GeneradorCodigoIntermedio() {
        this.codigoIntermedio = new StringBuilder();
        this.codigoData = new StringBuilder();
        this.tempCounter = 1;
        this.labelCounter = 1;
        this.stringCounter = 1;
        this.breakLabels = new Stack<>();
        this.variables = new HashMap<>();
        this.stringConstants = new HashMap<>();
    }

    public String generar(NodoArbol raiz) {
        // Procesar el árbol
        visitar(raiz);
        
        // Agregar constantes de string al principio si existen
        if (codigoData.length() > 0) {
            return codigoData.toString() + "\n" + codigoIntermedio.toString();
        }
        
        return codigoIntermedio.toString();
    }

    private String nuevoTemp() {
        return "t" + tempCounter++;
    }

    private String nuevaEtiqueta() {
        return "L" + labelCounter++;
    }

    private String registrarString(String str) {
        // Remover comillas si las tiene
        if (str.startsWith("\"") && str.endsWith("\"")) {
            str = str.substring(1, str.length() - 1);
        }
        
        // Reemplazar secuencias de escape
        str = str.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"");
        if (stringConstants.containsValue(str)) {
            // Buscar el label existente
            for (String key : stringConstants.keySet()) {
                if (stringConstants.get(key).equals(str)) {
                    return key; // Retornar el label existente
                }
            }
        }
        String label = "str_" + stringCounter;  // Prefijo 'str_' seguido del número
        stringConstants.put(label, str);        // Guardar con el label correcto
        codigoData.append("").append(label).append(" = \"").append(str).append("\"\n");
        
        // Incrementar el contador después de usarlo
        stringCounter++;
        
        return label;  // Retornar el label que se usará en el código intermedio
    }

    private void procesarGlobales(NodoArbol nodo) {
        if (!nodo.getTipo().equals("globales") && !nodo.getTipo().equals("program")) {
            return;
        }

        for (NodoArbol global : nodo.getHijos()) {

            List<NodoArbol> hijos = global.getHijos();

            if (hijos.size() < 4) {
                continue;
            }

            if (!hijos.get(0).getTipo().equals("globales")) {
                return;
            }

            if (hijos.size() == 6 || hijos.size() == 7) {
                variables.put(hijos.get(hijos.size() - 4).getLexema(), hijos.get(hijos.size() - 2).getLexema());
                String valorInicial = hijos.get(hijos.size() - 2).getLexema();
                String tipo = hijos.get(hijos.size() - 5).getLexema();
                String identificador = hijos.get(hijos.size() - 4).getLexema();

                codigoIntermedio.append("GLOBAL ")
                                .append(identificador)
                                .append(" : ")
                                .append(tipo)
                                .append(" = ")
                                .append(valorInicial)
                                .append("\n");
            } else {
                variables.put(hijos.get(hijos.size() - 2).getLexema(), hijos.get(hijos.size() - 3).getLexema());
                String tipo = hijos.get(hijos.size() - 3).getLexema();
                String identificador = hijos.get(hijos.size() - 2).getLexema();
                codigoIntermedio.append("GLOBAL ")
                                .append(identificador)
                                .append(" : ")
                                .append(tipo)
                                .append("\n");
            }
        }
    }
    private void procesarListaArgumentos(NodoArbol nodo, List<String> argumentos) {
        // Si la lista de argumentos está vacía, no hacer nada
        if (nodo.getHijos().isEmpty()) {
            return;
        }
        
        // Caso: listaArgumentos -> listaArgumentos , expresión
        // O caso: listaArgumentos -> expresión
        
        // Primero buscar si hay sublista
        NodoArbol sublista = null;
        NodoArbol expresion = null;
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("listaArgumentos")) {
                sublista = hijo;
            } else if (!hijo.getTipo().equals("COMMA") && 
                    !hijo.getTipo().equals("LPAREN") && 
                    !hijo.getTipo().equals("RPAREN")) {
                // Es una expresión (int_literal, char_literal, IDENTIFIER, etc.)
                expresion = hijo;
            }
        }
        
        // Procesar recursivamente la sublista primero (para mantener orden correcto)
        if (sublista != null) {
            procesarListaArgumentos(sublista, argumentos);
        }
        
        // Luego procesar la expresión actual
        if (expresion != null) {
            String valor = visitar(expresion);
            if (!valor.isEmpty()) {
                argumentos.add(valor);
            }
        }
    }
    private String procesarLlamadaFuncion(NodoArbol nodo) {
        String nombreFuncion = "";
        List<String> argumentos = new ArrayList<>();
        
        // Recorrer hijos para obtener nombre y argumentos
        for (NodoArbol hijo : nodo.getHijos()) {
            switch (hijo.getTipo()) {
                case "IDENTIFIER":
                    nombreFuncion = hijo.getLexema();
                    break;
                    
                case "listaArgumentos":
                    procesarListaArgumentos(hijo, argumentos);
                    break;
                    
                // Ignorar tokens de paréntesis
                case "LPAREN":
                case "RPAREN":
                    break;
            }
        }
        
        if (!nombreFuncion.isEmpty()) {
            // Generar código para pasar parámetros
            if (!argumentos.isEmpty()) {
                codigoIntermedio.append("   ");
                for (int i = 0; i < argumentos.size(); i++) {
                    String arg = argumentos.get(i);
                    codigoIntermedio.append("PARAM ").append(arg);
                    if (i < argumentos.size() - 1) {
                        codigoIntermedio.append(", ");
                    }
                }
                codigoIntermedio.append("\n");
            }
            
            // Generar llamada a la función
            codigoIntermedio.append("   CALL ").append(nombreFuncion).append("\n");
            
            // Si la función retorna un valor, guardarlo en un temporal
            String tempRetorno = nuevoTemporal();
            codigoIntermedio.append("   ").append(tempRetorno).append(" = RET\n");
            
            return tempRetorno;
        }
        
        return "";
    }

    private String visitar(NodoArbol nodo) {
        if (nodo == null) return "";
        
        String tipo = nodo.getTipo();
        String lexema = nodo.getLexema();
        
        procesarGlobales(nodo);
        
        switch (tipo) {
            case "program":
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";
            case "function_call":
                return procesarLlamadaFuncion(nodo);
            
            case "funciones":
                if (!navidadEncontrado) {
                    codigoIntermedio.append("GOTO navidad\n");
                    navidadEncontrado = true;
                }
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                
                return "";
                
            case "funcion":
                return procesarFuncion(nodo);
                
            case "navidad":
                navidadEncontrado = true;
                procesarNavidad(nodo);
                return "";
                
            case "bloque":
                for (NodoArbol hijo : nodo.getHijos()) {
                    if (hijo.getTipo().equals("LBRACKET") || hijo.getTipo().equals("RBRACKET")) {
                        continue;
                    }
                    visitar(hijo);
                }
                return "";
                
            case "listaInstr":
                for (NodoArbol hijo : nodo.getHijos()) {
                    if (!hijo.getTipo().equals("listaInstr_vacia")) {
                        visitar(hijo);
                    }
                }
                return "";
                
            case "listaInstr_vacia":
                return "";
                
            case "instruccion":
                if (nodo.getHijos().size() > 0) {
                    return visitar(nodo.getHijos().get(0));
                }
                return "";
                
            // INSTRUCCIONES
            case "instruccion_show":
                return procesarShow(nodo);
                
            case "instruccion_return":
                return procesarReturn(nodo);
                
            case "instruccion_break":
                return procesarBreak();
                
            case "instruccion_read":
                return procesarRead(nodo);
                
            case "get_statement":
                // El manejo real está en procesarRead
                return "";
                
            // DECLARACIONES
            case "declaracionVariable_local":
                return procesarDeclaracionLocal(nodo, false);
                
            case "declaracionVariable_local_asign":
                return procesarDeclaracionLocal(nodo, true);
                
            case "declaracionArray_local_init":
                return procesarDeclaracionArray(nodo);
                
            // ASIGNACIONES
            case "=":
                return procesarAsignacionSimple(nodo);
                
            case "array_assign":
                return procesarAsignacionArray(nodo);
                
            // ESTRUCTURAS DE CONTROL
            case "decide":
                return procesarDecide(nodo);
                
            case "decide_with_else":
                return procesarDecideConElse(nodo);
                
            case "loop":
                return procesarLoop(nodo);
                
            case "for_stmt":
                return procesarFor(nodo);
                
            // EXPRESIONES Y OPERADORES
            case "+":
                return generarOperacionBinaria(nodo, "+");
                
            case "-":
                return generarOperacionBinaria(nodo, "-");
                
            case "*":
                return generarOperacionBinaria(nodo, "*");
                
            case "/":
                return generarOperacionBinaria(nodo, "/");
                
            case "%":
                return generarOperacionBinaria(nodo, "%");
                
            case "^":
                return generarOperacionBinaria(nodo, "**");
                
            // OPERADORES RELACIONALES
            case "==":
                return generarOperacionBinaria(nodo, "==");
                
            case "!=":
                return generarOperacionBinaria(nodo, "!=");
                
            case "<":
                return generarOperacionBinaria(nodo, "<");
                
            case "<=":
                return generarOperacionBinaria(nodo, "<=");
                
            case ">":
                return generarOperacionBinaria(nodo, ">");
                
            case ">=":
                return generarOperacionBinaria(nodo, ">=");
                
            // OPERADORES LOGICOS
            case "@":
                return generarOperacionBinaria(nodo, "&&");
                
            case "~":
                return generarOperacionBinaria(nodo, "||");
                
            case "Σ": // NOT
                return generarOperacionNot(nodo);
                
            // OPERADORES UNARIOS
            case "MINUS":
                return generarOperacionUnaria(nodo, "-");
                
            case "++_pre":
                return generarIncrementoPre(nodo);
                
            case "--_pre":
                return generarDecrementoPre(nodo);
                
            // LITERALES
            case "int_literal":
                return lexema;
                
            case "float_literal":
                return lexema;
                
            case "bool_literal":
                return lexema.equals("true") ? "true" : "false";
                
            case "char_literal":
                return lexema;
                
            case "IDENTIFIER":
                return lexema;
            case "CALL":
                return "";
                
            // ACCESO A ARRAY
            case "array_access":
                return procesarAccesoArray(nodo);
                
            // TIPOS
            case "INT":
            case "FLOAT":
            case "BOOL":
            case "CHAR":
            case "STRING":
                return lexema.toLowerCase();
                
            // CONDICIONES PARA DECIDE
            case "condicionesDecide":
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";
            case "GET":
                procesarGet(nodo);
                return "";
                
            case "condicionDecide":
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return procesarCondicionDecide(nodo);
                
            // PARÁMETROS Y LLAMADAS
            case "listaParametros":
                // Se procesa dentro de la función
                return "";
                
            case "parametros":
                // Se procesa dentro de la función
                return "";
                
            case "parametros_vacio":
                return "";
                
            // ARRAY DIMENSIONS
            case "arrayDimensions":
                // Se procesa en declaración de array
                return "";
                
            case "arrayInit":
            case "arrayInitList":
            case "arrayValues":
                // Se procesa en inicialización de array
                return "";
                
            // IGNORAR TOKENS
            case "WORLD":
            case "LOCAL":
            case "ENDL":
            case "LPAREN":
            case "RPAREN":
            case "LBRACKET":
            case "RBRACKET":
            case "DECLBRACKETL":
            case "DECLBRACKETR":
            case "COAL":
            case "NAVIDAD":
            case "GIFT":
            case "RETURN":
            case "SHOW":
            case "DECIDE":
            case "OF":
            case "END":
            case "LOOP":
            case "WHEN":
            case "FOR":
            case "BREAK":
            case "ARROW":
            case "COMMA":
            case "ASSIGN":
            case "ELSE":
                return "";
            
            default:
                // Para cualquier otro nodo, procesar hijos
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";
        }
    }
    private String procesarGet(NodoArbol nodo) {
        String variable = "";
        
        // Buscar identificador a leer (dentro de paréntesis)
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("IDENTIFIER")) {
                variable = hijo.getLexema();
                break;
            } else if (hijo.getTipo().equals("()")) {
                // Buscar dentro de los paréntesis
                for (NodoArbol hijoParen : hijo.getHijos()) {
                    if (hijoParen.getTipo().equals("IDENTIFIER")) {
                        variable = hijoParen.getLexema();
                        break;
                    }
                }
            }
        }
        
        if (!variable.isEmpty()) {
            // Determinar el tipo de la variable para saber qué tipo de READ hacer
            String tipoVariable = variables.get(variable);
            
            if (tipoVariable != null) {
                switch (tipoVariable.toLowerCase()) {
                    case "int":
                        codigoIntermedio.append("   ").append("READINT ").append(variable).append("\n");
                        break;
                    case "float":
                        codigoIntermedio.append("   ").append("READFLOAT ").append(variable).append("\n");
                        break;
                    case "char":
                        codigoIntermedio.append("   ").append("READCHAR ").append(variable).append("\n");
                        break;
                    case "string":
                        codigoIntermedio.append("   ").append("READSTRING ").append(variable).append("\n");
                        break;
                    case "bool":
                        codigoIntermedio.append("   ").append("READBOOL ").append(variable).append("\n");
                        break;
                    default:
                        // Por defecto asumimos int
                        codigoIntermedio.append("   ").append("READ ").append(variable).append("\n");
                        break;
                }
            } else {
                // Si no conocemos el tipo, usar READ genérico
                codigoIntermedio.append("   ").append("READ ").append(variable).append("\n");
            }
        }
        
        return variable;
    }
    
    private void procesarNavidad(NodoArbol nodo) {
        
        codigoIntermedio.append("   ").append("\n# FUNCION PRINCIPAL (navidad)\n");
        codigoIntermedio.append("navidad:\n");
        
        // Procesar las instrucciones dentro de navidad
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("listaInstr")) {
                visitar(hijo);
            } else if (hijo.getTipo().equals("instruccion_return")) {
                // Manejar return específico de navidad
                procesarReturn(hijo);
            }
        }
        
        // Si no hay return explícito, agregar uno
        codigoIntermedio.append("   ").append("RETURN NAVIDAD\n");
        
    }
    private String generarOperacionBinariaDesdeArbol(NodoArbol nodo) {
        // En el árbol, los operandos pueden estar en diferentes posiciones
        String operador = obtenerOperador(nodo.getTipo());
        String izquierda = "";
        String derecha = "";
        if (nodo.getTipo().equals("*")){
            System.out.println("Generando operacion binaria * desde arbol");
        }
        // Buscar operandos en los hijos
        for (NodoArbol hijo : nodo.getHijos()) {
            if (izquierda.isEmpty()) {
                izquierda = evaluarExpr(hijo);
            } else if (!hijo.getTipo().equals("ENDL") && !hijo.getTipo().equals("ASSIGN")) {
                derecha = evaluarExpr(hijo);
            }
        }
        
        if (!izquierda.isEmpty() && !derecha.isEmpty()) {
            String temp = nuevoTemporal();
            codigoIntermedio.append("   ").append(temp).append(" = ").append(izquierda)
                          .append(" ").append(operador).append(" ").append(derecha).append("\n");
            return temp;
        }
        
        return "";
    }
    private String generarOperacionNotDesdeArbol(NodoArbol nodo) {
        // Buscar el operando
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("()") || 
                hijo.getTipo().equals("IDENTIFIER") ||
                hijo.getTipo().equals("bool_literal") ||
                hijo.getTipo().equals("==") || hijo.getTipo().equals("!=") ||
                hijo.getTipo().equals("<") || hijo.getTipo().equals("<=") ||
                hijo.getTipo().equals(">") || hijo.getTipo().equals(">=")) {
                
                String operando = evaluarExpr(hijo);
                if (!operando.isEmpty()) {
                    String temp = nuevoTemporal();
                    codigoIntermedio.append("   ").append(temp).append(" = NOT ").append(operando).append("\n");
                    return temp;
                }
            }
        }
        return "";
    }
    private String obtenerOperador(String tipo) {
        switch (tipo) {
            case "+": return "+";
            case "-": return "-";
            case "*": return "*";
            case "/": return "/";
            case "%": return "%";
            case "^": return "**";
            case "==": return "==";
            case "!=": return "!=";
            case "<": return "<";
            case "<=": return "<=";
            case ">": return ">";
            case ">=": return ">=";
            case "@": return "&&";
            case "~": return "||";
            default: return "";
        }
    }
    
    private String procesarFuncion(NodoArbol nodo) {
        // Obtener información de la función
        String tipoRetorno = "";
        String nombreFunc = "";
        
        for (NodoArbol hijo : nodo.getHijos()) {
            switch (hijo.getTipo()) {
                case "FLOAT":
                case "INT":
                case "BOOL":
                case "CHAR":
                case "STRING":
                    tipoRetorno = hijo.getLexema().toLowerCase();
                    break;
                case "IDENTIFIER":
                    nombreFunc = hijo.getLexema();
                    break;
            }
        }
        
        if (nombreFunc.isEmpty()) return "";
        
        codigoIntermedio.append("\n# FUNCION ").append(nombreFunc).append(" -> ").append(tipoRetorno).append("\n");
        codigoIntermedio.append(nombreFunc).append(":\n");
        
        // Procesar parámetros si existen
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("parametros") || hijo.getTipo().equals("listaParametros")) {
                procesarParametros(hijo);
            }
        }
        
        // Procesar cuerpo de la función
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("bloque")) {
                visitar(hijo);
                break;
            } 
        }
        
        // Solo agregar return si no hay uno explícito en el cuerpo
        // NO agregar GOTO aquí
        boolean tieneReturn = false;
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("bloque")) {
                tieneReturn = buscarReturnEnBloque(hijo);
                break;
            }
        }
        
        if (!tieneReturn) {
            if (tipoRetorno.equals("float") || tipoRetorno.equals("int")) {
                codigoIntermedio.append("   RETURN 0\n");
            } else if (tipoRetorno.equals("bool")) {
                codigoIntermedio.append("   RETURN false\n");
            } else {
                codigoIntermedio.append("   RETURN\n");
            }
        }
        return nombreFunc;
    }

    private boolean buscarReturnEnBloque(NodoArbol nodo) {
        if (nodo == null) return false;
        
        if (nodo.getTipo().equals("instruccion_return")) {
            return true;
        }
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (buscarReturnEnBloque(hijo)) {
                return true;
            }
        }
        
        return false;
    }
    
    private void procesarParametros(NodoArbol nodo) {
        // Procesar lista de parámetros
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("listaParametros")) {
                procesarListaParametros(hijo);
            }
        }
    }
    
    private void procesarListaParametros(NodoArbol nodo) {
        // Recorrer parámetros: tipo IDENTIFIER
        String tipoActual = "";
        for (NodoArbol hijo : nodo.getHijos()) {
            switch (hijo.getTipo()) {
                case "INT":
                case "FLOAT":
                case "BOOL":
                case "CHAR":
                case "STRING":
                    tipoActual = hijo.getLexema().toLowerCase();
                    break;
                case "IDENTIFIER":
                    if (!tipoActual.isEmpty()) {
                        variables.put(hijo.getLexema(), tipoActual);
                        codigoIntermedio.append("   PARAM ").append(hijo.getLexema())
                                      .append(": ").append(tipoActual).append("\n");
                        tipoActual = "";
                    }
                    break;
                case "listaParametros":
                    procesarListaParametros(hijo);
                    break;
            }
        }
    }
    
    private String procesarDeclaracionLocal(NodoArbol nodo, boolean conAsignacion) {
        String tipo = "";
        String nombre = "";
        String valor = null;
        
        for (NodoArbol hijo : nodo.getHijos()) {
            switch (hijo.getTipo()) {
                case "INT":
                case "FLOAT":
                case "BOOL":
                case "CHAR":
                case "STRING":
                    tipo = hijo.getLexema().toLowerCase();
                    break;
                case "IDENTIFIER":
                    nombre = hijo.getLexema();
                    break;
                case "int_literal":
                case "float_literal":
                case "bool_literal":
                    valor = hijo.getLexema();
                    break;
                case "char_literal":
                    valor = "'" + hijo.getLexema() + "'";
                    break;
                case "string_literal":
                    valor = registrarString(hijo.getLexema());
                    break;
            }
        }
        
        if (!nombre.isEmpty()) {
            // Guardar tipo en mapa
            variables.put(nombre, tipo);
            
            if (conAsignacion && valor != null) {
                if (tipo.equals("float")) {
                    String temp = nuevoTemporal() + "_f";
                    codigoIntermedio.append("   ").append(temp).append(" = ").append(valor).append("\n");
                    codigoIntermedio.append("   ").append(nombre).append(" = ").append(temp).append("\n");
                } else {
                    String temp = nuevoTemporal();
                    codigoIntermedio.append("   ").append(temp).append(" = ").append(valor).append("\n");
                    codigoIntermedio.append("   ").append(nombre).append(" = ").append(temp).append("\n");
                }
            } else {
                codigoIntermedio.append("   ").append("LOCAL ").append(nombre)
                            .append(" : ").append(tipo).append("\n");
            }
        }
        
        return nombre;
    }
    
    private String procesarDeclaracionArray(NodoArbol nodo) {

        List<NodoArbol> hijos = nodo.getHijos();

        String tipo = hijos.get(1).getLexema();
        String nombreArray = hijos.get(2).getLexema();
        String tamaño2 = hijos.get(3).getHijos().get(2).getLexema();
        String tamaño1 = hijos.get(3).getHijos().get(0).getHijos().get(1).getLexema();

        codigoIntermedio.append("   ").append("ARRAY ")
                    .append(nombreArray)
                    .append(" : ")
                    .append(tipo)
                    .append("[")
                    .append(tamaño1)
                    .append("]")
                    .append("[")
                    .append(tamaño2)
                    .append("]")
                    .append("\n");

        if (hijos.size() > 6) {
            NodoArbol listaValores = hijos.get(6);

            int indice = 0;
            for (NodoArbol valor : listaValores.getHijos()) {
                codigoIntermedio.append("   ").append(nombreArray)
                            .append("[")
                            .append(indice)
                            .append("] = ")
                            .append(valor.getLexema())
                            .append("\n");
                indice++;
            }
        }
        return nombreArray;
    }

    
    private String procesarShow(NodoArbol nodo) {
        String expresion = "";
        String tipoExpresion = "";
        
        // Buscar la expresión a mostrar
        for (NodoArbol hijo : nodo.getHijos()) {
            if (!hijo.getTipo().equals("SHOW") && 
                !hijo.getTipo().equals("ENDL") &&
                !hijo.getTipo().equals("LPAREN") &&
                !hijo.getTipo().equals("RPAREN")) {
                
                expresion = evaluarExpr(hijo);
                tipoExpresion = determinarTipoExpresion(hijo);
                break;
            } 
        }
        
        if (!expresion.isEmpty()) {
            if (tipoExpresion.equals("string") || expresion.startsWith("str_")) {
                // Si ya es un label de string (empieza con str_)
                codigoIntermedio.append("   ").append("PRINTSTRING ").append(expresion).append("\n");
            } else if (tipoExpresion.equals("float")) {
                codigoIntermedio.append("   ").append("PRINTFLOAT ").append(expresion).append("\n");
            } else {
                codigoIntermedio.append("   ").append("PRINT ").append(expresion).append("\n");
            }
        }
        
        return expresion;
    }
    private String determinarTipoExpresion(NodoArbol nodo) {
        String tipo = nodo.getTipo();
        
        switch (tipo) {
            case "string_literal":
                return "string";
                
            case "char_literal":
                return "char";
                
            case "float_literal":
                return "float";
                
            case "int_literal":
            case "bool_literal":
                return "int";
                
            case "IDENTIFIER":
                // Si tenemos información de tipos de variables, la usamos
                String tipoVariable = variables.get(nodo.getLexema());
                if (tipoVariable != null) {
                    return tipoVariable;
                }
                // Si el identificador empieza con str_ es una constante string
                if (nodo.getLexema().startsWith("str_")) {
                    return "string";
                }
                return "int";
                
            default:
                // Para otros casos, buscar recursivamente
                for (NodoArbol hijo : nodo.getHijos()) {
                    String tipoHijo = determinarTipoExpresion(hijo);
                    if (!tipoHijo.isEmpty() && !tipoHijo.equals("int")) {
                        return tipoHijo;
                    }
                }
                return "int";
        }
    }

    private String procesarReturn(NodoArbol nodo) {
        String valor = "";
        
        // Buscar valor de retorno si existe
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("int_literal") ||
                hijo.getTipo().equals("float_literal") ||
                hijo.getTipo().equals("bool_literal") ||
                hijo.getTipo().equals("char_literal") ||
                hijo.getTipo().equals("IDENTIFIER") ||
                hijo.getTipo().equals("()")) {
                
                valor = visitar(hijo);
                break;
            }
        }
        
        if (!valor.isEmpty()) {
            codigoIntermedio.append("   ").append("RETURN ").append(valor).append("\n");
        } else {
            codigoIntermedio.append("   ").append("RETURN\n");
        }
        
        return valor;
    }
    
    private String procesarBreak() {
        if (!breakLabels.isEmpty()) {
            String label = breakLabels.peek();
            codigoIntermedio.append("   ").append("GOTO ").append(label).append("\n");
        }
        return "";
    }
    
    private String procesarRead(NodoArbol nodo) {
        String variable = "";
        
        // Buscar identificador a leer
        for (NodoArbol hijo : nodo.getHijos().get(0).getHijos()) {
            if (hijo.getTipo().equals("IDENTIFIER")) {
                variable = hijo.getLexema();
                break;
            }
        }
        
        if (!variable.isEmpty()) {
            codigoIntermedio.append("   ").append("READ ").append(variable).append("\n");
        }
        
        return variable;
    }
    
    private String procesarAsignacionSimple(NodoArbol nodo) {
        String destino = "";
        String valor = "";
        
        // Buscar destino (izquierda del =)
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("IDENTIFIER")) {
                destino = hijo.getLexema();
                break;
            }
        }
        if (nodo.getHijos().get(1).getTipo().equals("string_literal")) {
            valor = registrarString(nodo.getHijos().get(1).getLexema());
            codigoIntermedio.append("   ").append(destino).append(" = ").append(valor).append("\n");
            return destino;
        }
        if (nodo.getHijos().get(1).getTipo().equals("bool_literal")) {
            valor = nodo.getHijos().get(1).getLexema();
            codigoIntermedio.append("   ").append(destino).append(" = ").append(valor).append("\n");
            return destino;
        }
        
        // Buscar valor (derecha del =)
        // El valor puede ser una expresión compleja
        
        valor = evaluarExpr(nodo.getHijos().get(1));
        
        if (!destino.isEmpty() && !valor.isEmpty()) {
            codigoIntermedio.append("   ").append(destino).append(" = ").append(valor).append("\n");
        }
        
        return destino;
    }
    private void recolectarArgumentos(NodoArbol nodo, List<NodoArbol> args) {
        if (nodo == null) return;

        if (nodo.getTipo().equals("listaArgumentos")) {
            for (NodoArbol hijo : nodo.getHijos()) {
                recolectarArgumentos(hijo, args);
            }
            return;
        }

        if (!nodo.getTipo().equals("COMMA")
            && !nodo.getTipo().equals("LPAREN")
            && !nodo.getTipo().equals("RPAREN")) {
            args.add(nodo);
        }
    }


    private String evaluarExpr(NodoArbol nodo) {
        String tipo = nodo.getTipo();

        switch (tipo) {
            case "int_literal":
            case "float_literal":
            case "bool_literal":
                return nodo.getLexema();

            case "char_literal":
                return "'" + nodo.getLexema() + "'";

            case "string_literal":
                return registrarString(nodo.getLexema());

            case "IDENTIFIER":
                return nodo.getLexema();

            case "MINUS": {
                if (!nodo.getHijos().isEmpty()) {
                    String valor = evaluarExpr(nodo.getHijos().get(0));
                    String temp = nuevoTemporal();
                    codigoIntermedio.append("   ")
                                    .append(temp)
                                    .append(" = -")
                                    .append(valor)
                                    .append("\n");
                    return temp;
                }
                return "";
            }

            case "++_pre": {
                String var = evaluarExpr(nodo.getHijos().get(0));
                String t1 = nuevoTemporal();
                String t2 = nuevoTemporal();
                codigoIntermedio.append("   ").append(t1).append(" = ").append(var).append(" + 1\n");
                codigoIntermedio.append("   ").append(var).append(" = ").append(t1).append("\n");
                codigoIntermedio.append("   ").append(t2).append(" = ").append(var).append("\n");
                return t2;
            }

            case "--_pre": {
                String var = evaluarExpr(nodo.getHijos().get(0));
                String t1 = nuevoTemporal();
                String t2 = nuevoTemporal();
                codigoIntermedio.append("   ").append(t1).append(" = ").append(var).append(" - 1\n");
                codigoIntermedio.append("   ").append(var).append(" = ").append(t1).append("\n");
                codigoIntermedio.append("   ").append(t2).append(" = ").append(var).append("\n");
                return t2;
            }

            case "function_call": {
                String nombreFuncion = "";
                List<NodoArbol> argumentos = new ArrayList<>();

                for (NodoArbol hijo : nodo.getHijos()) {
                    if (hijo.getTipo().equals("IDENTIFIER")) {
                        nombreFuncion = hijo.getLexema();
                    } else if (hijo.getTipo().equals("listaArgumentos")) {
                        recolectarArgumentos(hijo, argumentos);
                    }
                }

                for (NodoArbol arg : argumentos) {
                    String valor = evaluarExpr(arg);
                    codigoIntermedio.append("   PARAM ").append(valor).append("\n");
                }

                codigoIntermedio.append("   CALL ")
                    .append(nombreFuncion)
                    .append("\n");


                String temp = nuevoTemporal();
                codigoIntermedio.append("   ").append(temp).append(" = RET\n");
                return temp;
            }


            // Operadores binarios
            case "+":
            case "-":
            case "*":
            case "/":
                return generarOperacionBinariaConTipo(nodo, tipo);
            case "%":
            case "^":
            case "==":
            case "!=":
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "@":
            case "~":
                return generarOperacionBinariaDesdeArbol(nodo);

            case "Σ":
                return generarOperacionNotDesdeArbol(nodo);

            default:
                for (NodoArbol hijo : nodo.getHijos()) {
                    String r = evaluarExpr(hijo);
                    if (!r.isEmpty()) {
                        return r;
                    }
                }
                return "";
        }
    }
    private String convertirOperadorFloat(String operador) {
        switch (operador) {
            case "+": return "+";
            case "-": return "-";
            case "*": return "*";
            case "/": return "/";
            default: return operador;
        }
    }

    private String removerSufijoFloat(String valor) {
        if (valor.endsWith("_f")) {
            return valor.substring(0, valor.length() - 2);
        }
        return valor;
    }

    private boolean esVariableFloat(String nombre) {
        // Heurísticas para identificar variables float
        if (variables.containsKey(nombre)) {
            return variables.get(nombre).equalsIgnoreCase("float");
        }
        return nombre.toLowerCase().contains("float") || 
            nombre.toLowerCase().contains("_f") ||
            nombre.toLowerCase().contains("base") ||
            nombre.toLowerCase().contains("resultado");
    }
    private String generarOperacionBinariaConTipo(NodoArbol nodo, String operador) {
        if (nodo.getHijos().size() >= 2) {
            String izquierda = evaluarExpr(nodo.getHijos().get(0));
            String derecha = evaluarExpr(nodo.getHijos().get(1));
            
            // Determinar tipos de los operandos
            boolean izqEsFloat = izquierda.endsWith("_f") || esVariableFloat(izquierda);
            boolean derEsFloat = derecha.endsWith("_f") || esVariableFloat(derecha);
            
            String temp = nuevoTemporal();
            
            if (izqEsFloat || derEsFloat) {
                // Operación de punto flotante
                String opFloat = convertirOperadorFloat(operador);
                codigoIntermedio.append("   ").append(temp).append("_f = ")
                            .append(removerSufijoFloat(izquierda)).append(" ")
                            .append(opFloat).append(" ")
                            .append(derecha).append("\n");
                return temp + "_f";
            } else {
                // Operación entera
                codigoIntermedio.append("   ").append(temp).append(" = ")
                            .append(izquierda).append(" ")
                            .append(operador).append(" ")
                            .append(derecha).append("\n");
                return temp;
            }
        }
        return "";
    }

    private String procesarAsignacionArray(NodoArbol nodo) {
        String arrayAccess = "";
        String valor = "";
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("array_access")) {
                arrayAccess = visitar(hijo);
            } else if (!hijo.getTipo().equals("ASSIGN")) {
                valor = visitar(hijo);
            }
        }
        
        if (!arrayAccess.isEmpty() && !valor.isEmpty()) {
            codigoIntermedio.append("   ").append(arrayAccess).append(" = ").append(valor).append("\n");
            return arrayAccess;
        }
        
        return "";
    }
    
    private String procesarAccesoArray(NodoArbol nodo) {
        String nombreArray = "";
        String indice1 = "";
        String indice2 = "";
        
        for (NodoArbol hijo : nodo.getHijos()) {
            switch (hijo.getTipo()) {
                case "int_literal":
                    if (indice1.isEmpty()) {
                        indice1 = hijo.getLexema();
                    } else {
                        indice2 = hijo.getLexema();
                    }
                    break;
                case "IDENTIFIER":
                    // Si es un identificador como índice
                    if (indice1.isEmpty()) {
                        indice1 = hijo.getLexema();
                    } else {
                        indice2 = hijo.getLexema();
                    }
                    break;
            }
        }
        
        if (!nombreArray.isEmpty() && !indice1.isEmpty()) {
            String temp = nuevoTemp();
            if (indice2.isEmpty()) {
                // Array 1D
                codigoIntermedio.append("   ").append(temp).append(" = ").append(nombreArray)
                              .append("[").append(indice1).append("]\n");
            } else {
                // Array 2D
                codigoIntermedio.append("   ").append(temp).append(" = ").append(nombreArray)
                              .append("[").append(indice1).append("][").append(indice2).append("]\n");
            }
            return temp;
        }
        
        return "";
    }
    
    private String procesarDecide(NodoArbol nodo) {
        String endLabel = nuevaEtiqueta();
        
        // Procesar todas las condiciones recursivamente
        procesarCondicionesDecide(nodo, endLabel);
        
        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        return "";
    }

    private void procesarCondicionesDecide(NodoArbol nodo, String endLabel) {
        // Buscar el nodo condicionesDecide
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("condicionesDecide")) {
                procesarListaCondicionesDecide(hijo, endLabel);
                break;
            }
        }
    }

    private void procesarListaCondicionesDecide(NodoArbol nodo, String endLabel) {
        // Procesar cada condición en la lista
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("condicionesDecide")) {
                // Recursión para condiciones anidadas
                procesarListaCondicionesDecide(hijo, endLabel);
            } else if (hijo.getTipo().equals("condicionDecide")) {
                procesarUnaCondicionDecide(hijo, endLabel);
            }
        }
    }

    private void procesarUnaCondicionDecide(NodoArbol nodo, String endLabel) {
        String condicion = "";
        NodoArbol bloque = null;
        String elseLabel = nuevaEtiqueta();
        
        // Buscar la condición y el bloque
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("()")) {
                // Extraer la condición
                for (NodoArbol expr : hijo.getHijos()) {
                    if (!expr.getTipo().equals("LPAREN") && !expr.getTipo().equals("RPAREN")) {
                        condicion = evaluarExpr(expr);
                        break;
                    }
                }
            } else if (hijo.getTipo().equals("bloque")) {
                bloque = hijo;
            }
        }
        
        if (!condicion.isEmpty()) {
            // Generar IF NOT condición GOTO elseLabel
            String tempCond = nuevoTemporal();
            codigoIntermedio.append("   ").append(tempCond).append(" = ").append(condicion).append("\n");
            codigoIntermedio.append("   IF NOT ").append(tempCond).append(" GOTO ").append(elseLabel).append("\n");
            
            // Procesar el bloque si la condición es verdadera
            if (bloque != null) {
                visitar(bloque);
            }
            
            // Saltar al final del decide después del bloque
            codigoIntermedio.append("   GOTO ").append(endLabel).append("\n");
            
            // Etiqueta para else (siguiente condición o fin)
            codigoIntermedio.append("   ").append(elseLabel).append(":\n");
        }
    }

    
    private String procesarDecideConElse(NodoArbol nodo) {
        String elseLabel = nuevaEtiqueta();
        String endLabel = nuevaEtiqueta();
        
        // Procesar todas las condiciones primero
        boolean tieneElse = false;
        NodoArbol elseBloque = null;
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("condicionesDecide")) {
                procesarListaCondicionesConElse(hijo, elseLabel, endLabel);
            } else if (hijo.getTipo().equals("ELSE")) {
                tieneElse = true;
                // Buscar el bloque else
                int idx = nodo.getHijos().indexOf(hijo);
                if (idx + 2 < nodo.getHijos().size()) {
                    elseBloque = nodo.getHijos().get(idx + 2);
                }
            }
        }
        
        // Etiqueta else
        codigoIntermedio.append("   ").append(elseLabel).append(":\n");
        
        // Procesar bloque else si existe
        if (tieneElse && elseBloque != null) {
            visitar(elseBloque);
        }
        
        codigoIntermedio.append("   GOTO ").append(endLabel).append("\n");
        
        // Etiqueta de fin
        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        return "";
    }

    private void procesarListaCondicionesConElse(NodoArbol nodo, String elseLabel, String endLabel) {
        // Procesar cada condición
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("condicionesDecide")) {
                procesarListaCondicionesConElse(hijo, elseLabel, endLabel);
            } else if (hijo.getTipo().equals("condicionDecide")) {
                procesarUnaCondicionConElse(hijo, elseLabel, endLabel);
            }
        }
    }

    private void procesarUnaCondicionConElse(NodoArbol nodo, String elseLabel, String endLabel) {
        String condicion = "";
        NodoArbol bloque = null;
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("()")) {
                for (NodoArbol expr : hijo.getHijos()) {
                    if (!expr.getTipo().equals("LPAREN") && !expr.getTipo().equals("RPAREN")) {
                        condicion = evaluarExpr(expr);
                        break;
                    }
                }
            } else if (hijo.getTipo().equals("bloque")) {
                bloque = hijo;
            }
        }
        
        if (!condicion.isEmpty()) {
            String tempCond = nuevoTemporal();
            codigoIntermedio.append("   ").append(tempCond).append(" = ").append(condicion).append("\n");
            codigoIntermedio.append("   IF NOT ").append(tempCond).append(" GOTO ").append(elseLabel).append("\n");
            
            if (bloque != null) {
                visitar(bloque);
            }
            
            codigoIntermedio.append("   GOTO ").append(endLabel).append("\n");
        }
    }

    
    private String procesarCondicionDecide(NodoArbol nodo) {
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("()")) {
                for (NodoArbol expr : hijo.getHijos()) {
                    if (!expr.getTipo().equals("LPAREN") && !expr.getTipo().equals("RPAREN")) {
                        return evaluarExpr(expr);
                    }
                }
            } else if (hijo.getTipo().equals("bloque")) {
                return visitar(hijo);
            } else if (hijo.getTipo().equals("bool_literal") ||
                    hijo.getTipo().equals("IDENTIFIER") ||
                    hijo.getTipo().equals("==") ||
                    hijo.getTipo().equals("<") ||
                    hijo.getTipo().equals("<=") ||
                    hijo.getTipo().equals(">") ||
                    hijo.getTipo().equals(">=")) {
                return evaluarExpr(hijo);
            } 
        }
        return "";
    }
    
    private String procesarLoop(NodoArbol nodo) {
        String startLabel = nuevaEtiqueta();
        String endLabel = nuevaEtiqueta();
        
        // Etiqueta especial para EXIT WHEN
        String exitWhenLabel = "L_exit_when_" + labelCounter++;
        
        // Guardar la etiqueta de fin para BREAK
        breakLabels.push(endLabel);

        // Inicio del loop
        codigoIntermedio.append("   ").append(startLabel).append(":\n");
        
        // Buscar y procesar el cuerpo del loop
        NodoArbol cuerpoLoop = null;
        boolean tieneExitWhen = false;
        String condicionExit = "";
        
        // Primero buscar el cuerpo del loop
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("listaInstr")) {
                cuerpoLoop = hijo;
            }
            if (hijo.getTipo().equals("()")) {
                tieneExitWhen = true;
            }
                    
        }
        
        // Ahora procesar el cuerpo buscando EXIT WHEN
        if (cuerpoLoop != null) {
            // Crear una copia de los hijos para procesar
            List<NodoArbol> hijosCuerpo = new ArrayList<>(cuerpoLoop.getHijos());
            
            for (NodoArbol instruccion : hijosCuerpo) {
                visitar(instruccion);
            }
        }
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("()")) {
                // Extraer la condición de EXIT WHEN
                for (NodoArbol expr : hijo.getHijos()) {
                    if (!expr.getTipo().equals("LPAREN") && !expr.getTipo().equals("RPAREN")) {
                        condicionExit = evaluarExpr(expr);
                        break;
                    }
                }
            }
        }
        if (tieneExitWhen) {
            String tempCond = nuevoTemporal();
            codigoIntermedio.append("   ").append(tempCond).append(" = ").append(condicionExit).append("\n");
            codigoIntermedio.append("   IF ").append(tempCond).append(" GOTO ").append(exitWhenLabel).append("\n");
        }
        
        // Al final del cuerpo del loop, volver al inicio
        codigoIntermedio.append("   GOTO ").append(startLabel).append("\n");
        
        // Etiqueta para salir con EXIT WHEN
        if (tieneExitWhen) {
            codigoIntermedio.append("   ").append(exitWhenLabel).append(":\n");
        }
        
        // Etiqueta para BREAK
        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        
        // Quitar la etiqueta de break de la pila
        breakLabels.pop();
        
        return "";
    }
    
    private String procesarFor(NodoArbol nodo) {
        String startLabel = nuevaEtiqueta();
        String endLabel = nuevaEtiqueta();
        
        breakLabels.push(endLabel);
        
        // Procesar inicialización, condición, incremento y cuerpo
        NodoArbol initNode = null;
        NodoArbol condNode = null;
        NodoArbol incNode = null;
        NodoArbol bloqueNode = null;
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("declaracionVariable_local_asign")) {
                initNode = hijo;
            } else if (hijo.getTipo().equals(">=") || hijo.getTipo().equals(">") || 
                    hijo.getTipo().equals("<=") || hijo.getTipo().equals("<")) {
                condNode = hijo;
            } else if (hijo.getTipo().equals("--_pre") || hijo.getTipo().equals("++_pre")) {
                incNode = hijo;
            } else if (hijo.getTipo().equals("bloque")) {
                bloqueNode = hijo;
            }
        }
        
        // Inicialización
        if (initNode != null) {
            visitar(initNode);
        }
        
        // Saltar a la condición primero
        String condLabel = nuevaEtiqueta();
        codigoIntermedio.append("   GOTO ").append(condLabel).append("\n");
        
        // Cuerpo del for
        codigoIntermedio.append("   ").append(startLabel).append(":\n");
        if (bloqueNode != null) {
            visitar(bloqueNode.getHijos().get(1));
        }
        
        // Incremento
        if (incNode != null) {
            evaluarExpr(incNode);
        }
        
        // Condición (etiqueta para evaluar condición)
        codigoIntermedio.append("   ").append(condLabel).append(":\n");
        if (condNode != null) {
            String condicion = evaluarExpr(condNode);
            String tempCond = nuevoTemporal();
            codigoIntermedio.append("   ").append(tempCond).append(" = ").append(condicion).append("\n");
            codigoIntermedio.append("   IF ").append(tempCond).append(" GOTO ").append(startLabel).append("\n");
        }
        
        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        
        breakLabels.pop();
        return "";
    }
    
    private String generarOperacionBinaria(NodoArbol nodo, String operador) {
        if (nodo.getHijos().size() >= 2) {
            String izquierda = visitar(nodo.getHijos().get(0));
            String derecha = visitar(nodo.getHijos().get(1));
            
            if (!izquierda.isEmpty() && !derecha.isEmpty()) {
                String temp = nuevoTemp();
                codigoIntermedio.append("   ").append(temp).append(" = ").append(izquierda)
                              .append(" ").append(operador).append(" ").append(derecha).append("\n");
                return temp;
            }
        }
        return "";
    }
    
    private String generarOperacionNot(NodoArbol nodo) {
        if (nodo.getHijos().size() > 0) {
            String operando = visitar(nodo.getHijos().get(0));
            if (!operando.isEmpty()) {
                String temp = nuevoTemp();
                codigoIntermedio.append("   ").append(temp).append(" = !").append(operando).append("\n");
                return temp;
            }
        }
        return "";
    }
    
    private String generarOperacionUnaria(NodoArbol nodo, String operador) {
        if (nodo.getHijos().size() > 0) {
            String operando = visitar(nodo.getHijos().get(0));
            if (!operando.isEmpty()) {
                String temp = nuevoTemp();
                codigoIntermedio.append("   ").append(temp).append(" = ").append(operador).append(operando).append("\n");
                return temp;
            }
        }
        return "";
    }
    
    private String generarIncrementoPre(NodoArbol nodo) {
        if (nodo.getHijos().size() > 0) {
            String variable = visitar(nodo.getHijos().get(0));
            if (!variable.isEmpty()) {
                String temp1 = nuevoTemp();
                String temp2 = nuevoTemp();
                codigoIntermedio.append("   ").append(temp1).append(" = ").append(variable).append(" + 1\n");
                codigoIntermedio.append("   ").append(variable).append(" = ").append(temp1).append("\n");
                codigoIntermedio.append("   ").append(temp2).append(" = ").append(variable).append("\n");
                return temp2;
            }
        }
        return "";
    }
    
    private String generarDecrementoPre(NodoArbol nodo) {
        if (nodo.getHijos().size() > 0) {
            String variable = visitar(nodo.getHijos().get(0));
            if (!variable.isEmpty()) {
                String temp1 = nuevoTemp();
                String temp2 = nuevoTemp();
                codigoIntermedio.append("   ").append(temp1).append(" = ").append(variable).append(" - 1\n");
                codigoIntermedio.append("   ").append(variable).append(" = ").append(temp1).append("\n");
                codigoIntermedio.append("   ").append(temp2).append(" = ").append(variable).append("\n");
                return temp2;
            }
        }
        return "";
    }
}