@echo off
setlocal enabledelayedexpansion

rem ====== RUTAS / VERSIONES ======
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
set "JLINK=%JAVA_HOME%\bin\jlink.exe"
set "WIXLIGHT=C:\Program Files (x86)\WiX Toolset v3.14\bin\light.exe"
set "WIXCANDLE=C:\ Program Files (x86)\WiX Toolset v3.14\bin\candle.exe"

rem JavaFX (JMODS, NO jar)
set "FX_JMODS=C:\javafx-jmods-21.0.9"

rem Proyecto
set "APP_NAME=FEA Mass Signer"
set "APP_VER=1.0.1"
set "MAIN_CLASS=com.dictuc.feamass.MainFx"
set "MAIN_JAR=feamass-1.0.1-jar-with-dependencies.jar"

rem Salida / temporales / log
set "DEST=dist"
set "TMP_DIR=jpkg_tmp"
set "LOG=dist\jpackage.log"
set "FINAL_MSI=FEA-Mass-Signer-%APP_VER%.msi"  rem nombre sin espacios

echo === Verificando herramientas ===
if not exist "%JPACKAGE%" echo [ERROR] No se encontro jpackage "%JPACKAGE%" & exit /b 1
if not exist "%WIXLIGHT%" echo [ERROR] No se encontro WiX (light.exe). Instala WiX 3.14+ & exit /b 1
if not exist "%FX_JMODS%\javafx.base.jmod" echo [ERROR] No se encontro %FX_JMODS%\javafx.base.jmod & exit /b 1

echo === Compilando JAR (si falta) ===
if not exist "target\%MAIN_JAR%" (
  mvn -q -DskipTests package || ( echo [ERROR] Maven fallo & exit /b 1 )
)

echo === Preparando carpetas ===
if not exist "%DEST%" mkdir "%DEST%"
if exist "%TMP_DIR%" rmdir /s /q "%TMP_DIR%" >nul 2>&1
if exist runtime rmdir /s /q runtime >nul 2>&1

echo === Creando runtime con jlink ===
"%JLINK%" ^
  --module-path "%JAVA_HOME%\jmods;%FX_JMODS%" ^
  --add-modules java.base,java.logging,java.xml,java.desktop,javafx.base,javafx.graphics,javafx.controls,javafx.fxml ^
  --strip-debug --no-man-pages --no-header-files --compress=2 ^
  --output runtime || ( echo [ERROR] jlink fallo & exit /b 1 )

echo === Ejecutando jpackage (MSI) ===
set "ICON_FLAG=--icon assets\icon.ico"
if not exist assets\icon.ico set "ICON_FLAG="

"%JPACKAGE%" --verbose ^
  --type msi ^
  --name "%APP_NAME%" ^
  --app-version %APP_VER% ^
  --input target ^
  --main-jar %MAIN_JAR% ^
  --main-class %MAIN_CLASS% ^
  --runtime-image runtime ^
  --dest "%DEST%" ^
  --temp "%TMP_DIR%" ^
  --win-dir-chooser --win-menu --win-shortcut ^
 > "%LOG%" 2>&1

if errorlevel 1 (
  echo [ERROR] jpackage fallo. Revisa "%LOG%"
  exit /b 1
)

echo === Buscando MSI generado ===
set "BUILT_MSI="
for %%F in ("%DEST%\*.msi") do set "BUILT_MSI=%%~fF"

if not defined BUILT_MSI (
  echo [ERROR] No se encontro ningun MSI en "%DEST%".
  echo Revisa: "%LOG%" y los temporales "%TMP_DIR%".
  echo Tambien revisa Seguridad de Windows -> Historial de proteccion (puede haberlo puesto en Cuarentena).
  exit /b 1
)

rem Renombrar a nombre fijo (opcional)
echo MSI encontrado: "%BUILT_MSI%"
if exist "%DEST%\%FINAL_MSI%" del /q "%DEST%\%FINAL_MSI%"
move /y "%BUILT_MSI%" "%DEST%\%FINAL_MSI%" >nul

echo.
echo [OK] MSI listo: "%CD%\%DEST%\%FINAL_MSI%"
echo Log: "%CD%\%LOG%"
exit /b 0