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
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

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
        root.setPadding(new Insets(24));

        var title = new Label("Inicializando…");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var msg = new Label("Buscando certificados (Windows / Token)…");
        msg.setWrapText(true);

        var spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        var box = new VBox(12, title, msg, spinner);
        box.setAlignment(Pos.CENTER);

        root.setCenter(box);

		stage.setResizable(false);

        var scene = new Scene(root, 640, 360);
        stage.setScene(scene);
        stage.initStyle(StageStyle.DECORATED);
        loadWindowIcons(stage);
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

        btnBrowseIn = new Button("Examinar…");
        btnBrowseIn.setOnAction(e -> chooseInput());

        txtOutput = new TextField();
        txtOutput.setPromptText("Archivo de salida (se sugiere automáticamente)…");
        txtOutput.setPrefColumnCount(40);

        btnBrowseOut = new Button("Destino…");
        btnBrowseOut.setOnAction(e -> chooseOutput());

        HBox rowIn = new HBox(10, new Label("Entrada:"), txtInput, btnBrowseIn);
        rowIn.setAlignment(Pos.CENTER_LEFT);

        HBox rowOut = new HBox(10, new Label("Salida:"), txtOutput, btnBrowseOut);
        rowOut.setAlignment(Pos.CENTER_LEFT);

        // ======= Modo (PDF único o ZIP) =======
        ToggleGroup tg = new ToggleGroup();
        rbPdf = new RadioButton("Un PDF");
        rbZip = new RadioButton("Un ZIP (varios PDFs)");
		rbZip.setSelected(true);
        rbPdf.setToggleGroup(tg);
        rbZip.setToggleGroup(tg);

        HBox rowMode = new HBox(20, new Label("Modo:"), rbPdf, rbZip);
        rowMode.setAlignment(Pos.CENTER_LEFT);

        // ======= Certificados =======
        cboCerts = new ComboBox<>();
        cboCerts.setPrefWidth(600);
        cboCerts.setPromptText("Seleccione un certificado…");

        if (initialCerts != null) {
            cboCerts.setItems(FXCollections.observableArrayList(initialCerts));
            if (!initialCerts.isEmpty()) cboCerts.getSelectionModel().select(0);
        }

        btnRefreshCerts = new Button("Detectar certificados");
        btnRefreshCerts.setOnAction(e -> refreshCertsAsync());

        HBox rowCert = new HBox(10, new Label("Certificado:"), cboCerts, btnRefreshCerts);
        rowCert.setAlignment(Pos.CENTER_LEFT);

        // ======= Log + acciones =======
        log = new TextArea();
        log.setEditable(false);
        log.setPrefRowCount(12);

        btnSign = new Button("Firmar");
        btnSign.setDefaultButton(true);
        btnSign.setOnAction(e -> onSign());

        Button btnExit = new Button("Salir");
        btnExit.setOnAction(e -> Platform.exit());

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(progressBar, Priority.ALWAYS);

        HBox rowActions = new HBox(10, progressBar, btnSign, btnExit);
        rowActions.setAlignment(Pos.CENTER_RIGHT);

        // ======= Layout raíz =======
        VBox root = new VBox(12,
                rowMode,
                rowIn,
                rowOut,
                new Separator(),
                rowCert,
                new Label("Registro:"),
                log,
                rowActions
        );
        root.setPadding(new Insets(14));

        Scene scene = new Scene(root, 920, 540);
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
                    } else {
                        appendLog("[*] Firmando ZIP (solo PDFs, conservando el mismo nombre)…");
                        PdfSigner.signZip(in, out, kc);
                        appendLog("[OK] ZIP firmado: " + out.getAbsolutePath());
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

			// Agregar icono (igual que el de la app principal)
			Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
			var iconStream = getClass().getResourceAsStream("/icons/icon-64.png");
			if (iconStream != null) {
				alertStage.getIcons().add(new Image(iconStream));
			}

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

    /** Intenta cargar iconos del Stage desde /icons (PNG). */
    private void loadWindowIcons(Stage stage) {
        String[] names = {
                "/icons/icon-16.png",
                "/icons/icon-32.png",
                "/icons/icon-64.png",
                "/icons/icon-128.png"
        };
        for (String res : names) {
            try {
                var is = getClass().getResourceAsStream(res);
                if (is != null) {
                    stage.getIcons().add(new Image(is));
                }
            } catch (Exception ignored) {
            }
        }
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