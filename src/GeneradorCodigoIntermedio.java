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
    private Stack<String> continueLabels;
    private HashMap<String, String> variables;
    private HashMap<String, String> funciones;
    private HashMap<String, String> stringConstants;
    private boolean enFuncion;
    private String funcionActual;
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
        this.continueLabels = new Stack<>();
        this.variables = new HashMap<>();
        this.funciones = new HashMap<>();
        this.stringConstants = new HashMap<>();
        this.enFuncion = false;
        this.funcionActual = "";
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
        
        String label = "_str_" + stringCounter++;
        stringConstants.put(label, str);
        codigoData.append("").append(label).append(": string \"").append(str).append("\"\n");
        return label;
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
                System.out.println(valorInicial);
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

    private String visitar(NodoArbol nodo) {
        if (nodo == null) return "";
        
        String tipo = nodo.getTipo();
        String lexema = nodo.getLexema();
        if (tipo.equals("globales")) System.out.println("Procesando " + tipo);
        
        procesarGlobales(nodo);
        
        switch (tipo) {
            case "program":
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";
                
            case "funciones":
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
                
            case "ОЈ": // NOT (carácter cirílico S)
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
                
            case "string_literal":
                String strLabel = registrarString(lexema);
                return strLabel;
                
            case "IDENTIFIER":
                return lexema;
                
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
            case "ELSE":
            case "END":
            case "LOOP":
            case "EXIT":
            case "WHEN":
            case "FOR":
            case "BREAK":
            case "ARROW":
            case "COMMA":
            case "ASSIGN":
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
        enFuncion = true;
        funcionActual = "navidad";
        
        codigoIntermedio.append("   ").append("\n# FUNCION PRINCIPAL (navidad)\n");
        codigoIntermedio.append("main:\n");
        
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
        
        enFuncion = false;
        funcionActual = "";
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
        
        enFuncion = true;
        funcionActual = nombreFunc;
        
        codigoIntermedio.append("\n# FUNCION ").append(nombreFunc).append(" -> ").append(tipoRetorno).append("\n");
        codigoIntermedio.append("func_").append(nombreFunc).append(":\n");
        
        // Procesar parámetros si existen
        boolean enParametros = false;
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("parametros") || hijo.getTipo().equals("listaParametros")) {
                enParametros = true;
                procesarParametros(hijo);
            } else if (enParametros && hijo.getTipo().equals("bloque")) {
                // Procesar cuerpo de la función
                visitar(hijo);
            }
        }
        
        // Si no se procesó el bloque, buscarlo directamente
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("bloque")) {
                visitar(hijo);
                break;
            }
        }
        
        // Si no hay return en el cuerpo, agregar uno según el tipo
        if (tipoRetorno.equals("float") || tipoRetorno.equals("int")) {
            codigoIntermedio.append("   ").append("RETURN 0\n");
        } else if (tipoRetorno.equals("bool")) {
            codigoIntermedio.append("   ").append("RETURN false\n");
        } else {
            codigoIntermedio.append("   ").append("RETURN\n");
        }
        
        enFuncion = false;
        funcionActual = "";
        return nombreFunc;
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
        String valor = "";
        
        for (NodoArbol hijo : nodo.getHijos()) {
            switch (hijo.getTipo()) {
                case "INT":
                case "FLOAT":
                case "BOOL":
                case "CHAR":
                case "STRING":
                    tipo = hijo.getLexema().toLowerCase();
                    break;
                case "int_literal":
                case "float_literal":
                case "bool_literal":
                case "char_literal":
                case "string_literal":
                case "IDENTIFIER":
                    if (conAsignacion) {
                        valor = visitar(hijo);
                    }
                    nombre = hijo.getLexema();
                    break;
                case "MINUS":
                case "++_pre":
                case "--_pre":
                    if (conAsignacion) {
                        valor = visitar(hijo);
                    }
                    break;
            }
        }
        
        if (nombre.isEmpty()) return "";
        
        variables.put(nombre, tipo);
        
        if (conAsignacion && !valor.isEmpty()) {
            codigoIntermedio.append("   ").append(nombre).append(" = ").append(valor).append("\n");
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
        
        // Buscar la expresión a mostrar
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("string_literal") || 
                hijo.getTipo().equals("int_literal") ||
                hijo.getTipo().equals("float_literal") ||
                hijo.getTipo().equals("bool_literal") ||
                hijo.getTipo().equals("char_literal") ||
                hijo.getTipo().equals("IDENTIFIER") ||
                hijo.getTipo().equals("MINUS")) {
                
                expresion = visitar(hijo);
                break;
            }
        }
        
        if (!expresion.isEmpty()) {
            codigoIntermedio.append("   ").append("PRINT ").append(expresion).append("\n");
        }
        
        return expresion;
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
        // Buscar identificador a leer
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("get_statement")) {
                for (NodoArbol nieto : hijo.getHijos()) {
                    if (nieto.getTipo().equals("IDENTIFIER")) {
                        String var = nieto.getLexema();
                        codigoIntermedio.append("   ").append("READ ").append(var).append("\n");
                        return var;
                    }
                }
            }
        }
        return "";
    }
    
    private String procesarAsignacionSimple(NodoArbol nodo) {

        if (!nodo.getTipo().equals("=")) {
            return "";
        }

        NodoArbol izquierda = nodo.getHijos().get(0);
        NodoArbol derecha = nodo.getHijos().get(1);

        if (!izquierda.getTipo().startsWith("id")) {
            return "";
        }

        String destino = izquierda.getLexema();
        String valor = evaluarExpr(derecha);

        codigoIntermedio.append(destino)
                    .append(" = ")
                    .append(valor)
                    .append("\n");
        return "";
    }

    private String evaluarExpr(NodoArbol nodo) {

        String tipo = nodo.getTipo();

        switch (tipo) {

            case "literalint":
            case "literalfloat":
            case "literalbool":
            case "literalchar":
            case "literalstring":
                return nodo.getLexema();

            case "id":
            case "ida":
            case "idb":
                return nodo.getLexema();

            case "accesoArray": {
                String nombre = nodo.getHijos().get(0).getLexema();
                String indice = evaluarExpr(nodo.getHijos().get(2));
                String temp = nuevoTemporal();

                codigoIntermedio.append(temp)
                            .append(" = ")
                            .append(nombre)
                            .append("[")
                            .append(indice)
                            .append("]\n");
                return temp;
            }

            case "minus": {
                String valor = evaluarExpr(nodo.getHijos().get(0));
                String temp = nuevoTemporal();

                codigoIntermedio.append(temp)
                            .append(" = - ")
                            .append(valor)
                            .append("\n");
                return temp;
            }

            case "+":
            case "-":
            case "*":
            case "/":
            case "%": {
                String izq = evaluarExpr(nodo.getHijos().get(0));
                String der = evaluarExpr(nodo.getHijos().get(1));
                String temp = nuevoTemporal();

                codigoIntermedio.append(temp)
                            .append(" = ")
                            .append(izq)
                            .append(" ")
                            .append(tipo)
                            .append(" ")
                            .append(der)
                            .append("\n");
                return temp;
            }

            case "expr":
                if (nodo.getHijos().size() == 1) {
                    return evaluarExpr(nodo.getHijos().get(0));
                }
                return evaluarExpr(nodo.getHijos().get(1));

            default:
                for (NodoArbol hijo : nodo.getHijos()) {
                    return evaluarExpr(hijo);
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
        
        // Buscar y procesar condiciones
        boolean primeraCondicion = true;
        String falseLabel = null;
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("condicionesDecide")) {
                for (NodoArbol condHijo : hijo.getHijos()) {
                    if (condHijo.getTipo().equals("condicionDecide")) {
                        if (!primeraCondicion) {
                            codigoIntermedio.append("   ").append(falseLabel).append(":\n");
                        }
                        
                        falseLabel = nuevaEtiqueta();
                        String condicion = procesarCondicionDecide(condHijo);
                        
                        codigoIntermedio.append("   ").append("IF NOT ").append(condicion)
                                      .append(" GOTO ").append(falseLabel).append("\n");
                        
                        primeraCondicion = false;
                    }
                }
            }
        }
        
        if (falseLabel != null) {
            codigoIntermedio.append("   ").append(falseLabel).append(":\n");
        }
        
        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        return "";
    }
    
    private String procesarDecideConElse(NodoArbol nodo) {
        String elseLabel = nuevaEtiqueta();
        String endLabel = nuevaEtiqueta();
        
        // Procesar condiciones (if/else if)
        boolean encontradaCondicion = false;
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("condicionesDecide")) {
                encontradaCondicion = true;
                for (NodoArbol condHijo : hijo.getHijos()) {
                    if (condHijo.getTipo().equals("condicionDecide")) {
                        String condLabel = nuevaEtiqueta();
                        String condicion = procesarCondicionDecide(condHijo);
                        
                        codigoIntermedio.append("   ").append("IF NOT ").append(condicion)
                                      .append(" GOTO ").append(condLabel).append("\n");
                        
                        // Código si condición verdadera
                        // Buscar bloque
                        for (NodoArbol bloqueHijo : condHijo.getHijos()) {
                            if (bloqueHijo.getTipo().equals("bloque")) {
                                visitar(bloqueHijo);
                                break;
                            }
                        }
                        
                        codigoIntermedio.append("   ").append("GOTO ").append(endLabel).append("\n");
                        codigoIntermedio.append("   ").append(condLabel).append(":\n");
                    }
                }
            } else if (hijo.getTipo().equals("ELSE") && encontradaCondicion) {
                // Procesar else
                codigoIntermedio.append("   ").append(elseLabel).append(":\n");
                
                // Buscar bloque del else
                int idx = nodo.getHijos().indexOf(hijo);
                if (idx + 2 < nodo.getHijos().size()) {
                    NodoArbol elseBloque = nodo.getHijos().get(idx + 2);
                    if (elseBloque.getTipo().equals("bloque")) {
                        visitar(elseBloque);
                    }
                }
                
                codigoIntermedio.append("   ").append("GOTO ").append(endLabel).append("\n");
            }
        }
        
        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        return "";
    }
    
    private String procesarCondicionDecide(NodoArbol nodo) {
        String condicion = "";
        NodoArbol bloque = null;
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("()")) {
                // Expresión entre paréntesis
                for (NodoArbol exprHijo : hijo.getHijos()) {
                    if (!exprHijo.getTipo().equals("LPAREN") && 
                        !exprHijo.getTipo().equals("RPAREN")) {
                        condicion = visitar(exprHijo);
                        break;
                    }
                }
            } else if (hijo.getTipo().equals("bool_literal") ||
                      hijo.getTipo().equals("IDENTIFIER")) {
                condicion = visitar(hijo);
            } else if (hijo.getTipo().equals("bloque")) {
                bloque = hijo;
            }
        }
        
        return condicion;
    }
    
    private String procesarLoop(NodoArbol nodo) {
        String startLabel = nuevaEtiqueta();
        String endLabel = nuevaEtiqueta();
        
        breakLabels.push(endLabel);
        
        codigoIntermedio.append("   ").append(startLabel).append(":\n");
        
        // Procesar instrucciones del loop
        boolean encontradoExit = false;
        String condicionExit = "";
        
        for (NodoArbol hijo : nodo.getHijos()) {
            if (hijo.getTipo().equals("listaInstr")) {
                visitar(hijo);
            } else if (hijo.getTipo().equals("EXIT")) {
                encontradoExit = true;
                // Buscar condición del exit when
                for (NodoArbol exitHijo : hijo.getHijos()) {
                    if (exitHijo.getTipo().equals("WHEN")) {
                        // El siguiente hijo debería ser la condición
                        int idx = hijo.getHijos().indexOf(exitHijo);
                        if (idx + 1 < hijo.getHijos().size()) {
                            NodoArbol condNode = hijo.getHijos().get(idx + 1);
                            condicionExit = visitar(condNode);
                        }
                        break;
                    }
                }
            }
        }
        
        if (encontradoExit && !condicionExit.isEmpty()) {
            codigoIntermedio.append("   ").append("IF ").append(condicionExit).append(" GOTO ").append(endLabel).append("\n");
        }
        
        codigoIntermedio.append("   ").append("GOTO ").append(startLabel).append("\n");
        codigoIntermedio.append("   ").append(endLabel).append(":\n");
        
        breakLabels.pop();
        return "";
    }
    
    private String procesarFor(NodoArbol nodo) {
        String startLabel = nuevaEtiqueta();
        String endLabel = nuevaEtiqueta();
        
        breakLabels.push(endLabel);
        
        // Procesar inicialización
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
        
        codigoIntermedio.append("   ").append(startLabel).append(":\n");
        
        // Condición
        if (condNode != null) {
            String condicion = visitar(condNode);
            codigoIntermedio.append("   ").append("IF NOT ").append(condicion).append(" GOTO ").append(endLabel).append("\n");
        }
        
        // Cuerpo
        if (bloqueNode != null) {
            visitar(bloqueNode);
        }
        
        // Incremento
        if (incNode != null) {
            visitar(incNode);
        }
        
        codigoIntermedio.append("   ").append("GOTO ").append(startLabel).append("\n");
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