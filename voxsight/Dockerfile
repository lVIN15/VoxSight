# Stage 1: Build the Spring Boot Application
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy Maven POM and source files
COPY pom.xml .

# Download dependencies (caching layer)
RUN mvn dependency:go-offline -B || true

# Copy application source code
COPY src src

# Build executable JAR archive
RUN mvn clean package -DskipTests

# Stage 2: Production Runtime Environment
FROM ubuntu:22.04 AS runner

# Prevent interactive prompts during apt install
ENV DEBIAN_FRONTEND=noninteractive

# Install Java 17, Python 3, Tesseract OCR, and required utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless \
    python3 \
    python3-pip \
    tesseract-ocr \
    tesseract-ocr-eng \
    curl \
    wget \
    unzip \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install Python music21 library
RUN pip3 install --no-cache-dir music21

# Download & Install Audiveris OMR Engine
WORKDIR /tmp
RUN wget -q https://github.com/Audiveris/audiveris/releases/download/v5.3.1/audiveris-5.3.1.zip -O audiveris.zip \
    && unzip -q audiveris.zip -d /opt \
    && rm -f audiveris.zip \
    && mv /opt/audiveris-5.3.1 /opt/audiveris \
    && chmod -R +x /opt/audiveris/bin \
    && (ln -s /opt/audiveris/bin/audiveris /opt/audiveris/bin/Audiveris || true) \
    || mkdir -p /opt/audiveris/bin

# Create application directory
WORKDIR /app

# Create required temporary processing directories
RUN mkdir -p /tmp/uploads /tmp/outputs scripts

# Copy compiled JAR from Builder Stage
COPY --from=builder /build/target/voxsight-0.0.1-SNAPSHOT.jar /app/voxsight.jar

# Copy Python processing scripts
COPY scripts/ /app/scripts/

# Default Environment Variables for Production Container
ENV PORT=8080 \
    AUDIVERIS_PATH=/opt/audiveris/bin/audiveris \
    PYTHON_PATH=/usr/bin/python3 \
    TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata \
    JAVA_OPTS="-Djava.awt.headless=true" \
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true" \
    UPLOADS_DIR=/tmp/uploads \
    OUTPUTS_DIR=/tmp/outputs \
    CORS_ALLOWED_ORIGINS=*

# Expose HTTP port
EXPOSE 8080

# Run Spring Boot backend
ENTRYPOINT ["java", "-jar", "/app/voxsight.jar"]
