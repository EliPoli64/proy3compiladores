import java.io.*;
import java_cup.runtime.Symbol;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Uso incorrecto. Ejecutar: java Main <archivo_fuente>");
            System.exit(1);
        }

        String fileName = args[0];
        System.out.println("Iniciando análisis del archivo: " + fileName);

        parser p = null;
        try {
            // 1. Crear el Lexer
            LexerProyUno lexer = new LexerProyUno(new InputStreamReader(new FileInputStream(fileName), "UTF-8"));
            
            // 2. Crear el Parser con el Lexer
            p = new parser(lexer);
            
            // 3. Ejecutar el análisis (parse)
            Symbol result = p.parse();
            
            // 4. Si llega aquí, el análisis fue exitoso
            System.out.println("\n------------------------------------------------");
            System.out.println("RESULTADO: El archivo FUE generado exitosamente por la gramática.");
            System.out.println("------------------------------------------------\n");
            
            // 5. Primero mostrar la Tabla de Símbolos (para que salga aunque falle el árbol)
            p.imprimirTablaSimbolos();
            
            String tablaSimbolosContent = p.obtenerTablaSimbolosString();
            guardarEnArchivo("tablaSimbolos.txt", tablaSimbolosContent);
            System.out.println("Tabla de símbolos guardada en: tablaSimbolos.txt");

            // 6. Obtener y mostrar el Árbol Sintáctico
            if (result.value instanceof NodoArbol) {
                NodoArbol raiz = (NodoArbol) result.value;                
                String arbolContent = raiz.getArbolPrettyString();
                System.out.println("Árbol Sintáctico:");
                System.out.println(arbolContent);
                guardarEnArchivo("arbol.txt", arbolContent);
                System.out.println("Árbol sintáctico guardado en: arbol.txt");
            } else {
                System.out.println("Nota: El resultado del parser no es un NodoArbol (es null o de otro tipo).");
            }

        } catch (FileNotFoundException e) {
            System.err.println("Error fatal: Archivo no encontrado - " + fileName);
        } catch (Exception e) {
            System.err.println("\n------------------------------------------------");
            System.err.println("RESULTADO: El archivo NO puede ser generado por la gramática.");
            System.err.println("------------------------------------------------");
            System.err.println("Error encontrado: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\nEjecucion finalizada");
        }
    }

    private static void guardarEnArchivo(String nombreArchivo, String contenido) {
        try (FileWriter writer = new FileWriter(nombreArchivo)) {
            writer.write(contenido);
        } catch (IOException e) {
            System.err.println("Error al guardar archivo " + nombreArchivo + ": " + e.getMessage());
        }
    }
}
