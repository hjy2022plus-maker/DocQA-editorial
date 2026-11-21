# syntax=docker/dockerfile:1

# 后端镜像：Maven 打包，Java 17 运行。PDFBox 直接提取文本层。
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# 先只拷贝 pom，尽量复用依赖下载缓存
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package \
    && mkdir -p /workspace/out \
    && cp target/*.jar /workspace/out/app.jar

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        tzdata \
    && rm -rf /var/lib/apt/lists/*

# 旧版的 Node / LiteParse / OCR 安装不再执行。

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

WORKDIR /app

COPY --from=build /workspace/out/app.jar /app/app.jar
RUN mkdir -p /app/logs

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java -Xms512m -Xmx1g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -XX:+ExitOnOutOfMemoryError $JAVA_OPTS -jar /app/app.jar"]
