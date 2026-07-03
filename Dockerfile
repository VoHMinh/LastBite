FROM eclipse-temurin:21-jdk-jammy AS deps

WORKDIR /build

COPY --chmod=0755 mvnw mvnw
COPY .mvn/ .mvn/

RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -DskipTests

FROM deps AS package

WORKDIR /build

COPY ./src src/
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 \
    ./mvnw test -Dtest=JacksonConfigTest,SecurityConfigTest && \
    ./mvnw package -DskipTests && \
    mv target/$(./mvnw help:evaluate -Dexpression=project.artifactId -q -DforceStdout)-$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout).jar target/app.jar && \
    jar tf target/app.jar | grep -q 'BOOT-INF/classes/com/LastBite/common/config/JacksonConfig.class'

FROM package AS extract

WORKDIR /build

RUN java -Djarmode=layertools -jar target/app.jar extract --destination target/extracted

FROM eclipse-temurin:21-jre-jammy AS final

ARG UID=10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=10.0 -Dfile.encoding=UTF-8"
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser

# Lệnh mẫu để lưu file riêng tư vào container volume nếu cần dùng sau này
# RUN mkdir -p /app/keys && chown appuser:appuser /app/keys
# COPY key/private_key.pem /app/keys/cloudfront-private-key.pem
# RUN chown appuser:appuser /app/keys/cloudfront-private-key.pem && chmod 600 /app/keys/cloudfront-private-key.pem

COPY --from=extract build/target/extracted/dependencies/ ./
COPY --from=extract build/target/extracted/spring-boot-loader/ ./
COPY --from=extract build/target/extracted/snapshot-dependencies/ ./
COPY --from=extract build/target/extracted/application/ ./

EXPOSE 8080

USER appuser

ENTRYPOINT [ "java", "org.springframework.boot.loader.launch.JarLauncher" ]
