@echo off
setlocal
set JAVA_HOME=C:\Program Files\Java\jdk-21
set FX=C:\javafx-sdk-21.0.9\lib
set APP=FEA Mass Signer
set VER=1.0.1
set JAR=target\feamass-%VER%-jar-with-dependencies.jar

echo === Verificando JDK y modulo msc ===
if not exist "%JAVA_HOME%\jmods\jdk.crypto.mscapi.jmod" (
  echo [ERROR] Tu JDK no trae jdk.crypto.mscapi: %JAVA_HOME%\jmods\jdk.crypto.mscapi.jmod no existe
  exit /b 1
)

echo === Compilando JAR (si falta) ===
if not exist "%JAR%" (
  mvn -q -DskipTests package || ( echo [ERROR] Maven fallo & exit /b 1 )
)

echo === Empaquetando MSI ===
jpackage ^
  --type msi ^
  --name "%APP%" ^
  --app-version %VER% ^
  --input target ^
  --main-jar feamass-%VER%-jar-with-dependencies.jar ^
  --main-class com.dictuc.feamass.MainFx ^
  --icon assets\icon.ico ^
  --win-dir-chooser --win-menu --win-shortcut ^
  --module-path "%FX%" ^
  --add-modules java.desktop,javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.swing,jdk.crypto.mscapi ^
  --java-options "--add-opens=java.base/sun.security.util=ALL-UNNAMED" ^
  --java-options "-Dsun.security.mscapi.keyStoreCompatibilityMode=true" ^
  --temp jpkg_tmp

if errorlevel 1 (
  echo [ERROR] jpackage fallo. Revisa jpkg_tmp\* para mas detalle.
  exit /b 1
)

echo [OK] MSI generado en el directorio actual.