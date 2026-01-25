mkdir -p bin # si no esta
java -jar lib/jflex-full-1.9.1.jar src/jflex.flex # clase del flex
java -jar lib/java-cup-11b.jar -destdir src -parser parser -symbols sym src/cup.cup # parser y sym
javac -d bin -cp "lib/*" src/*.java # compilar
jar cvfm proy2compiladores.jar manifest.txt -C bin . # crear jar
java -jar proy2compiladores.jar base.c # ejecutar