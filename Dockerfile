# ========== 阶段 1：构建 ==========
# 用 Maven + JDK17 镜像在容器内编译，无需服务器装 Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY backend/settings.xml /root/.m2/settings.xml
COPY backend/pom.xml .
# 先拉依赖（利用缓存）
RUN mvn -B dependency:go-offline

COPY backend/src ./src
RUN mvn -B clean package -DskipTests

# ========== 阶段 2：运行 ==========
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /build/target/order-app.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8006

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]