# PaperTrader 3D in the browser.
# Runs the Java desktop app on a virtual screen (Xvfb) and streams it to the
# browser with noVNC. Used for the Hugging Face Space, also works locally:
#   docker build -t papertrader . && docker run -p 7860:7860 papertrader
#   then open http://localhost:7860

# ---- 1. Build the runnable jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- 2. Virtual desktop + app ----
# Full JDK image: the JRE image leaves out jdk.unsupported.desktop, which JavaFX-in-Swing needs
FROM eclipse-temurin:17-jdk-jammy
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb x11vnc openbox novnc websockify \
        libgtk-3-0 libgl1 libgl1-mesa-dri libglu1-mesa libxtst6 libxxf86vm1 \
        fonts-dejavu fonts-noto-core \
    && rm -rf /var/lib/apt/lists/*

# Hugging Face runs the container as user 1000
RUN useradd -m -u 1000 user && mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix
COPY --from=build /build/target/paper-trading-simulator-1.0.0.jar /app/papertrader.jar
COPY docker/start.sh /app/start.sh
COPY docker/openbox-rc.xml /home/user/.config/openbox/rc.xml
# noVNC web client with our own landing page that connects straight away
RUN cp -r /usr/share/novnc /app/web && chmod +x /app/start.sh
COPY docker/index.html /app/web/index.html
RUN chown -R user:user /app /home/user

USER user
ENV HOME=/home/user \
    DISPLAY=:1 \
    SCREEN_SIZE=1440x900x24 \
    PAPERTRADER_DATA_DIR=/home/user/data \
    LIBGL_ALWAYS_SOFTWARE=1
WORKDIR /home/user
EXPOSE 7860
CMD ["/app/start.sh"]
