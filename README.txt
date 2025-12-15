FEA Mass Signer (Windows 11) - Proyecto Maven
=============================================

Requisitos:
  - JDK 17+ (recomendado JDK 21 x64)
  - Maven 3.8+
  - (Opcional) SafeNet Authentication Client x64, si usarás PKCS#11
  - Token FEA (SafeNet/eToken)

Configuración PKCS#11 (si no detecta por Windows-MY):
  Edita src/main/resources/config.properties y ajusta:
    pkcs11.lib=C:\Windows\System32\eTPKCS11.dll
    pkcs11.slot=0

Comandos:

  Compilar + empaquetar fat-jar:
    mvn -q -DskipTests package

  Ejecutar con JavaFX (en desarrollo):
    mvn -q javafx:run

  Ejecutar jar (GUI):
    java -jar target/feamass-1.0.1-jar-with-dependencies.jar

Notas:
  - Por ahora la firma PDF es un STUB (copia el PDF). Reemplaza el método
    signPdfStub(...) en PdfSigner por tu implementación real (PDFBox + BC).
  - La GUI permite elegir PDF individual o un ZIP con múltiples PDFs.
  - Primero intenta detectar un certificado firmable en Windows-MY; si falla,
    usa PKCS#11 con la DLL indicada en config.properties y solicita PIN.
