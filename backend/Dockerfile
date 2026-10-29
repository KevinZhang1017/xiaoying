# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# 可选：Maven 镜像源（国内可用 https://maven.aliyun.com/repository/public）
ARG MAVEN_MIRROR=
RUN if [ -n "$MAVEN_MIRROR" ]; then mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>custom</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR" > /root/.m2/settings.xml; fi
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# 测试在 CI 中执行，镜像构建只打包
RUN mvn -B -ntp -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app && mkdir -p /data/files && chown -R app /data
COPY --from=build /src/target/mockstudio-backend.jar app.jar
USER app
ENV STORAGE_ROOT=/data/files \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
