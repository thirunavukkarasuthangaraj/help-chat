# help-chat backend — multi-stage build, small runtime image.
# Build:  docker build -t help-chat ./backend
# Run:    docker run -p 8090:8090 -e HELPCHAT_ALLOWED_ORIGINS=https://yoursite.com help-chat
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/chat-service-1.0.0.jar app.jar
# External help-docs folder — mount your .md files here to edit without rebuild
RUN mkdir -p /app/docs
ENV HELPCHAT_DOCS_DIR=/app/docs
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8090/chat/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
