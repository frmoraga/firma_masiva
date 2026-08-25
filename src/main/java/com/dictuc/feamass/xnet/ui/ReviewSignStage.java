package com.dictuc.feamass.xnet.ui;

import com.dictuc.feamass.ActivityLog;
import com.dictuc.feamass.AppTheme;
import com.dictuc.feamass.KeyAndCert;
import com.dictuc.feamass.PdfSigner;
import com.dictuc.feamass.certs.CertInfo;
import com.dictuc.feamass.certs.CertLoader;
import com.dictuc.feamass.certs.RutMatcher;
import com.dictuc.feamass.xnet.XnetConfig;
import com.dictuc.feamass.xnet.XnetException;
import com.dictuc.feamass.xnet.XnetSession;
import com.dictuc.feamass.xnet.model.InformeSummary;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Revision (vista previa en memoria, sin guardar/imprimir/copiar), firma y rechazo de un
 * informe descargado desde XNET. El PDF original y el firmado solo existen como bytes en
 * memoria: nunca se escriben a disco ni quedan accesibles al usuario.
 */
public final class ReviewSignStage extends Stage {

    /** Que decidio el usuario sobre este informe -- solo se reporta si realmente hubo decision. */
    public enum Resultado { FIRMADO, RECHAZADO }

    private static final DateTimeFormatter ETIQUETA_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final Stage appOwner;
    private final XnetSession session;
    private final InformeSummary informe;
    private final byte[] firmaImagenPropia;
    private final Consumer<Resultado> onDone;

    private byte[] originalPdfBytes;

    /** Visor compartido con la confirmacion de firma masiva (ver PdfPreviewPane). */
    private final PdfPreviewPane visor = new PdfPreviewPane(680, 480);
    private final ComboBox<CertInfo> cboCerts = new ComboBox<>();
    private final CheckBox chkSello = new CheckBox("Incluir imagen de mi firma en el PDF");
    private final ComboBox<PdfSigner.PosicionSello> cboPosicionSello = new ComboBox<>();
    private final Button btnFirmar = new Button("Firmar y enviar");
    private final Button btnRechazar = new Button("Rechazar…");
    private final Button btnCancelar = new Button("Cancelar");
    private final Label lblStatus = new Label();
    private final Label lblCertWarning = new Label();
    private final ProgressIndicator spinner = new ProgressIndicator();

    private Task<?> currentTask;
    // true solo mientras firmarYSubir()/rechazarInforme() tienen una request mutante en vuelo
    // (nunca durante la carga inicial de la vista previa, que es segura de abandonar). Cancelar
    // un Task cuyo call() esta bloqueado en una llamada HTTP sincrona no "desenvia" una request
    // que el servidor ya recibio y proceso -- por eso la X nativa de la ventana no puede
    // simplemente cerrar en este estado, o el usuario queda sin saber si la firma/rechazo
    // realmente se aplico, y RevisionSecuencial trataria el cierre como una cancelacion
    // silenciosa, cortando el resto del lote sin avisar.
    private boolean accionEnCurso = false;
    private final java.util.function.Consumer<PdfSigner.PosicionSello> onPosicionCambiada;

    public ReviewSignStage(Stage appOwner, XnetSession session, InformeSummary informe,
                            List<CertInfo> cachedCerts, byte[] firmaImagenPropia,
                            PdfSigner.PosicionSello posicionInicial,
                            java.util.function.Consumer<PdfSigner.PosicionSello> onPosicionCambiada,
                            Consumer<Resultado> onDone) {
        this.appOwner = appOwner;
        this.session = session;
        this.informe = informe;
        this.firmaImagenPropia = firmaImagenPropia;
        this.onPosicionCambiada = onPosicionCambiada;
        this.onDone = onDone;

        setTitle("XNET - Revisar y firmar informe #" + informe.id);
        initOwner(appOwner);
        AppTheme.aplicarIcono(this);

        cboCerts.setPromptText("Selecciona un certificado…");
        cboCerts.setPrefWidth(380);
        HBox.setHgrow(cboCerts, Priority.ALWAYS);
        if (cachedCerts != null) {
            cboCerts.setItems(FXCollections.observableArrayList(cachedCerts));
            if (!cachedCerts.isEmpty()) cboCerts.getSelectionModel().select(0);
        }
        cboCerts.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> actualizarBotones());

        lblCertWarning.getStyleClass().add("texto-error");
        lblCertWarning.setWrapText(true);
        lblCertWarning.setVisible(false);
        lblCertWarning.setManaged(false);

        btnFirmar.getStyleClass().add("button-primario");
        btnFirmar.setDefaultButton(true);
        btnFirmar.setDisable(true);
        btnFirmar.setOnAction(e -> firmarYSubir());

        btnRechazar.getStyleClass().add("button-peligro");
        btnRechazar.setDisable(true);
        btnRechazar.setOnAction(e -> rechazarInforme());

        btnCancelar.getStyleClass().add("button-fantasma");
        btnCancelar.setOnAction(e -> close());

        spinner.setVisible(true);
        spinner.setPrefSize(18, 18);
        lblStatus.setWrapText(true);
        lblStatus.getStyleClass().add("texto-error");

        HBox firmaRow = new HBox(10, AppTheme.etiqueta("Certificado:"), cboCerts, btnFirmar, btnRechazar, btnCancelar, spinner);
        firmaRow.setAlignment(Pos.CENTER_LEFT);

        cboPosicionSello.setItems(FXCollections.observableArrayList(PdfSigner.PosicionSello.values()));
        cboPosicionSello.getSelectionModel().select(posicionInicial != null ? posicionInicial : PdfSigner.PosicionSello.INFERIOR_DERECHA);
        cboPosicionSello.setPrefWidth(160);
        cboPosicionSello.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null && onPosicionCambiada != null) onPosicionCambiada.accept(sel);
        });

        HBox selloRow;
        if (firmaImagenPropia != null) {
            chkSello.setSelected(true);
            selloRow = new HBox(10, chkSello, AppTheme.etiqueta("Posicion:"), cboPosicionSello);
        } else {
            Label sinImagen = AppTheme.etiqueta(
                    "No tienes una imagen de firma configurada (panel Dictuc -> Mi cuenta).");
            selloRow = new HBox(10, sinImagen);
        }
        selloRow.setAlignment(Pos.CENTER_LEFT);

        VBox previewCard = new VBox(10, visor);
        previewCard.getStyleClass().add("card");
        VBox.setVgrow(previewCard, Priority.ALWAYS);
        VBox.setVgrow(visor, Priority.ALWAYS);

        VBox bottom = new VBox(12, selloRow, firmaRow, lblCertWarning, lblStatus);
        bottom.setPadding(new Insets(4, 0, 0, 0));

        Label header = AppTheme.etiqueta("Solicitante " + informe.rutSolicitante + " · " + informe.unidadNombre);

        VBox body = new VBox(14,
                AppTheme.tituloSeccion("Informe #" + informe.id),
                header,
                previewCard,
                bottom);
        body.setPadding(new Insets(20));
        VBox.setVgrow(body, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(AppTheme.header("XNET", "Revisar y firmar"));
        root.setCenter(body);

        Scene scene = new Scene(root, 780, 720);
        // Defensa en profundidad: bloquear atajos de guardar/imprimir/copiar sobre la vista previa.
        PdfPreviewPane.protegerAtajos(scene);
        AppTheme.apply(scene);
        setScene(scene);

        setOnCloseRequest(ev -> {
            if (accionEnCurso) {
                ev.consume();
                AppTheme.alert(Alert.AlertType.WARNING,
                        "Espera a que termine de firmar/rechazar antes de cerrar esta ventana.");
                return;
            }
            if (currentTask != null) currentTask.cancel();
            originalPdfBytes = null;
            visor.limpiar();
        });

        cargarYRenderizar();
    }

    private void cargarYRenderizar() {
        setBusy(true, "Descargando y preparando vista previa…");
        Task<List<Image>> task = new Task<>() {
            @Override
            protected List<Image> call() throws Exception {
                byte[] bytes = session.client().descargarPdf(informe.id);
                originalPdfBytes = bytes;
                return PdfPreviewRenderer.renderPages(bytes, XnetConfig.previewDpi());
            }
        };
        currentTask = task;
        task.setOnSucceeded(ev -> {
            visor.mostrar(task.getValue());
            setBusy(false, "");
            if (!visor.tienePaginas()) {
                lblStatus.setText("El informe no tiene paginas para mostrar.");
            } else {
                actualizarBotones();
            }
        });
        task.setOnFailed(ev -> {
            setBusy(false, "");
            handleError(task.getException(), false);
        });
        Thread hilo = new Thread(task, "xnet-preview");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void firmarYSubir() {
        CertInfo sel = cboCerts.getSelectionModel().getSelectedItem();
        if (sel == null) {
            lblStatus.setText("Debes seleccionar un certificado.");
            return;
        }
        if (!sel.esValido()) {
            lblStatus.setText("Certificado no utilizable: " + sel.validacion.motivo);
            return;
        }
        if (originalPdfBytes == null) {
            lblStatus.setText("El informe aun no ha terminado de cargarse.");
            return;
        }

        String advertenciaRut = RutMatcher.pareceNoCoincidir(sel.cert, session.rutUsuario(), session.correoUsuario())
                ? "\n\n⚠ ADVERTENCIA: este certificado no parece corresponder a tu sesion ("
                        + session.rutUsuario() + " / " + session.correoUsuario() + "). XNET registrara "
                        + "esta firma a tu nombre igual, aunque la firma legal del PDF quedara a nombre "
                        + "de otra persona. Verifica el certificado antes de continuar."
                : "";
        boolean confirmado = AppTheme.confirmar(
                "Vas a firmar el informe #" + informe.id + " (solicitante " + informe.rutSolicitante + ")\n"
                        + "con el certificado:\n" + sel + advertenciaRut + "\n\n¿Confirmas la firma?");
        if (!confirmado) {
            return;
        }

        PdfSigner.SelloVisual sello = null;
        if (firmaImagenPropia != null && chkSello.isSelected()) {
            String etiqueta = session.nombreUsuario() + " - " + LocalDateTime.now().format(ETIQUETA_FMT);
            sello = new PdfSigner.SelloVisual(
                    firmaImagenPropia, etiqueta, cboPosicionSello.getSelectionModel().getSelectedItem(), -1);
        }
        PdfSigner.SelloVisual selloFinal = sello;

        setBusy(true, "Firmando y enviando a XNET…");
        accionEnCurso = true;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                KeyAndCert kc = CertLoader.toKeyAndCert(sel, null);
                byte[] signed = PdfSigner.signToBytes(originalPdfBytes, kc, selloFinal);
                session.client().subirFirmado(informe.id, session.csrfToken(), signed);
                return null;
            }
        };
        currentTask = task;
        task.setOnSucceeded(ev -> {
            accionEnCurso = false;
            setBusy(false, "");
            originalPdfBytes = null;
            visor.limpiar();
            ActivityLog.registrar("Firma XNET OK informe #" + informe.id + " cert=" + sel.alias);
            onDone.accept(Resultado.FIRMADO);
            close();
        });
        task.setOnFailed(ev -> {
            accionEnCurso = false;
            setBusy(false, "");
            Throwable ex = task.getException();
            ActivityLog.registrar("Firma XNET ERROR informe #" + informe.id + " cert=" + sel.alias
                    + " " + (ex != null ? ex.getMessage() : ""));
            handleError(ex, true);
        });
        Thread hilo = new Thread(task, "xnet-firmar");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void rechazarInforme() {
        if (originalPdfBytes == null) {
            lblStatus.setText("El informe aun no ha terminado de cargarse.");
            return;
        }

        var justificacion = AppTheme.pedirTexto(
                "Rechazar informe #" + informe.id,
                "Explica el motivo del rechazo (por ejemplo, un error encontrado en el PDF):");
        if (justificacion.isEmpty()) {
            return;
        }
        String motivo = justificacion.get();

        setBusy(true, "Rechazando y notificando a XNET…");
        accionEnCurso = true;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                session.client().rechazar(informe.id, session.csrfToken(), motivo);
                return null;
            }
        };
        currentTask = task;
        task.setOnSucceeded(ev -> {
            accionEnCurso = false;
            setBusy(false, "");
            originalPdfBytes = null;
            visor.limpiar();
            ActivityLog.registrar("Rechazo XNET OK informe #" + informe.id);
            onDone.accept(Resultado.RECHAZADO);
            close();
        });
        task.setOnFailed(ev -> {
            accionEnCurso = false;
            setBusy(false, "");
            Throwable ex = task.getException();
            ActivityLog.registrar("Rechazo XNET ERROR informe #" + informe.id
                    + " " + (ex != null ? ex.getMessage() : ""));
            handleError(ex, true);
        });
        Thread hilo = new Thread(task, "xnet-rechazar");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void setBusy(boolean busy, String status) {
        spinner.setVisible(busy);
        actualizarBotones();
        btnCancelar.setDisable(busy);
        lblStatus.getStyleClass().setAll(busy ? "subtitulo" : "texto-error");
        lblStatus.setText(status);
    }

    private void actualizarBotones() {
        CertInfo sel = cboCerts.getSelectionModel().getSelectedItem();
        boolean certOk = sel != null && sel.esValido();
        boolean mostrarAviso = sel != null && !certOk;
        lblCertWarning.setText(mostrarAviso ? "Este certificado no se puede usar para firmar: " + sel.validacion.motivo : "");
        lblCertWarning.setVisible(mostrarAviso);
        lblCertWarning.setManaged(mostrarAviso);

        boolean ocupado = spinner.isVisible();
        boolean hayPdf = visor.tienePaginas();
        btnFirmar.setDisable(ocupado || !hayPdf || !certOk);
        btnRechazar.setDisable(ocupado || !hayPdf);
    }

    private void handleError(Throwable ex, boolean duringAccion) {
        if (ex instanceof XnetException xe) {
            if (xe.kind() == XnetException.Kind.UNAUTHENTICATED) {
                AppTheme.alert(Alert.AlertType.WARNING, "Tu sesion expiro. Vuelve a iniciar sesion.");
                close();
                return;
            }
            if (duringAccion && xe.kind() == XnetException.Kind.CONFLICT) {
                AppTheme.alert(Alert.AlertType.WARNING, "Este informe ya no esta disponible para firmar o rechazar (fue firmado, rechazado o modificado por otra persona).");
                close();
                return;
            }
        }
        lblStatus.setText(ex != null ? ex.getMessage() : "Ocurrio un error inesperado.");
    }
}
