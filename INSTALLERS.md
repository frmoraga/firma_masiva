# Instaladores (MSI y EXE)

Requisitos:
- Windows 11
- JDK 17+ con `jpackage` en PATH (viene con JDK)
- (Opcional) Windows SDK si quieres **firmar** el instalador con `signtool`

## Generar MSI
```bat
build_msi_gui.bat
```
Salida: `FEA Mass Signer-1.0.0.msi` (nombre exacto puede variar).

## Generar EXE
```bat
build_exe_gui.bat
```

## Firma de código (opcional, recomendado)
```bat
sign_installer_example.bat "FEA Mass Signer-1.0.0.msi"
sign_installer_example.bat "FEA Mass Signer-1.0.0.exe"
```
Edita dentro del script la ruta a tu `.pfx`, contraseña y URL de timestamp.
