@echo off
setlocal
set JAVA_HOME=C:\Program Files\Java\jdk-21
set FX=C:\javafx-sdk-21.0.9\lib
set APP=FEA Mass Signer
set VER=1.0.1
set JAR=target\feamass-%VER%-jar-with-dependencies.jar
set STAGE=target\jpackage-input

echo === Verificando JDK y modulo msc ===
if not exist "%JAVA_HOME%\jmods\jdk.crypto.mscapi.jmod" (
  echo [ERROR] Tu JDK no trae jdk.crypto.mscapi: %JAVA_HOME%\jmods\jdk.crypto.mscapi.jmod no existe
  exit /b 1
)

echo === Compilando JAR (si falta) ===
if not exist "%JAR%" (
  mvn -q -DskipTests package || ( echo [ERROR] Maven fallo & exit /b 1 )
)

rem jpackage copia TODO lo que haya en --input dentro de la app instalada. Apuntarlo a target\
rem metia en el instalador classes\, maven-status\, generated-sources\, archive-tmp\ y una
rem segunda copia del jar sin dependencias: peso muerto y codigo de la app duplicado en disco
rem del cliente. Por eso se arma una carpeta con EXCLUSIVAMENTE el fat jar.
echo === Preparando carpeta de entrada limpia para jpackage ===
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%" || ( echo [ERROR] No se pudo crear %STAGE% & exit /b 1 )
copy /y "%JAR%" "%STAGE%\" >nul || ( echo [ERROR] No se pudo copiar %JAR% a %STAGE% & exit /b 1 )

rem java.net.http es obligatorio en --add-modules: lo usan el cliente XNET y el sellado de tiempo
rem RFC 3161 (HttpTsaClient). Faltaba aca -el workflow de CI si lo trae-, y al ser un modulo que
rem jlink no incluye solo, la app empaquetada moria al conectarse. Si se agrega otra dependencia
rem de la JDK, agregarla en LOS DOS lados (aqui y .github/workflows/ci.yml).
rem
rem OJO --add-opens=java.base/sun.security.util: parece un resto de un intento antiguo de hablar
rem con MSCAPI por clases internas. Hoy NO hay nada en el codigo que use ese paquete (CertLoader
rem usa solo API publica: Security.getProvider("SunMSCAPI") + KeyStore "Windows-MY"), asi que se
rem ve eliminable. No se quita a ciegas: si algo de la cadena SunMSCAPI lo necesitara en runtime,
rem la app empaquetada fallaria al listar certificados y ningun test unitario lo detectaria (no
rem tocan Windows-MY ni hardware). Para sacarlo: generar el MSI sin esa linea y probar a firmar
rem con el certificado/token real en una maquina limpia.
echo === Empaquetando MSI ===
jpackage ^
  --type msi ^
  --name "%APP%" ^
  --app-version %VER% ^
  --input "%STAGE%" ^
  --main-jar feamass-%VER%-jar-with-dependencies.jar ^
  --main-class com.dictuc.feamass.MainFx ^
  --icon assets\icon.ico ^
  --win-dir-chooser --win-menu --win-shortcut ^
  --module-path "%FX%" ^
  --add-modules java.desktop,java.net.http,javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.swing,jdk.crypto.mscapi ^
  --java-options "--add-opens=java.base/sun.security.util=ALL-UNNAMED" ^
  --java-options "-Dsun.security.mscapi.keyStoreCompatibilityMode=true" ^
  --temp jpkg_tmp

if errorlevel 1 (
  echo [ERROR] jpackage fallo. Revisa jpkg_tmp\* para mas detalle.
  exit /b 1
)

echo [OK] MSI generado en el directorio actual.