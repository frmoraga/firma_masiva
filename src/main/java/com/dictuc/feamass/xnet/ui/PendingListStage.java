package com.dictuc.feamass.xnet.ui;

import com.dictuc.feamass.ActivityLog;
import com.dictuc.feamass.AppTheme;
import com.dictuc.feamass.KeyAndCert;
import com.dictuc.feamass.PdfSigner;
import com.dictuc.feamass.certs.CertInfo;
import com.dictuc.feamass.certs.CertLoader;
import com.dictuc.feamass.xnet.XnetException;
import com.dictuc.feamass.xnet.XnetSession;
import com.dictuc.feamass.xnet.model.InformeSummary;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Lista de informes pendientes de firma para el usuario autenticado en XNET. */
public final class PendingListStage extends Stage {

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter ETIQUETA_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final Stage appOwner;
    private XnetSession session;
    private final List<CertInfo> cachedCerts;
    private byte[] firmaImagenPropia;

    private final Label lblResumen = new Label();
    private final TableView<InformeSummary> table = new TableView<>();
    private final Button btnRevisar = new Button("Revisar y firmar");
    private final Button btnActualizar = new Button("Actualizar");
    private final Button btnCerrarSesion = new Button("Cerrar sesion");
    private final ProgressIndicator spinner = new ProgressIndicator();

    private final ComboBox<CertInfo> cboCertBulk = new ComboBox<>();
    private final Button btnFirmarSeleccionados = new Button("Firmar seleccionados");
    private final Label lblCertWarningBulk = new Label();
    private final CheckBox chkSelloBulk = new CheckBox("Incluir imagen de mi firma");
    private final ComboBox<PdfSigner.PosicionSello> cboPosicionBulk = new ComboBox<>();
    private final Label lblPosicionBulk = AppTheme.etiqueta("Posicion:");
    private final Label lblSinImagenBulk = new Label("No tienes una imagen de firma configurada (panel Dictuc -> Mi cuenta).");
    private boolean ocupado = false;
    // Compartida con ReviewSignStage: la posicion elegida en cualquiera de las dos ventanas
    // (esta lista o "Revisar y firmar") queda como preferencia para la proxima vez.
    private PdfSigner.PosicionSello posicionPreferida = PdfSigner.PosicionSello.INFERIOR_DERECHA;

    public PendingListStage(Stage appOwner, XnetSession session, List<CertInfo> cachedCerts) {
        this.appOwner = appOwner;
        this.session = session;
        this.cachedCerts = cachedCerts;

        setTitle("XNET - Informes pendientes de firma");
        initOwner(appOwner);
        AppTheme.aplicarIcono(this);

        buildColumns();
        table.setPlaceholder(new Label("Sin informes pendientes."));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<InformeSummary>) c -> actualizarBotones());
        VBox.setVgrow(table, Priority.ALWAYS);

        btnRevisar.getStyleClass().add("button-primario");
        btnRevisar.setDisable(true);
        btnRevisar.setOnAction(e -> abrirRevisionSecuencial());

        btnActualizar.getStyleClass().add("button-secundario");
        btnActualizar.setOnAction(e -> cargarPendientes());

        btnCerrarSesion.getStyleClass().add("button-fantasma");
        btnCerrarSesion.setOnAction(e -> cerrarSesion());

        spinner.setVisible(false);
        spinner.setPrefSize(18, 18);

        lblResumen.getStyleClass().add("titulo-seccion");

        cboCertBulk.setPromptText("Certificado para firma masiva…");
        cboCertBulk.setPrefWidth(340);
        HBox.setHgrow(cboCertBulk, Priority.ALWAYS);
        if (cachedCerts != null) {
            cboCertBulk.setItems(FXCollections.observableArrayList(cachedCerts));
            if (!cachedCerts.isEmpty()) cboCertBulk.getSelectionModel().select(0);
        }
        cboCertBulk.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> actualizarBotones());

        lblCertWarningBulk.getStyleClass().add("texto-error");
        lblCertWarningBulk.setWrapText(true);
        lblCertWarningBulk.setVisible(false);
        lblCertWarningBulk.setManaged(false);

        btnFirmarSeleccionados.getStyleClass().add("button-primario");
        btnFirmarSeleccionados.setDisable(true);
        btnFirmarSeleccionados.setOnAction(e -> firmarSeleccionados());

        HBox rowBulk = new HBox(10, AppTheme.etiqueta("Firma masiva:"), cboCertBulk, btnFirmarSeleccionados);
        rowBulk.setAlignment(Pos.CENTER_LEFT);

        cboPosicionBulk.setItems(FXCollections.observableArrayList(PdfSigner.PosicionSello.values()));
        cboPosicionBulk.getSelectionModel().select(posicionPreferida);
        cboPosicionBulk.setPrefWidth(160);
        cboPosicionBulk.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) posicionPreferida = sel;
        });
        chkSelloBulk.setSelected(false);
        chkSelloBulk.setVisible(false);
        chkSelloBulk.setManaged(false);
        lblPosicionBulk.setVisible(false);
        lblPosicionBulk.setManaged(false);
        cboPosicionBulk.setVisible(false);
        cboPosicionBulk.setManaged(false);
        lblSinImagenBulk.getStyleClass().add("subtitulo");

        HBox rowSelloBulk = new HBox(10, chkSelloBulk, lblPosicionBulk, cboPosicionBulk, lblSinImagenBulk);
        rowSelloBulk.setAlignment(Pos.CENTER_LEFT);

        VBox cardBulk = new VBox(8, rowBulk, rowSelloBulk, lblCertWarningBulk);

        HBox actions = new HBox(10, btnRevisar, btnActualizar, spinner, spacer(), btnCerrarSesion);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(14, lblResumen, table, cardBulk, actions);
        body.setPadding(new Insets(20));
        VBox.setVgrow(body, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(AppTheme.header("XNET", "Sesion de " + session.nombreUsuario()));
        root.setCenter(body);

        Scene scene = new Scene(root, 820, 520);
        AppTheme.apply(scene);
        setScene(scene);

        cargarPendientes();
        cargarFirmaImagenPropia();
    }

    /** Descarga (best-effort) la imagen de firma propia, para ofrecer el sello visual al firmar. */
    private void cargarFirmaImagenPropia() {
        Task<byte[]> task = new Task<>() {
            @Override
            protected byte[] call() throws Exception {
                return session.client().descargarFirmaImagenPropia();
            }
        };
        task.setOnSucceeded(ev -> {
            firmaImagenPropia = task.getValue();
            boolean hayImagen = firmaImagenPropia != null;
            chkSelloBulk.setSelected(hayImagen);
            chkSelloBulk.setVisible(hayImagen);
            chkSelloBulk.setManaged(hayImagen);
            lblPosicionBulk.setVisible(hayImagen);
            lblPosicionBulk.setManaged(hayImagen);
            cboPosicionBulk.setVisible(hayImagen);
            cboPosicionBulk.setManaged(hayImagen);
            lblSinImagenBulk.setVisible(!hayImagen);
            lblSinImagenBulk.setManaged(!hayImagen);
        });
        task.setOnFailed(ev -> {
            // Best-effort: si falla la descarga, se sigue firmando sin sello visual.
            firmaImagenPropia = null;
        });
        new Thread(task, "xnet-firma-imagen").start();
    }

    private static javafx.scene.layout.Region spacer() {
        javafx.scene.layout.Region r = new javafx.scene.layout.Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private void buildColumns() {
        TableColumn<InformeSummary, String> colId = new TableColumn<>("N°");
        colId.setCellValueFactory(cd -> stringProp(String.valueOf(cd.getValue().id)));

        TableColumn<InformeSummary, String> colSolicitante = new TableColumn<>("RUT solicitante");
        colSolicitante.setCellValueFactory(cd -> stringProp(cd.getValue().rutSolicitante));

        TableColumn<InformeSummary, String> colFacturador = new TableColumn<>("RUT facturador");
        colFacturador.setCellValueFactory(cd -> stringProp(cd.getValue().rutFacturador));

        TableColumn<InformeSummary, String> colUnidad = new TableColumn<>("Unidad");
        colUnidad.setCellValueFactory(cd -> stringProp(cd.getValue().unidadNombre));

        TableColumn<InformeSummary, String> colFecha = new TableColumn<>("Fecha creacion");
        colFecha.setCellValueFactory(cd -> {
            var f = cd.getValue().fechaCreacion;
            return stringProp(f == null ? "" : f.format(FECHA_FMT));
        });

        TableColumn<InformeSummary, String> colCategoria = new TableColumn<>("Categoria interna");
        colCategoria.setCellValueFactory(cd -> stringProp(cd.getValue().categoriaInternaNombre));

        TableColumn<InformeSummary, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(cd -> stringProp(cd.getValue().estado));

        // Reparte el ancho disponible entre las columnas en vez de dejarlas en su ancho
        // fijo por defecto (que trunca con "..." aunque sobre espacio a la derecha).
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        colId.setPrefWidth(60);
        colSolicitante.setPrefWidth(120);
        colFacturador.setPrefWidth(120);
        colUnidad.setPrefWidth(140);
        colFecha.setPrefWidth(140);
        colCategoria.setPrefWidth(160);
        colEstado.setPrefWidth(140);

        table.getColumns().setAll(List.of(colId, colSolicitante, colFacturador, colUnidad, colFecha, colCategoria, colEstado));
    }

    private static SimpleStringProperty stringProp(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }

    private void cargarPendientes() {
        setBusy(true);
        Task<List<InformeSummary>> task = new Task<>() {
            @Override
            protected List<InformeSummary> call() throws Exception {
                return session.client().listarPendientesFirma();
            }
        };
        task.setOnSucceeded(ev -> {
            setBusy(false);
            List<InformeSummary> items = task.getValue();
            table.setItems(FXCollections.observableArrayList(items));
            lblResumen.setText("Tienes " + items.size() + " informe(s) pendiente(s) de firma.");
        });
        task.setOnFailed(ev -> {
            setBusy(false);
            handleError(task.getException());
        });
        new Thread(task, "xnet-pendientes").start();
    }

    private void abrirRevisionSecuencial() {
        List<InformeSummary> seleccion = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (seleccion.isEmpty()) return;
        RevisionSecuencial.iniciar(appOwner, session, seleccion, cachedCerts, firmaImagenPropia,
                posicionPreferida, p -> posicionPreferida = p, this::cargarPendientes);
    }

    private void firmarSeleccionados() {
        List<InformeSummary> seleccion = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        CertInfo cert = cboCertBulk.getSelectionModel().getSelectedItem();
        if (seleccion.isEmpty() || cert == null || !cert.esValido()) return;

        // Confirmacion con contenido a la vista: que informes son y que dice cada uno. Antes era
        // solo un dialogo con la cantidad, o sea que se firmaban N documentos sin verlos nunca.
        boolean confirmado = BulkReviewStage.confirmar(this, session, seleccion, cert);
        if (!confirmado) return;

        PdfSigner.SelloVisual selloBase = null;
        if (firmaImagenPropia != null && chkSelloBulk.isSelected()) {
            String etiqueta = session.nombreUsuario() + " - " + LocalDateTime.now().format(ETIQUETA_FMT);
            selloBase = new PdfSigner.SelloVisual(
                    firmaImagenPropia, etiqueta, cboPosicionBulk.getSelectionModel().getSelectedItem(), -1);
        }
        PdfSigner.SelloVisual sello = selloBase;

        setBusy(true);
        lblResumen.setText("Firmando 0 de " + seleccion.size() + "…");

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                List<String> errores = new ArrayList<>();
                int hechos = 0;
                for (InformeSummary inf : seleccion) {
                    try {
                        byte[] pdf = session.client().descargarPdf(inf.id);
                        KeyAndCert kc = CertLoader.toKeyAndCert(cert, null);
                        byte[] firmado = PdfSigner.signToBytes(pdf, kc, sello);
                        session.client().subirFirmado(inf.id, session.csrfToken(), firmado);
                        ActivityLog.registrar("Firma XNET (masiva) OK informe #" + inf.id + " cert=" + cert.alias);
                    } catch (Exception e) {
                        String msg = "Informe #" + inf.id + ": " + e.getMessage();
                        errores.add(msg);
                        ActivityLog.registrar("Firma XNET (masiva) ERROR " + msg);
                    }
                    hechos++;
                    updateMessage("Firmando " + hechos + " de " + seleccion.size() + "…");
                }
                return errores;
            }
        };
        task.messageProperty().addListener((obs, old, msg) -> lblResumen.setText(msg));
        task.setOnSucceeded(ev -> {
            setBusy(false);
            List<String> errores = task.getValue();
            cargarPendientes();
            int ok = seleccion.size() - errores.size();
            if (errores.isEmpty()) {
                AppTheme.alert(Alert.AlertType.INFORMATION, ok + " informe(s) firmado(s) correctamente.");
            } else {
                AppTheme.alert(Alert.AlertType.WARNING,
                        "Se firmaron " + ok + " de " + seleccion.size() + " informe(s).\n\nErrores:\n"
                                + String.join("\n", errores));
            }
        });
        task.setOnFailed(ev -> {
            setBusy(false);
            handleError(task.getException());
        });
        new Thread(task, "xnet-firmar-masivo").start();
    }

    private void cerrarSesion() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    session.client().logout(session.csrfToken());
                } catch (Exception ignore) {
                    // best-effort: si falla igual volvemos al login
                }
                return null;
            }
        };
        task.setOnSucceeded(ev -> volverALogin());
        task.setOnFailed(ev -> volverALogin());
        new Thread(task, "xnet-logout").start();
    }

    private void volverALogin() {
        session = null;
        new LoginStage(appOwner, cachedCerts).show();
        close();
    }

    private void setBusy(boolean busy) {
        ocupado = busy;
        spinner.setVisible(busy);
        btnActualizar.setDisable(busy);
        table.setDisable(busy);
        cboCertBulk.setDisable(busy);
        actualizarBotones();
    }

    private void actualizarBotones() {
        int seleccionados = table.getSelectionModel().getSelectedItems().size();
        btnRevisar.setDisable(ocupado || seleccionados == 0);
        btnRevisar.setText(seleccionados <= 1 ? "Revisar y firmar" : "Revisar y firmar (" + seleccionados + ")");

        CertInfo cert = cboCertBulk.getSelectionModel().getSelectedItem();
        boolean certOk = cert != null && cert.esValido();
        boolean mostrarAviso = cert != null && !certOk;
        lblCertWarningBulk.setText(mostrarAviso ? "Este certificado no se puede usar para firmar: " + cert.validacion.motivo : "");
        lblCertWarningBulk.setVisible(mostrarAviso);
        lblCertWarningBulk.setManaged(mostrarAviso);

        btnFirmarSeleccionados.setDisable(ocupado || seleccionados == 0 || !certOk);
        btnFirmarSeleccionados.setText(seleccionados <= 1
                ? "Firmar seleccionados"
                : "Firmar seleccionados (" + seleccionados + ")");
    }

    private void handleError(Throwable ex) {
        if (ex instanceof XnetException xe && xe.kind() == XnetException.Kind.UNAUTHENTICATED) {
            AppTheme.alert(Alert.AlertType.WARNING, "Tu sesion expiro. Vuelve a iniciar sesion.");
            new LoginStage(appOwner, cachedCerts).show();
            close();
            return;
        }
        AppTheme.alert(Alert.AlertType.ERROR,
                "No fue posible obtener los informes pendientes: " + (ex != null ? ex.getMessage() : "error desconocido"));
    }
}
