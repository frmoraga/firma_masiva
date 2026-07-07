package com.dictuc.feamass;

import com.dictuc.feamass.certs.CertInfo;
import com.dictuc.feamass.certs.CertLoader;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class MainFx extends Application {

    // ===== Campos UI (vista principal) =====
    private TextField txtInput;
    private TextField txtOutput;
    private Button btnBrowseIn;
    private Button btnBrowseOut;
    private Button btnSign;

    private RadioButton rbPdf;
    private RadioButton rbZip;

    private ComboBox<CertInfo> cboCerts;
    private Button btnRefreshCerts;
    private Label lblCertWarning;

    private TextArea log;
    private ProgressBar progressBar; // para mostrar progreso en firma / refresh

    // ===== Estado =====
    private List<CertInfo> cachedCerts;
	private File lastInputDir;

    // ===== Config =====
    private static final boolean AUTOFILL_OUTPUT = true;

    // Si usas PKCS#11, setea este flag/archivo cuando corresponda (o déjalo en false/null)
    private static final boolean USE_PKCS11 = false;
    private static final Path PKCS11_CFG = null; // p.ej. Paths.get("pkcs11.cfg");

    @Override
    public void start(Stage stage) {
        stage.setTitle("Dictuc - Firma Masiva");
        showSplashAndScan(stage);
    }

    // ---------- SPLASH + SCAN INICIAL ----------
    private void showSplashAndScan(Stage stage) {
        // UI de splash
        var root = new BorderPane();
        root.getStyleClass().add("root");
        root.setPadding(new Insets(24));

        var title = new Label("Inicializando…");
        title.getStyleClass().add("titulo-seccion");

        var msg = new Label("Buscando certificados (Windows / Token)…");
        msg.getStyleClass().add("subtitulo");
        msg.setWrapText(true);

        var spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        var card = new VBox(14, title, msg, spinner);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(360);
        card.setMaxHeight(VBox.USE_PREF_SIZE);

        var box = new StackPane(card);
        root.setCenter(box);

		stage.setResizable(false);

        var scene = new Scene(root, 640, 360);
        AppTheme.apply(scene);
        stage.setScene(scene);
        stage.initStyle(StageStyle.DECORATED);
        AppTheme.aplicarIcono(stage);
        stage.show();

        // Tarea en segundo plano para no congelar la UI
        Task<List<CertInfo>> scanTask = new Task<>() {
            @Override
            protected List<CertInfo> call() throws Exception {
                updateMessage("Leyendo almacén Windows-MY…");
                // Escaneo inicial: Windows-MY y (opcional) PKCS#11
                return CertLoader.listAll(true, USE_PKCS11, PKCS11_CFG);
            }
        };

        msg.textProperty().bind(scanTask.messageProperty());

        scanTask.setOnSucceeded(ev -> {
            cachedCerts = scanTask.getValue();
            // Construye la UI principal y puebla el combo
            buildAndShowMain(stage, cachedCerts);
        });

        scanTask.setOnFailed(ev -> {
            Throwable ex = scanTask.getException();
            ex.printStackTrace();
            showFatal("No fue posible cargar certificados.\n" +
                    (ex != null ? ex.getMessage() : "Error desconocido"));
        });

        Thread t = new Thread(scanTask, "cert-scan-startup");
        t.setDaemon(true);
        t.start();
    }

    // ---------- VENTANA PRINCIPAL ----------
    private void buildAndShowMain(Stage stage, List<CertInfo> initialCerts) {
        // ======= Controles de archivo =======
        txtInput = new TextField();
        txtInput.setPromptText("Archivo PDF o ZIP a firmar…");
        txtInput.setPrefColumnCount(40);
        HBox.setHgrow(txtInput, Priority.ALWAYS);

        btnBrowseIn = new Button("Examinar…");
        btnBrowseIn.getStyleClass().add("button-secundario");
        btnBrowseIn.setOnAction(e -> chooseInput());

        txtOutput = new TextField();
        txtOutput.setPromptText("Archivo de salida (se sugiere automáticamente)…");
        txtOutput.setPrefColumnCount(40);
        HBox.setHgrow(txtOutput, Priority.ALWAYS);

        btnBrowseOut = new Button("Destino…");
        btnBrowseOut.getStyleClass().add("button-secundario");
        btnBrowseOut.setOnAction(e -> chooseOutput());

        HBox rowIn = new HBox(10, AppTheme.etiqueta("Entrada:"), txtInput, btnBrowseIn);
        rowIn.setAlignment(Pos.CENTER_LEFT);

        HBox rowOut = new HBox(10, AppTheme.etiqueta("Salida:"), txtOutput, btnBrowseOut);
        rowOut.setAlignment(Pos.CENTER_LEFT);

        // ======= Modo (PDF único o ZIP) =======
        ToggleGroup tg = new ToggleGroup();
        rbPdf = new RadioButton("Un PDF");
        rbZip = new RadioButton("Un ZIP (varios PDFs)");
		rbZip.setSelected(true);
        rbPdf.setToggleGroup(tg);
        rbZip.setToggleGroup(tg);

        HBox rowMode = new HBox(20, AppTheme.etiqueta("Modo:"), rbPdf, rbZip);
        rowMode.setAlignment(Pos.CENTER_LEFT);

        VBox cardDocumento = new VBox(12,
                AppTheme.tituloSeccion("Documento a firmar"),
                rowMode, rowIn, rowOut);
        cardDocumento.getStyleClass().add("card");

        // ======= Certificados =======
        cboCerts = new ComboBox<>();
        cboCerts.setPrefWidth(600);
        cboCerts.setPromptText("Seleccione un certificado…");
        HBox.setHgrow(cboCerts, Priority.ALWAYS);

        lblCertWarning = new Label();
        lblCertWarning.getStyleClass().add("texto-error");
        lblCertWarning.setWrapText(true);
        lblCertWarning.setVisible(false);
        lblCertWarning.setManaged(false);

        if (initialCerts != null) {
            cboCerts.setItems(FXCollections.observableArrayList(initialCerts));
            if (!initialCerts.isEmpty()) cboCerts.getSelectionModel().select(0);
        }
        cboCerts.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> actualizarAvisoCertificado(sel));
        actualizarAvisoCertificado(cboCerts.getSelectionModel().getSelectedItem());

        btnRefreshCerts = new Button("Detectar certificados");
        btnRefreshCerts.getStyleClass().add("button-secundario");
        btnRefreshCerts.setOnAction(e -> refreshCertsAsync());

        HBox rowCert = new HBox(10, AppTheme.etiqueta("Certificado:"), cboCerts, btnRefreshCerts);
        rowCert.setAlignment(Pos.CENTER_LEFT);

        VBox cardCertificado = new VBox(12,
                AppTheme.tituloSeccion("Certificado de firma"),
                rowCert, lblCertWarning);
        cardCertificado.getStyleClass().add("card");

        // ======= Log =======
        log = new TextArea();
        log.setEditable(false);
        log.setPrefRowCount(10);
        VBox.setVgrow(log, Priority.ALWAYS);

        VBox cardLog = new VBox(10, AppTheme.tituloSeccion("Registro de actividad"), log);
        cardLog.getStyleClass().add("card");
        VBox.setVgrow(cardLog, Priority.ALWAYS);

        // ======= Acciones =======
        btnSign = new Button("Firmar");
        btnSign.getStyleClass().add("button-primario");
        btnSign.setDefaultButton(true);
        btnSign.setOnAction(e -> onSign());
        actualizarAvisoCertificado(cboCerts.getSelectionModel().getSelectedItem());

        Button btnXnet = new Button("Firmar vía XNET…");
        btnXnet.getStyleClass().add("button-secundario");
        btnXnet.setOnAction(e -> new com.dictuc.feamass.xnet.ui.XnetFlow(stage, cachedCerts).start());

        Button btnExit = new Button("Salir");
        btnExit.getStyleClass().add("button-fantasma");
        btnExit.setOnAction(e -> Platform.exit());

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(progressBar, Priority.ALWAYS);

        HBox rowActions = new HBox(10, progressBar, btnExit, btnXnet, btnSign);
        rowActions.setAlignment(Pos.CENTER_RIGHT);

        // ======= Encabezado =======
        HBox header = AppTheme.header("Firma Masiva", "Dictuc · Firma Electrónica Avanzada");

        // ======= Layout raíz =======
        VBox body = new VBox(16, cardDocumento, cardCertificado, cardLog, rowActions);
        body.setPadding(new Insets(20));
        VBox.setVgrow(body, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(body);

        Scene scene = new Scene(root, 960, 640);
        AppTheme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("Dictuc - Firma Masiva");
        stage.show();
    }

    // ---------- Acciones UI ----------
    private void chooseInput() {
		FileChooser fc = new FileChooser();
		fc.setTitle("Seleccionar entrada");

		// Si ya tenemos un directorio usado antes, partir desde ahí
		if (lastInputDir != null && lastInputDir.isDirectory()) {
			try {
				fc.setInitialDirectory(lastInputDir);
			} catch (Exception ignored) {
				// Por si hay problemas de permisos o algo raro
			}
		}

		if (rbPdf.isSelected()) {
			fc.getExtensionFilters().setAll(
					new FileChooser.ExtensionFilter("PDF", "*.pdf")
			);
		} else {
			fc.getExtensionFilters().setAll(
					new FileChooser.ExtensionFilter("ZIP", "*.zip")
			);
		}

		File f = fc.showOpenDialog(getPrimaryStage());
		if (f != null) {
			// Guardar ruta seleccionada
			txtInput.setText(f.getAbsolutePath());

			// Recordar el directorio para la próxima vez
			File parent = f.getParentFile();
			if (parent != null && parent.isDirectory()) {
				lastInputDir = parent;
			}

			if (AUTOFILL_OUTPUT) {
				String out = suggestOutputPath(f, rbPdf.isSelected());
				txtOutput.setText(out);
			}
		}
	}

    private void chooseOutput() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar salida");

        if (rbPdf.isSelected()) {
            fc.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("PDF firmado", "*.pdf"));
        } else {
            fc.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("ZIP firmado", "*.zip"));
        }

        // sugerencia de nombre
        File cur = txtOutput.getText() == null || txtOutput.getText().isBlank()
                ? null
                : new File(txtOutput.getText());
        if (cur != null && cur.getParentFile() != null && cur.getParentFile().isDirectory()) {
            fc.setInitialDirectory(cur.getParentFile());
            fc.setInitialFileName(cur.getName());
        }

        File f = fc.showSaveDialog(getPrimaryStage());
        if (f != null) txtOutput.setText(f.getAbsolutePath());
    }

    private void onSign() {
        String inPath = txtInput.getText();
        String outPath = txtOutput.getText();
        if (inPath == null || inPath.isBlank()) {
            appendLog("[ERROR] Debes seleccionar el archivo de entrada.");
            return;
        }
        if (outPath == null || outPath.isBlank()) {
            appendLog("[ERROR] Debes indicar el archivo de salida.");
            return;
        }
        File in = new File(inPath);
        File out = new File(outPath);

        CertInfo sel = cboCerts.getSelectionModel().getSelectedItem();
        if (sel == null) {
            appendLog("[ERROR] Debes seleccionar un certificado.");
            return;
        }
        if (!sel.esValido()) {
            appendLog("[ERROR] Certificado no utilizable: " + sel.validacion.motivo);
            return;
        }

        String resumen = rbPdf.isSelected()
                ? "Vas a firmar el PDF:\n" + in.getName()
                : "Vas a firmar los PDFs contenidos en el ZIP:\n" + in.getName();
        boolean confirmado = AppTheme.confirmar(resumen + "\n\nCertificado: " + sel + "\n\n¿Confirmas la firma?");
        if (!confirmado) {
            appendLog("[*] Firma cancelada por el usuario.");
            return;
        }

        // Firma en 2do plano para no congelar UI
        Task<Void> signTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(-1, 1); // indeterminado
                try {
                    appendLog("[*] Preparando clave/certificado…");
                    KeyAndCert kc = CertLoader.toKeyAndCert(sel, null); // pide PIN si lo necesitas

                    if (rbPdf.isSelected()) {
                        appendLog("[*] Firmando PDF único…");
                        PdfSigner.signSingle(in, out, kc);
                        appendLog("[OK] PDF firmado: " + out.getAbsolutePath());
                        ActivityLog.registrar("Firma local OK pdf=" + in.getName() + " cert=" + sel.alias);
                    } else {
                        appendLog("[*] Firmando ZIP (solo PDFs, conservando el mismo nombre)…");
                        PdfSigner.signZip(in, out, kc);
                        appendLog("[OK] ZIP firmado: " + out.getAbsolutePath());
                        ActivityLog.registrar("Firma local OK zip=" + in.getName() + " cert=" + sel.alias);
                    }
                } finally {
                    updateProgress(0, 1);
                }
                return null;
            }
        };

        progressBar.progressProperty().bind(signTask.progressProperty());

        signTask.setOnSucceeded(ev -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            alert(Alert.AlertType.INFORMATION, "Proceso de firma finalizado.");
        });
        signTask.setOnFailed(ev -> {
            progressBar.progressProperty().unbind();
            progressBar.setProgress(0);
            var ex = signTask.getException();
            ActivityLog.registrar("Firma local ERROR entrada=" + in.getName() + " cert=" + sel.alias
                    + " " + (ex != null ? ex.getMessage() : ""));
            alert(Alert.AlertType.ERROR, "Error al firmar: " + (ex != null ? ex.getMessage() : "desconocido"));
            if (ex != null) ex.printStackTrace();
        });

        new Thread(signTask, "sign-task").start();
    }

    private void refreshCertsAsync() {
        btnRefreshCerts.setDisable(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

        Task<List<CertInfo>> task = new Task<>() {
            @Override
            protected List<CertInfo> call() throws Exception {
                updateMessage("Buscando certificados…");
                return CertLoader.listAll(true, USE_PKCS11, PKCS11_CFG);
            }
        };

        task.setOnSucceeded(ev -> {
            List<CertInfo> list = task.getValue();
            cachedCerts = list;
            cboCerts.setItems(FXCollections.observableArrayList(list));
            if (!list.isEmpty()) cboCerts.getSelectionModel().select(0);
            appendLog("[*] Detectados " + list.size() + " certificado(s).");
            btnRefreshCerts.setDisable(false);
            progressBar.setProgress(0);
        });

        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            appendLog("[ERROR] No se pudieron listar certificados: " + (ex != null ? ex.getMessage() : "desconocido"));
            if (ex != null) ex.printStackTrace();
            btnRefreshCerts.setDisable(false);
            progressBar.setProgress(0);
        });

        new Thread(task, "cert-refresh").start();
    }

    // ---------- Utilidades ----------
    private void actualizarAvisoCertificado(CertInfo sel) {
        if (lblCertWarning == null) return;
        boolean mostrar = sel != null && !sel.esValido();
        lblCertWarning.setText(mostrar ? "Este certificado no se puede usar para firmar: " + sel.validacion.motivo : "");
        lblCertWarning.setVisible(mostrar);
        lblCertWarning.setManaged(mostrar);
        if (btnSign != null) {
            btnSign.setDisable(sel == null || !sel.esValido());
        }
    }

    private void appendLog(String s) {
        if (log == null) return;
        if (Platform.isFxApplicationThread()) {
            log.appendText(s + System.lineSeparator());
        } else {
            Platform.runLater(() -> log.appendText(s + System.lineSeparator()));
        }
    }

    private void alert(Alert.AlertType type, String msg) {
		Platform.runLater(() -> {
			Alert alert = new Alert(type, msg, ButtonType.OK);
			alert.setTitle("Dictuc - Firma Masiva");
			alert.setHeaderText(null); // sin cabecera, solo mensaje
			AppTheme.apply(alert.getDialogPane().getScene());
			AppTheme.aplicarIcono((Stage) alert.getDialogPane().getScene().getWindow());
			alert.showAndWait();
		});
	}

    private Stage getPrimaryStage() {
        return (Stage) txtInput.getScene().getWindow();
    }

    private static String suggestOutputPath(File input, boolean pdfMode) {
        String name = input.getName();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0) ? name.substring(0, dot) : name;
        String ext = pdfMode ? ".pdf" : ".zip";
        String suggested = base + "_firmado" + ext;
        return new File(input.getParentFile(), suggested).getAbsolutePath();
    }

    private void showFatal(String msg) {
        Platform.runLater(() -> {
            new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
            Platform.exit();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}