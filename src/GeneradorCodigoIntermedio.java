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
                codigoIntermedio.append("GOTO navidad\n");
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";
                
            case "funcion":
                return procesarFuncion(nodo);
                
            case "navidad":
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
            case "GET":
            case "DECIDE":
            case "OF":
            case "END":
            case "LOOP":
            case "EXIT":
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
        codigoIntermedio.append("   ").append("RETURN 0\n");
        
    }
    private String generarOperacionBinariaDesdeArbol(NodoArbol nodo) {
        // En el árbol, los operandos pueden estar en diferentes posiciones
        String operador = obtenerOperador(nodo.getTipo());
        String izquierda = "";
        String derecha = "";
        
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
                    codigoIntermedio.append(temp).append(" = !").append(operando).append("\n");
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
                case "MINUS":
                    if (conAsignacion && hijo.getHijos().size() > 0) {
                        NodoArbol valorHijo = hijo.getHijos().get(0);
                        if (valorHijo.getTipo().endsWith("_literal")) {
                            valor = "-" + valorHijo.getLexema();
                        } else {
                            valor = evaluarExpr(valorHijo);
                        }
                    }
                    break;
                case "string_literal":
                    valor = registrarString(hijo.getLexema());  // Ya retorna el label
                    break;
            }
        }
        
        if (!nombre.isEmpty()) {
            variables.put(nombre, tipo);
            
            if (conAsignacion && valor != null) {
                String temp = nuevoTemporal();
                codigoIntermedio.append("   ").append(temp).append(" = ").append(valor).append("\n");
                codigoIntermedio.append("   ").append(nombre).append(" = ").append(temp).append("\n");
            } else {
                codigoIntermedio.append("   ").append("LOCAL ").append(nombre).append(" : ").append(tipo).append("\n");
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
        for (NodoArbol hijo : nodo.getHijos()) {
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
        
        // Buscar valor (derecha del =)
        // El valor puede ser una expresión compleja
        for (int i = 0; i < nodo.getHijos().size(); i++) {
            NodoArbol hijo = nodo.getHijos().get(i);
            if (hijo.getTipo().equals("ASSIGN")) {
                // El siguiente hijo después de ASSIGN es la expresión
                if (i + 1 < nodo.getHijos().size()) {
                    valor = evaluarExpr(nodo.getHijos().get(i + 1));
                }
                break;
            }
        }
        
        if (!destino.isEmpty() && !valor.isEmpty()) {
            codigoIntermedio.append("   ").append(destino).append(" = ").append(valor).append("\n");
        }
        
        return destino;
    }

    private String evaluarExpr(NodoArbol nodo) {
        String tipo = nodo.getTipo();
    
        switch (tipo) {
            case "int_literal":
            case "float_literal":
            case "bool_literal":
                return nodo.getLexema();
                
            case "char_literal":
                // Para char, mantener el formato con comillas simples
                return "'" + nodo.getLexema() + "'";
                
            case "string_literal":
                return registrarString(nodo.getLexema());
                
            case "IDENTIFIER":
                return nodo.getLexema();
                
            case "MINUS": {
                if (nodo.getHijos().size() > 0) {
                    String valor = evaluarExpr(nodo.getHijos().get(0));
                    String temp = nuevoTemporal();
                    codigoIntermedio.append("   ").append(temp).append(" = -").append(valor).append("\n");
                    return temp;
                }
                return "";
            }
                
            case "++_pre": {
                if (nodo.getHijos().size() > 0) {
                    String var = evaluarExpr(nodo.getHijos().get(0));
                    String temp1 = nuevoTemporal();
                    String temp2 = nuevoTemporal();
                    codigoIntermedio.append("   ").append(temp1).append(" = ").append(var).append(" + 1\n");
                    codigoIntermedio.append("   ").append(var).append(" = ").append(temp1).append("\n");
                    codigoIntermedio.append("   ").append(temp2).append(" = ").append(var).append("\n");
                    return temp2;
                }
                return "";
            }
                
            case "--_pre": {
                if (nodo.getHijos().size() > 0) {
                    String var = evaluarExpr(nodo.getHijos().get(0));
                    String temp1 = nuevoTemporal();
                    String temp2 = nuevoTemporal();
                    codigoIntermedio.append("   ").append(temp1).append(" = ").append(var).append(" - 1\n");
                    codigoIntermedio.append("   ").append(var).append(" = ").append(temp1).append("\n");
                    codigoIntermedio.append("   ").append(temp2).append(" = ").append(var).append("\n");
                    return temp2;
                }
                return "";
            }
                
            // Operadores binarios
            case "+":
            case "-":
            case "*":
            case "/":
            case "%":
            case "^":
            case "==":
            case "!=":
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "@":  // AND
            case "~":  // OR
                return generarOperacionBinariaDesdeArbol(nodo);
                
            case "Σ":  // NOT
                return generarOperacionNotDesdeArbol(nodo);
                
            default:
                // Para nodos compuestos
                for (NodoArbol hijo : nodo.getHijos()) {
                    String result = evaluarExpr(hijo);
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
                return "";
        }
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

        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("condicionesDecide")) {
                for (NodoArbol cond : hijo.getHijos()) {
                    if (!cond.getTipo().equals("condicionDecide")){
                        visitar(cond);
                        continue;
                    }

                    String condicion = procesarCondicionDecide(cond);
                    codigoIntermedio.append("   IF NOT ")
                            .append(condicion)
                            .append(" GOTO ")
                            .append(endLabel)
                            .append("\n");

                    for (NodoArbol c : cond.getHijos()) {
                        if (c.getTipo().equals("bloque")) {
                            visitar(c);
                            break;
                        }
                    }
                }
            }
        }

        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        return "";
    }

    
    private String procesarDecideConElse(NodoArbol nodo) {
        String elseLabel = nuevaEtiqueta();
        String endLabel = nuevaEtiqueta();

        NodoArbol condiciones = null;
        NodoArbol elseBloque = null;

        for (int i = 0; i < nodo.getHijos().size(); i++) {
            NodoArbol h = nodo.getHijos().get(i);
            if (h.getTipo().equals("condicionesDecide")) {
                condiciones = h;
            } else if (h.getTipo().equals("ELSE")) {
                if (i + 2 < nodo.getHijos().size()) {
                    elseBloque = nodo.getHijos().get(i + 2);
                }
            }
        }

        if (condiciones != null) {
            for (NodoArbol cond : condiciones.getHijos()) {
                if (!cond.getTipo().equals("condicionDecide")) continue;

                String condicion = procesarCondicionDecide(cond);
                codigoIntermedio.append("   IF NOT ")
                        .append(condicion)
                        .append(" GOTO ")
                        .append(elseLabel)
                        .append("\n");

                for (NodoArbol h : cond.getHijos()) {
                    if (h.getTipo().equals("bloque")) {
                        visitar(h);
                        break;
                    }
                }

                codigoIntermedio.append("   GOTO ").append(endLabel).append("\n");
            }
        }

        codigoIntermedio.append("   ").append(elseLabel).append(":\n");

        if (elseBloque != null) {
            visitar(elseBloque);
        }

        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        return "";
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
        String exitLabel = nuevaEtiqueta();  // Para EXIT WHEN
        String endLabel = nuevaEtiqueta();   // Para fin del loop
        
        breakLabels.push(endLabel);
        
        codigoIntermedio.append("   ").append(startLabel).append(":\n");
        
        // Procesar instrucciones del loop
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("listaInstr")) {
                visitar(hijo);
            }
        }
        
        // Volver al inicio del loop
        codigoIntermedio.append("   GOTO ").append(startLabel).append("\n");
        
        // Etiqueta para EXIT WHEN (si se cumple la condición, salta aquí)
        codigoIntermedio.append("   ").append(exitLabel).append(":\n");
        
        // Fin del loop
        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        
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
            } else if (hijo.getTipo().equals("--_pre")) {
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
            visitar(bloqueNode);
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