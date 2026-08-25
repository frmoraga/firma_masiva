package com.dictuc.feamass.xnet.ui;

import com.dictuc.feamass.AppTheme;
import com.dictuc.feamass.certs.CertInfo;
import com.dictuc.feamass.certs.RutMatcher;
import com.dictuc.feamass.xnet.XnetConfig;
import com.dictuc.feamass.xnet.XnetSession;
import com.dictuc.feamass.xnet.model.InformeSummary;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Confirmacion previa a la firma masiva.
 *
 * Antes de esta ventana la firma masiva se confirmaba con un dialogo de texto que solo decia
 * cuantos informes eran: el usuario terminaba aplicando su FEA a N documentos que nunca vio, y
 * un backend suplantado o comprometido podia hacerle firmar cualquier contenido. Aca se listan
 * exactamente los informes que se van a firmar y se puede abrir la vista previa de cualquiera
 * -la misma que en "Revisar y firmar", ver PdfPreviewPane- sin salir de la ventana.
 *
 * No se obliga a revisar los N documentos (eso anularia el sentido de firmar en lote), pero se
 * muestra cuantos se revisaron para que la decision sea consciente.
 */
final class BulkReviewStage extends Stage {

    /** Cuantos PDF renderizados se conservan en memoria al ir saltando entre informes. */
    private static final int MAX_EN_CACHE = 3;

    private final XnetSession session;
    private final List<InformeSummary> informes;

    private final TableView<InformeSummary> tabla = new TableView<>();
    private final PdfPreviewPane visor = new PdfPreviewPane(520, 420);
    private final Label lblEstado = new Label();
    private final Label lblRevisados = new Label();
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Button btnConfirmar;
    private final Button btnCancelar = new Button("Cancelar");

    private final Set<Long> revisados = new HashSet<>();
    private final Map<Long, List<Image>> cachePaginas = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<Image>> eldest) {
            return size() > MAX_EN_CACHE;
        }
    };

    private Task<?> tareaActual;
    private boolean confirmado = false;

    /**
     * Advertencia visible (no bloqueante) si el certificado elegido no parece corresponder
     * al RUT de la sesion XNET activa -- ver RutMatcher y Hallazgo S-1 de
     * AUDITORIA_SEGURIDAD_FASE2.md. Devuelve un Label vacio (sin ocupar espacio) si coincide
     * o si no se pudo determinar.
     */
    private static Label advertenciaRut(XnetSession session, CertInfo cert) {
        Label lbl = new Label();
        if (cert != null && RutMatcher.pareceNoCoincidir(cert.cert, session.rutUsuario(), session.correoUsuario())) {
            lbl.setText("⚠ Este certificado no parece corresponder a tu sesion ("
                    + session.rutUsuario() + " / " + session.correoUsuario() + "). La firma legal del "
                    + "PDF quedara a nombre de otra persona aunque XNET la registre a tu nombre. "
                    + "Verifica antes de continuar.");
            lbl.setWrapText(true);
            lbl.setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");
        } else {
            lbl.setManaged(false);
            lbl.setVisible(false);
        }
        return lbl;
    }

    /** Abre la confirmacion en modo modal. Devuelve true solo si el usuario acepta firmar. */
    static boolean confirmar(Stage owner, XnetSession session, List<InformeSummary> informes, CertInfo cert) {
        BulkReviewStage ventana = new BulkReviewStage(owner, session, informes, cert);
        ventana.showAndWait();
        return ventana.confirmado;
    }

    private BulkReviewStage(Stage owner, XnetSession session, List<InformeSummary> informes, CertInfo cert) {
        this.session = session;
        this.informes = informes;

        setTitle("XNET - Confirmar firma masiva");
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        AppTheme.aplicarIcono(this);

        construirTabla();
        tabla.setItems(FXCollections.observableArrayList(informes));
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> cargarPrevia(sel));
        tabla.setPrefWidth(360);
        HBox.setHgrow(tabla, Priority.SOMETIMES);

        btnConfirmar = new Button("Firmar " + informes.size() + " informe(s)");
        btnConfirmar.getStyleClass().add("button-primario");
        btnConfirmar.setDefaultButton(true);
        btnConfirmar.setOnAction(e -> {
            confirmado = true;
            cerrar();
        });

        btnCancelar.getStyleClass().add("button-fantasma");
        btnCancelar.setCancelButton(true);
        btnCancelar.setOnAction(e -> cerrar());

        spinner.setVisible(false);
        spinner.setPrefSize(18, 18);
        lblEstado.getStyleClass().add("subtitulo");
        lblEstado.setWrapText(true);
        lblRevisados.getStyleClass().add("subtitulo");

        VBox visorCard = new VBox(8,
                AppTheme.etiqueta("Vista previa del informe seleccionado"),
                visor);
        visorCard.getStyleClass().add("card");
        VBox.setVgrow(visor, Priority.ALWAYS);
        HBox.setHgrow(visorCard, Priority.ALWAYS);

        HBox centro = new HBox(14, tabla, visorCard);
        VBox.setVgrow(centro, Priority.ALWAYS);

        HBox acciones = new HBox(10, lblRevisados, spacer(), spinner, btnCancelar, btnConfirmar);
        acciones.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(12,
                AppTheme.tituloSeccion("Vas a firmar " + informes.size() + " informe(s)"),
                AppTheme.etiqueta("Certificado: " + cert),
                advertenciaRut(session, cert),
                AppTheme.etiqueta("Selecciona un informe de la lista para ver su contenido antes de firmar. "
                        + "Una vez confirmada, la firma se aplica y se sube a XNET informe por informe."),
                centro,
                lblEstado,
                acciones);
        body.setPadding(new Insets(20));
        VBox.setVgrow(body, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(AppTheme.header("XNET", "Confirmar firma masiva"));
        root.setCenter(body);

        Scene scene = new Scene(root, 980, 660);
        PdfPreviewPane.protegerAtajos(scene);
        AppTheme.apply(scene);
        setScene(scene);

        setOnCloseRequest(ev -> soltarRecursos());

        actualizarRevisados();
        if (!informes.isEmpty()) tabla.getSelectionModel().select(0);
    }

    private void construirTabla() {
        TableColumn<InformeSummary, String> colId = new TableColumn<>("N°");
        colId.setCellValueFactory(cd -> texto(String.valueOf(cd.getValue().id)));
        colId.setPrefWidth(60);

        TableColumn<InformeSummary, String> colSolicitante = new TableColumn<>("RUT solicitante");
        colSolicitante.setCellValueFactory(cd -> texto(cd.getValue().rutSolicitante));
        colSolicitante.setPrefWidth(120);

        TableColumn<InformeSummary, String> colUnidad = new TableColumn<>("Unidad");
        colUnidad.setCellValueFactory(cd -> texto(cd.getValue().unidadNombre));
        colUnidad.setPrefWidth(120);

        TableColumn<InformeSummary, String> colRevisado = new TableColumn<>("Revisado");
        colRevisado.setCellValueFactory(cd -> texto(revisados.contains(cd.getValue().id) ? "si" : ""));
        colRevisado.setPrefWidth(70);

        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabla.getColumns().setAll(List.of(colId, colSolicitante, colUnidad, colRevisado));
    }

    private static SimpleStringProperty texto(String valor) {
        return new SimpleStringProperty(valor == null ? "" : valor);
    }

    /** Descarga y renderiza el informe seleccionado (con cache, para volver atras sin re-descargar). */
    private void cargarPrevia(InformeSummary informe) {
        if (informe == null) {
            visor.limpiar();
            return;
        }

        List<Image> enCache = cachePaginas.get(informe.id);
        if (enCache != null) {
            visor.mostrar(enCache);
            estado("");
            return;
        }

        if (tareaActual != null) tareaActual.cancel();
        visor.limpiar();
        ocupado(true, "Descargando informe #" + informe.id + "…");

        Task<List<Image>> task = new Task<>() {
            @Override
            protected List<Image> call() throws Exception {
                byte[] pdf = session.client().descargarPdf(informe.id);
                return PdfPreviewRenderer.renderPages(pdf, XnetConfig.previewDpi());
            }
        };
        tareaActual = task;
        task.setOnSucceeded(ev -> {
            ocupado(false, "");
            cachePaginas.put(informe.id, task.getValue());
            revisados.add(informe.id);
            actualizarRevisados();
            tabla.refresh();
            // Puede haber cambiado la seleccion mientras descargaba: no pisar lo que se ve ahora.
            if (tabla.getSelectionModel().getSelectedItem() == informe) {
                visor.mostrar(task.getValue());
                if (!visor.tienePaginas()) estado("El informe #" + informe.id + " no tiene paginas para mostrar.");
            }
        });
        task.setOnFailed(ev -> {
            ocupado(false, "");
            Throwable ex = task.getException();
            estado("No se pudo mostrar el informe #" + informe.id + ": "
                    + (ex != null ? ex.getMessage() : "error desconocido")
                    + ". Puedes firmar igual, pero no habras revisado este documento.");
        });
        Thread hilo = new Thread(task, "xnet-preview-masiva");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void actualizarRevisados() {
        lblRevisados.setText("Revisados " + revisados.size() + " de " + informes.size() + " informe(s).");
    }

    private void ocupado(boolean ocupado, String mensaje) {
        spinner.setVisible(ocupado);
        estado(mensaje);
    }

    private void estado(String mensaje) {
        lblEstado.setText(mensaje);
    }

    private void cerrar() {
        soltarRecursos();
        close();
    }

    /** El PDF nunca toca disco: al cerrar se sueltan las imagenes y se corta cualquier descarga. */
    private void soltarRecursos() {
        if (tareaActual != null) tareaActual.cancel();
        cachePaginas.clear();
        visor.limpiar();
    }

    private static javafx.scene.layout.Region spacer() {
        javafx.scene.layout.Region r = new javafx.scene.layout.Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}
