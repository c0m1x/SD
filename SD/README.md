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
