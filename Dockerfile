# syntax=docker/dockerfile:1

# 后端镜像：构建阶段用 Maven 打包，运行阶段装 JRE + LiteParse CLI（PDF 解析依赖）
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# 先只拷贝 pom，尽量复用依赖下载缓存
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package \
    && mkdir -p /workspace/out \
    && cp target/*.jar /workspace/out/app.jar

FROM node:20-bookworm-slim

# LiteParse 的 Linux 二进制依赖字体库，PDF 渲染需要
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-17-jre-headless \
        curl \
        ca-certificates \
        tzdata \
        libfontconfig1 \
        libfreetype6 \
        libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# 后端通过 lit 命令解析 PDF（file.parsing.pdf.engine=liteparse）
RUN npm config set registry https://registry.npmmirror.com \
    && npm install -g @llamaindex/liteparse@2.18.0

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8

WORKDIR /app

COPY --from=build /workspace/out/app.jar /app/app.jar
RUN mkdir -p /app/logs

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java -Xms512m -Xmx1g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs -XX:+ExitOnOutOfMemoryError $JAVA_OPTS -jar /app/app.jar"]
