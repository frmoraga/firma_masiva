package com.dictuc.feamass;

import com.dictuc.feamass.certs.CertInfo;
import com.dictuc.feamass.certs.CertLoader;
import com.dictuc.feamass.certs.RutMatcher;

import java.util.List;

/**
 * Herramienta de diagnostico, NO parte del flujo normal de la app: lista los certificados
 * visibles en el almacen Windows-MY de esta maquina e imprime su Subject DN completo, sin
 * tocar ninguna clave privada ni firmar nada. Sirve para calibrar RutMatcher (Hallazgo S-1,
 * AUDITORIA_SEGURIDAD_FASE2.md) contra el formato real que usa la CA del token conectado --
 * confirmar si el RUT aparece en el DN y en que forma, antes de decidir si conviene pasar de
 * "advertencia" a "bloqueo duro" al firmar con un certificado que no coincide con la sesion.
 *
 * Uso: mvn exec:java -Dexec.mainClass=com.dictuc.feamass.DiagnosticoCertificados
 */
public final class DiagnosticoCertificados {
    private DiagnosticoCertificados() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Certificados visibles en Windows-MY ===");
        List<CertInfo> certs = CertLoader.fromWindowsMY();
        if (certs.isEmpty()) {
            System.out.println("(ninguno encontrado -- el token puede no estar registrado como proveedor CSP/CNG de Windows)");
            return;
        }
        for (CertInfo ci : certs) {
            String dn = ci.cert != null ? ci.cert.getSubjectX500Principal().getName() : "(sin certificado)";
            System.out.println("\nAlias: " + ci.alias);
            System.out.println("Subject DN completo: " + dn);
            System.out.println("Valido para firmar (CertValidation): " + ci.esValido()
                    + (ci.esValido() ? "" : " -- motivo: " + ci.validacion.motivo));
            List<String> candidatosRut = RutMatcher.candidatosEn(dn);
            System.out.println("RUT-like candidatos que RutMatcher extrae del DN: "
                    + (candidatosRut.isEmpty() ? "NINGUNO" : candidatosRut));
            List<String> candidatosEmail = RutMatcher.emailsEn(dn);
            System.out.println("Correo(s) que RutMatcher extrae del DN: "
                    + (candidatosEmail.isEmpty() ? "NINGUNO" : candidatosEmail));
        }
        System.out.println("\n=== Fin ===");
        System.out.println("Compara el/los candidato(s) de arriba contra el RUT real de la persona dueña de este certificado.");
        System.out.println("Si coincide (normalizado, sin puntos ni guion): la heuristica de RutMatcher funciona para este emisor.");
    }
}
