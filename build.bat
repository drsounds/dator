@echo off
setlocal
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d bin -cp "lib\jexer-1.6.0.jar;lib\sqlite-jdbc-3.49.1.0.jar" @sources.txt
set RC=%ERRORLEVEL%
del sources.txt
exit /b %RC%
