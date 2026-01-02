Como Correr o Projeto
1️--Compilar (Maven multi-module)

mvn clean install



Executar o Servidor
# Terminal 1
cd server
mvn exec:java -Dexec.mainClass="uminho.grupo57.Main"

# Ou com parâmetros: porta maxDays maxSeries
mvn exec:java -Dexec.mainClass="uminho.grupo57.Main" -Dexec.args="8080 30 100"




Executar o Cliente UI
# Terminal 2
cd client-ui
mvn exec:java -Dexec.mainClass="uminho.grupo57.Main"



Gerar Comentários
mvn clean javadoc:aggregate -DskipTests

Abrir o HTML 

xdg-open ~/SD/target/site/apidocs/index.html


exemplo xdg-open /home/prancha/Documentos/3ano/1s/SD/SD/SD/target/site/apidocs/index.html