FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY gradlew build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon bootJar && \
    cp build/libs/*.jar /sideworks.jar

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 sideworks && \
    useradd --uid 10001 --gid sideworks --no-create-home sideworks && \
    mkdir -p /app/attachments && chown -R sideworks:sideworks /app
WORKDIR /app
COPY --from=build /sideworks.jar /app/sideworks.jar
USER sideworks
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/sideworks.jar"]
