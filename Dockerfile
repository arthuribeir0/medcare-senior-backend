# syntax=docker/dockerfile:1
# Imagem de produção do backend: compila com o JDK 17 e roda só com o JRE 17, como usuário sem privilégios.
# Os testes rodam na integração contínua antes de publicar a imagem (aqui só o empacotamento).
# Sem segredos na imagem: banco, JWT e Firebase entram por variáveis de ambiente e arquivo montado no contêiner.
# Multiarquitetura (linux/amd64 e linux/arm64): as imagens-base eclipse-temurin publicam as duas.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
# Dependências primeiro: a camada fica em cache enquanto o pom não muda
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/senior-*.jar /build/app.jar

FROM eclipse-temurin:17-jre
# Usuário e grupo de sistema, sem shell de login nem diretório pessoal
RUN groupadd --system medcare && useradd --system --gid medcare --no-create-home --shell /usr/sbin/nologin medcare
WORKDIR /app
COPY --from=build --chown=medcare:medcare /build/app.jar /app/app.jar
USER medcare
EXPOSE 8080
# Heap como fração da memória do contêiner (funciona na VM de 1 GB e na de 24 GB); sai se faltar memória, para o
# Docker reiniciar em vez de seguir quebrado. O fuso padrão da aplicação é America/Sao_Paulo (APP_TIMEZONE).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    TZ=America/Sao_Paulo
# Saúde mínima do actuator ({"status":"UP"}); 503 quando o banco cai. Sem curl/wget: só o bash do sistema.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"UP\"" <&3' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
