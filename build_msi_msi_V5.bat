@echo off
rem ============ CONFIG ============
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PROJ=C:\Users\soporte\Desktop\feamassV3"
set "FXJMODS=C:\javafx-jmods-21.0.9"
set "OUTDIR=%PROJ%\dist"
set "TMPDIR=%PROJ%\jpkg_tmp"
set "APPNAME=Dictuc - Firma Masiva"
set "MAINCLASS=com.dictuc.feamass.MainFx"
set "ICON=assets\icon.ico"

echo === Entrando al proyecto ===
cd /d "%PROJ%" || (echo [ERROR] No existe %PROJ% & exit /b 1)

echo === Buscando uber-jar ===
set "JAR="
for %%F in ("target\feamass-*-jar-with-dependencies.jar") do set "JAR=%%~nxF"

if not defined JAR (
  echo === Compilando con Maven ===
  call mvn -DskipTests clean package || (echo [ERROR] Fallo mvn & exit /b 1)
  set "JAR="
  for %%F in ("target\feamass-*-jar-with-dependencies.jar") do set "JAR=%%~nxF"
)

if not defined JAR (
  echo [ERROR] No encontre target\feamass-*-jar-with-dependencies.jar
  echo Verifica la version en el pom.xml y que el assembly plugin genere el *-jar-with-dependencies.
  exit /b 1
)
echo [OK] JAR: %JAR%

echo === Creando runtime con jlink ===
rmdir /s /q runtime 2>nul
"%JAVA_HOME%\bin\jlink.exe" ^
  --module-path "%JAVA_HOME%\jmods;%FXJMODS%" ^
  --add-modules java.base,javafx.base,javafx.graphics,javafx.controls,javafx.fxml,jdk.crypto.mscapi,jdk.crypto.cryptoki ^
  --strip-debug --no-header-files --no-man-pages ^
  --output runtime
if errorlevel 1 (
  echo [ERROR] Fallo jlink (revisa JAVA_HOME y FXJMODS)
  exit /b 1
)

echo === Generando MSI ===
rmdir /s /q "%TMPDIR%" 2>nul
mkdir "%OUTDIR%" 2>nul

"%JAVA_HOME%\bin\jpackage.exe" ^
  --type msi ^
  --name "%APPNAME%" ^
  --input target ^
  --main-jar "%JAR%" ^
  --main-class %MAINCLASS% ^
  --runtime-image runtime ^
  --icon "%ICON%" ^
  --dest "%OUTDIR%" ^
  --temp "%TMPDIR%" ^
  --win-menu --win-shortcut --win-dir-chooser --win-per-user-install ^
  --description "Software de firma electronica avanzada masiva para Dictuc" ^
  --vendor "DICTUC" ^
  --verbose

if errorlevel 1 (
  echo [ERROR] Fallo jpackage. Revisa "%TMPDIR%" para logs.
  exit /b 1
)

echo.
echo [OK] MSI generado en: "%OUTDIR%"
exit /b 0