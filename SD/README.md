Como Correr o Projeto
1️--Compilar (Maven multi-module)

mvn clean install



Executar o Servidor
```markdown
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
```

## Executar os testes

Executar todos os testes do módulo `server` (rápido):

```bash
cd /home/prancha/Documentos/3ano/1s/SD/SD/SD
mvn -pl server -DtrimStackTrace=false test
```

Executar testes específicos (ex.: apenas `LimitsTest`):

```bash
mvn -pl server -DtrimStackTrace=false -Dtest=LimitsTest#testDayLimit test
```

Executar múltiplos testes específicos:

```bash
mvn -pl server -DtrimStackTrace=false -Dtest=SerializationSpecTest,ScalabilitySpecTest test
```

Onde os resultados e logs são escritos:

- Logs e artefactos de benchmarks são gravados em `~/sd-test-logs/` (ex.: `perf-*.csv`, `serialization-*.txt`, `workload-mix-*.log`).
- Relatórios do Maven Surefire ficam em `server/target/surefire-reports/`.

Notas:

- Os testes adicionados usam classes do módulo `server` (p.ex. `SeriesMemoryManager`, `SeriesPersistence`).
- Se alterar o código fonte, recompile antes de executar testes: `mvn -pl server -DtrimStackTrace=false -DskipTests=false test`.
