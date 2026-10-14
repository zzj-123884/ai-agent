# 多阶段构建：先编译，再只带 JRE 运行，镜像更小
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /app/target/ai-agent-0.0.1-SNAPSHOT.jar app.jar
# 文件型聊天记忆/上传文件需要可写目录（compose 中挂载到 /app/data）
RUN mkdir -p /app/data && chown -R spring:spring /app
USER spring

EXPOSE 8123
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
