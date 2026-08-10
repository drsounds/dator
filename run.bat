@echo off
java --enable-native-access=ALL-UNNAMED -cp "bin;lib\jexer-1.6.0.jar;lib\sqlite-jdbc-3.49.1.0.jar" se.spacify.dator.Main %*
