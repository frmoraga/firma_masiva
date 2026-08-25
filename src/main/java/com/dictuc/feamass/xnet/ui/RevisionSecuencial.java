package com.dictuc.feamass.xnet.ui;

import com.dictuc.feamass.AppTheme;
import com.dictuc.feamass.PdfSigner;
import com.dictuc.feamass.certs.CertInfo;
import com.dictuc.feamass.xnet.XnetSession;
import com.dictuc.feamass.xnet.model.InformeSummary;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Encadena ReviewSignStage por cada informe de una seleccion: al firmar o rechazar uno, abre
 * automaticamente el siguiente, hasta agotar la cola. Con una seleccion de un solo informe el
 * comportamiento visible es identico al de abrir un unico ReviewSignStage (mismo aviso
 * individual dentro de esa ventana, mismo refresco final) -- no hay un camino de codigo
 * separado para "uno" vs "varios".
 *
 * Cerrar o cancelar una ventana de la cadena (sin firmar ni rechazar) la detiene ahi: no se
 * abren mas ventanas, pero igual se dispara el refresco final, via Stage.onHidden -- ese evento
 * de JavaFX se dispara siempre que la ventana se oculta, sea por close() tras una decision o por
 * cerrar/cancelar, a diferencia de onCloseRequest (que solo dispara con el boton X nativo).
 */
final class RevisionSecuencial {

    private final Stage appOwner;
    private final XnetSession session;
    private final Deque<InformeSummary> cola;
    private final int totalSeleccionado;
    private final List<CertInfo> cachedCerts;
    private final byte[] firmaImagenPropia;
    private final Consumer<PdfSigner.PosicionSello> onPosicionCambiada;
    private final Runnable alTerminar;

    private PdfSigner.PosicionSello posicion;
    private int firmados = 0;
    private int rechazados = 0;

    static void iniciar(Stage appOwner, XnetSession session, List<InformeSummary> seleccion,
                         List<CertInfo> cachedCerts, byte[] firmaImagenPropia,
                         PdfSigner.PosicionSello posicionInicial,
                         Consumer<PdfSigner.PosicionSello> onPosicionCambiada,
                         Runnable alTerminar) {
        new RevisionSecuencial(appOwner, session, seleccion, cachedCerts, firmaImagenPropia,
                posicionInicial, onPosicionCambiada, alTerminar).avanzar();
    }

    private RevisionSecuencial(Stage appOwner, XnetSession session, List<InformeSummary> seleccion,
                                List<CertInfo> cachedCerts, byte[] firmaImagenPropia,
                                PdfSigner.PosicionSello posicionInicial,
                                Consumer<PdfSigner.PosicionSello> onPosicionCambiada,
                                Runnable alTerminar) {
        this.appOwner = appOwner;
        this.session = session;
        this.cola = new ArrayDeque<>(seleccion);
        this.totalSeleccionado = seleccion.size();
        this.cachedCerts = cachedCerts;
        this.firmaImagenPropia = firmaImagenPropia;
        this.posicion = posicionInicial;
        this.onPosicionCambiada = onPosicionCambiada;
        this.alTerminar = alTerminar;
    }

    private void avanzar() {
        InformeSummary siguiente = cola.poll();
        if (siguiente == null) {
            finalizar();
            return;
        }

        boolean[] decidido = {false};
        ReviewSignStage stage = new ReviewSignStage(appOwner, session, siguiente, cachedCerts, firmaImagenPropia,
                posicion,
                p -> {
                    posicion = p;
                    onPosicionCambiada.accept(p);
                },
                resultado -> {
                    decidido[0] = true;
                    if (resultado == ReviewSignStage.Resultado.FIRMADO) firmados++; else rechazados++;
                });
        // onHidden (no onCloseRequest) captura TODO cierre: exito, cancelar, X, o sesion expirada.
        stage.setOnHidden(ev -> {
            if (decidido[0]) {
                avanzar();
            } else {
                finalizar();
            }
        });
        stage.show();
    }

    private void finalizar() {
        // Solo el resumen final si de verdad fue un lote (>1): para una seleccion de 1 informe,
        // ReviewSignStage ya no muestra nada especifico y no hace falta un segundo popup.
        if (totalSeleccionado > 1) {
            int pendientes = totalSeleccionado - firmados - rechazados;
            String mensaje = firmados + " informe(s) firmado(s), " + rechazados + " rechazado(s).";
            // Sin este aviso, si la cadena se corta en el primer informe (sesion expirada,
            // usuario cierra esa ventana) firmados+rechazados queda en 0 y el usuario no se
            // entera de que el resto de la seleccion nunca se llego a revisar -- parece que
            // "no paso nada" en vez de que el lote se detuvo a mitad de camino.
            if (pendientes > 0) {
                mensaje += "\n" + pendientes + " informe(s) seleccionados no se revisaron "
                        + "(la revision se detuvo antes de completar el lote).";
            }
            AppTheme.alert(Alert.AlertType.INFORMATION, mensaje);
        }
        alTerminar.run();
    }
}
