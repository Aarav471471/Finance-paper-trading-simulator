#!/bin/sh
# Starts the virtual screen, the window manager, the VNC server, the web client
# and the app. The app is restarted if someone closes its window.

Xvfb "$DISPLAY" -screen 0 "$SCREEN_SIZE" +extension GLX -nolisten tcp &
sleep 2
openbox &
x11vnc -display "$DISPLAY" -forever -shared -nopw -quiet -rfbport 5900 -localhost &
websockify --web /app/web 7860 localhost:5900 &

while true; do
    java -Dprism.forceGPU=true -Djava.awt.headless=false -jar /app/papertrader.jar
    echo "App closed, restarting in 2 seconds..."
    sleep 2
done
