package com.dictuc.feamass;

public final class TokenDetector {

    public static KeyAndCert autoDetect() {
        try {
            System.out.println("[auto] Probando Windows-MY…");
            return WindowsStoreUtil.findSigningKeyOnWindowsMY();
        } catch (Exception exWin) {
            System.out.println("[auto] Windows-MY no apto: " + exWin.getMessage());
            try {
                String lib = Config.get("pkcs11.lib", null);
                Integer slot = Config.getInt("pkcs11.slot", null);
                if (lib == null) throw new IllegalStateException("Falta pkcs11.lib en config.properties");
                char[] pin = PinPrompt.ask();
                if (pin == null || pin.length == 0) throw new IllegalStateException("PIN cancelado");
                return Pkcs11Util.findOnPkcs11(lib, slot, pin);
            } catch (Exception exPk) {
                throw new IllegalStateException("No se pudo detectar un token/certificado FEA. " +
                        "Windows-MY: " + exWin.getMessage() + " ; PKCS#11: " + exPk.getMessage());
            }
        }
    }
}
