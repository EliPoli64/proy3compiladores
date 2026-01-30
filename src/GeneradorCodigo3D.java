import java.util.ArrayList;

public class GeneradorCodigo3D {
    private StringBuilder codigo3D;
    private int tempCounter;
    private int labelCounter;
    private String currentBreakLabel = null;

    public GeneradorCodigo3D() {
        this.codigo3D = new StringBuilder();
        this.tempCounter = 1;
        this.labelCounter = 1;
    }

    private void procesarCondiciones(NodoArbol nodo, String labelSalida) {
         if (nodo.getHijos().get(0).getTipo().equals("condicionesDecide")) {
             // Procesar recursivamente las anteriores
             procesarCondiciones(nodo.getHijos().get(0), labelSalida);
             
             // Hijo 1 es "condicionDecide"
             NodoArbol condNode = nodo.getHijos().get(1);
             procesarUnicaCondicion(condNode, labelSalida);
         } else {
             // Caso base: Hijo 0 es "condicionDecide"
             NodoArbol condNode = nodo.getHijos().get(0);
             procesarUnicaCondicion(condNode, labelSalida);
         }
    }
    
    private void procesarUnicaCondicion(NodoArbol nodo, String labelSalida) {
        // Estructura de condicionDecide: expr ARROW bloque
        NodoArbol expr = nodo.getHijos().get(0);
        NodoArbol bloque = nodo.getHijos().get(2);
        generarCondicionIf(expr, bloque, labelSalida);
    }

    private void generarCondicionIf(NodoArbol expr, NodoArbol bloque, String labelSalida) {
        // Generamos dos etiquetas, una para el caso verdadero y otra para el caso falso
        String lblTrue = nuevaEtiqueta();
        String lblFalse = nuevaEtiqueta();
        
        String condVal = visitar(expr); // Acá se genera el código 3D de la expresión condicional (esta puede ser compuesta).
        // Generamos el código 3D. Este posee la siguiente estructura:
        
        // if (condición) goto lblTrue
        // goto lblFalse
        // lblTrue:
        // código 3D del bloque...
        // goto labelSalida
        // lblFalse:
        
        codigo3D.append("if ").append(condVal).append(" goto ").append(lblTrue).append("\n");
        codigo3D.append("goto ").append(lblFalse).append("\n");
        
        codigo3D.append(lblTrue).append(":\n");
        visitar(bloque);
        codigo3D.append("goto ").append(labelSalida).append("\n");
        
        codigo3D.append(lblFalse).append(":\n");
    }

    public String generar(NodoArbol raiz) {
        visitar(raiz);
        return codigo3D.toString();
    }

    private String nuevoTemp() {
        return "t" + (tempCounter++);
    }

    private String nuevaEtiqueta() {
        return "L" + (labelCounter++);
    }

    // Este es el metodo encargado de recorrer recursivamente el arbol.
    // Se podría decir que es el core del generador de codigo 3D.
    private String visitar(NodoArbol nodo) {
        if (nodo == null) return "";

        String tipo = nodo.getTipo();

        // Se basa en un switch case que analiza el tipo del nodo que analiza.
        // Dependiendo del tipo, la generación del código 3D varía.
        switch (tipo) {
            // Cuando se trata de bloques de código pasamos directamente a analizar sus hijos.
            case "program":
            case "globales":
            case "funciones":
            case "bloque":
            case "listaInstr":
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";

            case "navidad": // Cuando se detecta el main, lo declaramos en el código 3D y pasamos a analizar sus hijos.
                // Esta declaración puede ser importante a la hora de convertir a MIPS.
                codigo3D.append("\nmain:\n");
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";

            case "funcion": // Acá tenemos un approach similar al de navidad (main).
                // Declaramos la función en el código 3D y pasamos a analizar sus hijos.
                String nombreFunc = nodo.getHijos().get(2).getLexema();
                codigo3D.append("\nfunc_").append(nombreFunc).append(":\n");
                visitar(nodo.getHijos().get(6)); // Visitar bloque
                return "";
                // Acá hace falta el manejo de parámetros si es necesario para el stack frame.
            
            case "funcion_vacia": //La función vacía no tiene bloque, por lo que simplemente la declaramos en el código 3D.
                 String nombreFuncVacia = nodo.getHijos().get(0).getLexema();
                 codigo3D.append("\nfunc_").append(nombreFuncVacia).append(":\n");
                 return "";

            case "instruccion": // Cuando encontramos instrucciones no las declaramos. Esta declaración se hará en la expresión contenida en la instrucción.
            case "instruccion_endl":
                for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";
            
            // Se declaran los shows y los returns. "print ????" para show y "return ????" para return
            case "instruccion_show": 
                String resShow = visitar(nodo.getHijos().get(1));
                codigo3D.append("print ").append(resShow).append("\n");
                return "";

            case "instruccion_return":
                String resRet = visitar(nodo.getHijos().get(1));
                codigo3D.append("return ").append(resRet).append("\n");
                return "";
            


            case "declaracionVariable_local":
                // Esta declaración no genera código 3D, solo reserva espacio, pero en 3D simple no hacemos nada
                return "";

            case "declaracionVariable_local_asign":
                // Acá sí generamos código 3D, este es el caso: LOCAL tipo IDENTIFIER ASSIGN expression
                String id = nodo.getHijos().get(2).getLexema(); // Primero obtenemos el identificador
                String val = visitar(nodo.getHijos().get(4)); // Luego obtenemos el valor
                codigo3D.append(id).append(" = ").append(val).append("\n"); // Después generamos el código 3D
                return id;

            case "=": // Este case se encarga de la asignación de valores a variables.
                // Por ejemplo: var1 = 1 + 2 - 3 * 4 / 5 o también var1 = var2 + var3 y por ende a = tempX también cae en este case.
                // Misma estructura del case de arriba.
                String idAssign = nodo.getHijos().get(0).getLexema(); // Obtenemos el identificador
                String valAssign = visitar(nodo.getHijos().get(1)); // Obtenemos el valor
                codigo3D.append(idAssign).append(" = ").append(valAssign).append("\n"); // Generamos el código 3D
                return idAssign;

            // Acá tenemos las operaciones binarias. Acá está toda la lógica de la división para el código 3D.
            // Tanto las aritméticas como las relacionales y lógicas llaman a generarOperacionBinaria para hacer la partición de la expresión.
            case "+":
            case "-":
            case "*":
            case "/": // división flotante
            case "//": // división entera
            case "%":
            case "^":
                return generarOperacionBinaria(nodo, obtenerOpCode(tipo));

            // Operaciones Relacionales (Retornan temporal con 1 o 0, o sirven para saltos)
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

            case "MINUS": // Operación unaria: MINUS expression
                String operando = visitar(nodo.getHijos().get(0));
                String temp = nuevoTemp();
                codigo3D.append(temp).append(" = -").append(operando).append("\n");
                return temp;

            case "++_pre": // ++
                String idInc = nodo.getHijos().get(0).getLexema();
                codigo3D.append(idInc).append(" = ").append(idInc).append(" + 1\n");
                return idInc;
            
            case "--_pre": // --
                String idDec = nodo.getHijos().get(0).getLexema();
                codigo3D.append(idDec).append(" = ").append(idDec).append(" - 1\n");
                return idDec;

            case "()": // Pasamos a sus hijos directamente.
                return visitar(nodo.getHijos().get(1));

            // Cuando llegamos a las hojas retornamos el lexema. La siguiente iteración recursiva llega al caso default donde retornamos "".
            case "int_literal":
            case "float_literal":
            case "bool_literal":
            case "char_literal":
            case "string_literal":
            case "IDENTIFIER":
                return nodo.getLexema();

            // Acá llegamos a las estructuras de control. En esta generación de 3D intenamos ayudarnos para cuando hagamos la traducción a MIPS.
            // Por eso agregamos etiquetas y saltos.
            case "decide": // DECIDE OF condicionesDecide END DECIDE
                String labelSalida = nuevaEtiqueta(); // Primero generamos la etiqueta de salida
                procesarCondiciones(nodo.getHijos().get(2), labelSalida); // Este método se encarga de generar el código para las condiciones y el bloque de código
                codigo3D.append(labelSalida).append(":\n"); // Agregamos la etiqueta de salida
                return "";
            
            case "decide_with_else": // DECIDE OF condicionesDecide ELSE ARROW bloque END DECIDE
                // Este case sigue la misma lógica que el anterior. La única diferencia es que agregamos un bloque ELSE luego
                // de procesar el bloque "decide".
                String labelSalidaElse = nuevaEtiqueta();
                procesarCondiciones(nodo.getHijos().get(2), labelSalidaElse);
                
                // Justo después del 3D decide, agregamos el bloque ELSE.
                visitar(nodo.getHijos().get(5)); // Bloque ELSE
                codigo3D.append(labelSalidaElse).append(":\n");
                return "";

            case "loop": // LOOP listaInstr EXIT WHEN expression ENDL END LOOP ENDL
                String labelInicio = nuevaEtiqueta();
                String labelSalir = nuevaEtiqueta(); // Este label es para salir del loop
                
                codigo3D.append(labelInicio).append(":\n");
                // Guardamos el label de break anterior. Esto para soportar loops anidados.
                String labelAnterior = this.currentBreakLabel;
                this.currentBreakLabel = labelSalir;
                
                visitar(nodo.getHijos().get(1)); // Acá visitamos el bloque de instrucciones del loop
                
                String condExit = visitar(nodo.getHijos().get(4)); // Acá visitamos la condición de salida del loop
                codigo3D.append("if not ").append(condExit).append(" goto ").append(labelSalir).append("\n"); // Si la condición es verdadera, salimos del loop
                
                codigo3D.append("goto ").append(labelInicio).append("\n"); // Si la condición es falsa, volvemos al inicio del loop
                codigo3D.append(labelSalir).append(":\n"); // Etiqueta de salida del loop

                // Este 3D tiene la siguiente estructura:
                // labelInicio:
                //     instrucciones
                //     if condExit goto labelSalir
                //     goto labelInicio
                // labelSalir:
                
                this.currentBreakLabel = labelAnterior; // Restauramos el label de break anterior
                return "";

            case "for_stmt": // Parecido al loop, pero con una condición de inicio.
                String lblInicio = nuevaEtiqueta();
                String lblFin = nuevaEtiqueta();
                
                String prevBreak = this.currentBreakLabel;
                this.currentBreakLabel = lblFin;

                visitar(nodo.getHijos().get(2)); // Init
                
                codigo3D.append(lblInicio).append(":\n");
                String condFor = visitar(nodo.getHijos().get(3)); // Obtenemos la condición del for (Index 3)
                codigo3D.append("if not ").append(condFor).append(" goto ").append(lblFin).append("\n");
                
                visitar(nodo.getHijos().get(7)); // Visitamos el bloque del for (Index 7)
                visitar(nodo.getHijos().get(5)); // Manejamos el incremento (Index 5)
                
                codigo3D.append("goto ").append(lblInicio).append("\n");
                codigo3D.append(lblFin).append(":\n");

                this.currentBreakLabel = prevBreak;
                return "";
            
            case "instruccion_break": // Este case es para el break de las estructuras de control (loop y for)
                if (this.currentBreakLabel != null) {
                    codigo3D.append("goto ").append(this.currentBreakLabel).append("\n");
                } else {
                    codigo3D.append("; Error: Break fuera de loop\n");
                }
                return "";

            case "function_call": // Este case es para las llamadas a funciones
                String idCall = nodo.getHijos().get(0).getLexema(); // Obtenemos el nombre de la función
                // Argumentos
                if (nodo.getHijos().size() > 3) { // Si tiene argumentos entonces los visitamos
                    visitar(nodo.getHijos().get(2));
                }
                String tempCall = nuevoTemp(); // Le asignamos un nuevo temporal a la llamada
                codigo3D.append(tempCall).append(" = call ").append(idCall).append("\n"); // Agregamos el código 3D de la llamada
                return tempCall;

            case "listaArgumentos":
                // Aca tomamos la lista de parametros a una llamada y los agregamos al código 3D uno por uno
                for (NodoArbol hijo : nodo.getHijos()) {
                    if (hijo.getTipo().equals("listaArgumentos")) { // Llamada recursiva
                        visitar(hijo);
                    } else if(hijo.getTipo().equals("COMMA")) { 
                        continue;
                    } else {
                        String arg = visitar(hijo);
                        codigo3D.append("param ").append(arg).append("\n");
                    }
                }
                return "";

            default:
                // Si no reconocemos, visitamos hijos por defecto
                 for (NodoArbol hijo : nodo.getHijos()) {
                    visitar(hijo);
                }
                return "";
        }
    }

    // Este es el método que utilizan las operaciones binarias para generar el código 3D.
    // Se pasa como parámetros el nodo del árbol y el operador (aritmético, relacional o lógico).
    private String generarOperacionBinaria(NodoArbol nodo, String op) {
        // Se extrae el hijo izquierdo y derecho del nodo.
        String izq = visitar(nodo.getHijos().get(0));
        String der = visitar(nodo.getHijos().get(1));
        String temp = nuevoTemp(); // Se genera un nuevo temporal.
        codigo3D.append(temp).append(" = ").append(izq).append(" ").append(op).append(" ").append(der).append("\n"); // Se agrega la operación al código 3D.
        return temp; // Se retorna el temporal para que pueda ser utilizado en la siguiente operación.
    }

    private String obtenerOpCode(String tipo) { // Esto se añadió para agregar colocar operadores en vez de nombres en el 3D
        switch (tipo) {
            case "DIV_INT": return "/";
            case "MOD": return "%";
            case "POW": return "^";
            default: return tipo;
        }
    }
}
