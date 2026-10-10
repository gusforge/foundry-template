FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY foundry-dao/pom.xml foundry-dao/
COPY foundry-service/pom.xml foundry-service/
COPY foundry-web/pom.xml foundry-web/
COPY foundry-start/pom.xml foundry-start/
# 依赖单独成层：只改代码时不会重新下载
RUN mvn -B -q dependency:go-offline
COPY . .
RUN mvn -B -DskipTests -Dspotless.check.skip=true package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 不用 root 跑应用
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /src/foundry-start/target/*.jar app.jar
EXPOSE 8080
# 让 JVM 感知容器内存限制，避免小内存容器里 OOMKilled
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
