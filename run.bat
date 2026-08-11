@echo off
REM Runs directly in this console (ANSI rendering) by default.
REM Pass --swing to use a separate Swing window instead: run.bat --swing
REM Pass a db path to open a specific database: run.bat mydb.sqlite
java --enable-native-access=ALL-UNNAMED -cp "bin;lib\jexer-1.6.0.jar;lib\sqlite-jdbc-3.49.1.0.jar" se.spacify.dator.Main %*
