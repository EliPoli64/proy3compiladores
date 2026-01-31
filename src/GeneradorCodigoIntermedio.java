public class GeneradorCodigoIntermedio {
    private StringBuilder codigoIntermedio;
    private int tempCounter;
    private int labelCounter;
    private String currentBreakLabel;

    public GeneradorCodigoIntermedio() {
        this.codigoIntermedio = new StringBuilder();
        this.tempCounter = 1;
        this.labelCounter = 1;
        this.currentBreakLabel = null;
    }

    private void procesarCondiciones(NodoArbol nodo, String labelSalida) {
        if (nodo.getHijos().get(0).getTipo().equals("condicionesDecide")) {
            procesarCondiciones(nodo.getHijos().get(0), labelSalida);
            NodoArbol condNode = nodo.getHijos().get(1);
            procesarUnicaCondicion(condNode, labelSalida);
        } else {
            NodoArbol condNode = nodo.getHijos().get(0);
            procesarUnicaCondicion(condNode, labelSalida);
        }
    }

    private void procesarUnicaCondicion(NodoArbol nodo, String labelSalida) {
        NodoArbol expr = nodo.getHijos().get(0);
        NodoArbol bloque = nodo.getHijos().get(2);
        generarCondicionIf(expr, bloque, labelSalida);
    }

    private void generarCondicionIf(NodoArbol expr, NodoArbol bloque, String labelSalida) {
        String lblTrue = nuevaEtiqueta();
        String lblFalse = nuevaEtiqueta();
        String condVal = visitar(expr);

        codigoIntermedio.append("if ").append(condVal).append(" goto ").append(lblTrue).append("\n");
        codigoIntermedio.append("goto ").append(lblFalse).append("\n");
        
        codigoIntermedio.append(lblTrue).append(":\n");
        visitar(bloque);
        codigoIntermedio.append("goto ").append(labelSalida).append("\n");
        
        codigoIntermedio.append(lblFalse).append(":\n");
    }

    public String generar(NodoArbol raiz) {
        visitar(raiz);
        return codigoIntermedio.toString();
    }

    private String nuevoTemp() {
        return "t" + (tempCounter++);
    }

    private String nuevaEtiqueta() {
        return "L" + (labelCounter++);
    }

    private String visitar(NodoArbol nodo) {
        if (nodo == null) return "";

        String tipo = nodo.getTipo();

        switch (tipo) {
            case "program":
            case "globales":
            case "funciones":
            case "bloque":
            case "listaInstr":
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";

            case "navidad":
                codigoIntermedio.append("\nmain:\n");
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";

            case "funcion":
                String nombreFunc = nodo.getHijos().get(2).getLexema();
                codigoIntermedio.append("\nfunc_").append(nombreFunc).append(":\n");
                visitar(nodo.getHijos().get(6));
                return "";

            case "funcion_vacia":
                String nombreFuncVacia = nodo.getHijos().get(0).getLexema();
                codigoIntermedio.append("\nfunc_").append(nombreFuncVacia).append(":\n");
                return "";

            case "instruccion":
            case "instruccion_endl":
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";

            case "instruccion_show":
                String resShow = visitar(nodo.getHijos().get(1));
                codigoIntermedio.append("print ").append(resShow).append("\n");
                return "";

            case "instruccion_return":
                String resRet = visitar(nodo.getHijos().get(1));
                codigoIntermedio.append("return ").append(resRet).append("\n");
                return "";

            case "declaracionVariable_local":
                return "";

            case "declaracionVariable_local_asign":
                String id = nodo.getHijos().get(2).getLexema();
                String val = visitar(nodo.getHijos().get(4));
                codigoIntermedio.append(id).append(" = ").append(val).append("\n");
                return id;

            case "=":
                String idAssign = nodo.getHijos().get(0).getLexema();
                String valAssign = visitar(nodo.getHijos().get(1));
                codigoIntermedio.append(idAssign).append(" = ").append(valAssign).append("\n");
                return idAssign;

            case "+":
            case "-":
            case "*":
            case "/":
            case "//":
            case "%":
            case "^":
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "==":
            case "!=":
                return generarOperacionBinaria(nodo, tipo);

            case "@":
                return generarOperacionBinaria(nodo, "&&");

            case "~":
                return generarOperacionBinaria(nodo, "||");

            case "MINUS":
                String operando = visitar(nodo.getHijos().get(0));
                String temp = nuevoTemp();
                codigoIntermedio.append(temp).append(" = -").append(operando).append("\n");
                return temp;

            case "++_pre":
                String idInc = nodo.getHijos().get(0).getLexema();
                codigoIntermedio.append(idInc).append(" = ").append(idInc).append(" + 1\n");
                return idInc;

            case "--_pre":
                String idDec = nodo.getHijos().get(0).getLexema();
                codigoIntermedio.append(idDec).append(" = ").append(idDec).append(" - 1\n");
                return idDec;

            case "()":
                return visitar(nodo.getHijos().get(1));

            case "int_literal":
            case "float_literal":
            case "bool_literal":
            case "char_literal":
            case "string_literal":
            case "IDENTIFIER":
                return nodo.getLexema();

            case "decide":
                String labelSalida = nuevaEtiqueta();
                procesarCondiciones(nodo.getHijos().get(2), labelSalida);
                codigoIntermedio.append(labelSalida).append(":\n");
                return "";

            case "decide_with_else":
                String labelSalidaElse = nuevaEtiqueta();
                procesarCondiciones(nodo.getHijos().get(2), labelSalidaElse);
                visitar(nodo.getHijos().get(5));
                codigoIntermedio.append(labelSalidaElse).append(":\n");
                return "";

            case "loop":
                String labelInicio = nuevaEtiqueta();
                String labelSalir = nuevaEtiqueta();
                String labelAnterior = this.currentBreakLabel;
                this.currentBreakLabel = labelSalir;

                codigoIntermedio.append(labelInicio).append(":\n");
                visitar(nodo.getHijos().get(1));
                String condExit = visitar(nodo.getHijos().get(4));
                codigoIntermedio.append("if not ").append(condExit).append(" goto ").append(labelSalir).append("\n");
                codigoIntermedio.append("goto ").append(labelInicio).append("\n");
                codigoIntermedio.append(labelSalir).append(":\n");

                this.currentBreakLabel = labelAnterior;
                return "";

            case "for_stmt":
                String lblInicio = nuevaEtiqueta();
                String lblFin = nuevaEtiqueta();
                String prevBreak = this.currentBreakLabel;
                this.currentBreakLabel = lblFin;

                visitar(nodo.getHijos().get(2));
                codigoIntermedio.append(lblInicio).append(":\n");
                String condFor = visitar(nodo.getHijos().get(3));
                codigoIntermedio.append("if not ").append(condFor).append(" goto ").append(lblFin).append("\n");
                visitar(nodo.getHijos().get(7));
                visitar(nodo.getHijos().get(5));
                codigoIntermedio.append("goto ").append(lblInicio).append("\n");
                codigoIntermedio.append(lblFin).append(":\n");

                this.currentBreakLabel = prevBreak;
                return "";

            case "instruccion_break":
                if (this.currentBreakLabel != null) {
                    codigoIntermedio.append("goto ").append(this.currentBreakLabel).append("\n");
                }
                return "";

            case "function_call":
                String idCall = nodo.getHijos().get(0).getLexema();
                if (nodo.getHijos().size() > 3) {
                    visitar(nodo.getHijos().get(2));
                }
                String tempCall = nuevoTemp();
                codigoIntermedio.append(tempCall).append(" = call ").append(idCall).append("\n");
                return tempCall;

            case "listaArgumentos":
                for (NodoArbol hijo : nodo.getHijos()) {
                    if (hijo.getTipo().equals("listaArgumentos")) {
                        visitar(hijo);
                    } else if (!hijo.getTipo().equals("COMMA")) {
                        String arg = visitar(hijo);
                        codigoIntermedio.append("param ").append(arg).append("\n");
                    }
                }
                return "";

            default:
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";
        }
    }

    private String generarOperacionBinaria(NodoArbol nodo, String op) {
        String izq = visitar(nodo.getHijos().get(0));
        String der = visitar(nodo.getHijos().get(1));
        String temp = nuevoTemp();
        codigoIntermedio.append(temp).append(" = ").append(izq).append(" ").append(op).append(" ").append(der).append("\n");
        return temp;
    }

    private String obtenerOpCode(String tipo) {
        switch (tipo) {
            case "DIV_INT": return "/";
            case "MOD": return "%";
            case "POW": return "^";
            default: return tipo;
        }
    }
}