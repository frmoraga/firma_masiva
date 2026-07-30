package com.dictuc.feamass.tsa;

/**
 * Obtiene un sello de tiempo RFC 3161 sobre los bytes de una firma.
 *
 * Existe como interfaz (en vez de llamar directo al TSA desde PdfSigner) para poder inyectar
 * una implementacion distinta en los tests: asi se puede probar el armado del CMS con el token
 * adjunto sin depender de que un servidor externo real este disponible mientras corre la suite.
 */
public interface TsaClient {

    /**
     * @param bytesDeFirma valor de firma del SignerInfo (encryptedDigest), es decir lo que se
     *                     quiere anclar a una hora confiable.
     * @return token RFC 3161 codificado en DER (un ContentInfo), listo para adjuntarse como
     *         atributo no firmado id-aa-signatureTimeStampToken.
     * @throws Exception si el TSA no responde, responde con error o devuelve algo invalido.
     */
    byte[] obtenerToken(byte[] bytesDeFirma) throws Exception;
}
