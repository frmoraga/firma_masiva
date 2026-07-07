@echo off
setlocal enabledelayedexpansion

REM === RUTAS ===
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PROJ=C:\Users\soporte\Desktop\feamassV2"
set "FXJMODS=C:\javafx-jmods-21.0.9"
set "OUTDIR=%PROJ%\dist"
set "TMPDIR=%PROJ%\jpkg_tmp"

echo === Verificando prerequisitos ===
if not exist "%JAVA_HOME%\bin\jpackage.exe" (
  echo [ERROR] No se encontro jpackage en "%JAVA_HOME%\bin\jpackage.exe"
  exit /b 1
)
if not exist "%JAVA_HOME%\jmods\java.base.jmod" (
  echo [ERROR] No se encontro %JAVA_HOME%\jmods\java.base.jmod
  exit /b 1
)
if not exist "%FXJMODS%\javafx.base.jmod" (
  echo [ERROR] No se encontro %FXJMODS%\javafx.base.jmod
  exit /b 1
)

cd /d "%PROJ%" || (echo [ERROR] No existe %PROJ% & exit /b 1)

echo === Compilando JAR (si falta) ===
if not exist "target\feamass-1.0.1-jar-with-dependencies.jar" (
  mvn -q -DskipTests clean package || (echo [ERROR] Fallo mvn & exit /b 1)
)

echo === Creando runtime con jlink ===
rmdir /s /q runtime 2>nul
"%JAVA_HOME%\bin\jlink.exe" ^
  --module-path "%FXJMODS%;%JAVA_HOME%\jmods" ^
  --add-modules java.base,java.desktop,javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.swing,jdk.crypto.mscapi,jdk.crypto.cryptoki ^
  --strip-debug --no-header-files --no-man-pages ^
  --output runtime || (echo [ERROR] Fallo jlink & exit /b 1)

echo === Ejecutando jpackage (MSI) ===
rmdir /s /q "%TMPDIR%" 2>nul
mkdir "%OUTDIR%" 2>nul

"%JAVA_HOME%\bin\jpackage.exe" ^
  --type msi ^
  --name "FEA Mass Signer" ^
  --app-version 1.0.1 ^
  --input target ^
  --main-jar feamass-1.0.1-jar-with-dependencies.jar ^
  --main-class com.dictuc.feamass.MainFx ^
  --runtime-image runtime ^
  --dest "%OUTDIR%" --temp "%TMPDIR%" ^
  --win-menu --win-shortcut --win-dir-chooser ^
  --win-console || (echo [ERROR] Fallo jpackage (revisar "%TMPDIR%") & exit /b 1)

echo [OK] MSI generado en: "%OUTDIR%"
endlocal