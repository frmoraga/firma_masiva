@echo off
setlocal
set JAVA_HOME=C:\Program Files\Java\jdk-21
set FX=C:\javafx-sdk-21.0.9\lib
set APP=FEA Mass Signer
set VER=1.0.1
set JAR=target\feamass-%VER%-jar-with-dependencies.jar

echo === Verificando JDK ===
if not exist "%JAVA_HOME%\bin\jpackage.exe" ( echo [ERROR] jpackage no esta en %JAVA_HOME% & exit /b 1 )
if not exist "%JAVA_HOME%\jmods\jdk.crypto.mscapi.jmod" ( echo [ERROR] Tu JDK no tiene jdk.crypto.mscapi.jmod & exit /b 1 )

echo === Compilando JAR (si falta) ===
if not exist "%JAR%" (
  mvn -q -DskipTests package || ( echo [ERROR] Maven fallo & exit /b 1 )
)

rem ---- Opción 1: generar app-image para probar antes del MSI ----
set OUT=appimg
rmdir /s /q %OUT% 2>nul
jpackage ^
  --type msi ^
  --name "FEA Mass Signer" ^
  --app-version 1.0.1 ^
  --input target ^
  --main-jar feamass-1.0.1-jar-with-dependencies.jar ^
  --main-class com.dictuc.feamass.MainFx ^
  --module-path "%FX%" ^
  --add-modules java.se,javafx.controls,javafx.fxml,javafx.graphics,jdk.crypto.mscapi ^
  --dest %OUT% ^
  --java-options "-Dprism.order=sw" ^
  --java-options "-Dprism.verbose=true" ^
  --java-options "--add-opens=java.base/sun.security.util=ALL-UNNAMED" ^
  --java-options "-Dsun.security.mscapi.keyStoreCompatibilityMode=true" ^
  --win-menu --win-shortcut --win-dir-chooser ^
  --win-console

if errorlevel 1 ( echo [ERROR] app-image fallo & exit /b 1 )

echo.
echo === Probar app-image ===
"%OUT%\%APP%\%APP%.exe"
echo (si abre, seguimos con el MSI)

rem ---- Opción 2: generar MSI final ----
jpackage ^
  --type msi ^
  --name "%APP%" ^
  --app-version %VER% ^
  --input target ^
  --main-jar feamass-%VER%-jar-with-dependencies.jar ^
  --main-class com.dictuc.feamass.MainFx ^
  --module-path "%FX%" ^
  --add-modules java.se,javafx.controls,javafx.fxml,jdk.crypto.mscapi ^
  --java-options "--add-opens=java.base/sun.security.util=ALL-UNNAMED" ^
  --java-options "-Dsun.security.mscapi.keyStoreCompatibilityMode=true" ^
  --win-menu --win-shortcut --win-dir-chooser ^
  --win-console ^
  --temp jpkg_tmp