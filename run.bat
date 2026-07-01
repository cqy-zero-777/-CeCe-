@echo off
cd /d "%~dp0"
java -Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8 -jar target\demo-0.0.1-SNAPSHOT.jar