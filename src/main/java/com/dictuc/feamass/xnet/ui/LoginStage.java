package com.dictuc.feamass.xnet.ui;

import com.dictuc.feamass.ActivityLog;
import com.dictuc.feamass.AppTheme;
import com.dictuc.feamass.certs.CertInfo;
import com.dictuc.feamass.xnet.XnetClient;
import com.dictuc.feamass.xnet.XnetSession;
import com.dictuc.feamass.xnet.model.LoginResponseDto;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/** Pantalla de login contra XNET. Usuario/contrasena -> token, sin persistir credenciales. */
public final class LoginStage extends Stage {

    private final Stage appOwner;
    private final List<CertInfo> cachedCerts;
    private final TextField txtEmail = new TextField();
    private final PasswordField txtPassword = new PasswordField();
    private final Button btnLogin = new Button("Ingresar");
    private final Label lblError = new Label();
    private final ProgressIndicator spinner = new ProgressIndicator();

    public LoginStage(Stage appOwner, List<CertInfo> cachedCerts) {
        this.appOwner = appOwner;
        this.cachedCerts = cachedCerts;

        setTitle("XNET - Iniciar sesion");
        initOwner(appOwner);

        txtEmail.setPromptText("Correo Dictuc");
        txtPassword.setPromptText("Contrasena");
        lblError.getStyleClass().add("texto-error");
        lblError.setWrapText(true);
        spinner.setVisible(false);
        spinner.setPrefSize(18, 18);

        btnLogin.getStyleClass().add("button-primario");
        btnLogin.setDefaultButton(true);
        btnLogin.setOnAction(e -> doLogin());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(AppTheme.etiqueta("Correo"), 0, 0);
        grid.add(txtEmail, 0, 1);
        grid.add(AppTheme.etiqueta("Contrasena"), 0, 2);
        grid.add(txtPassword, 0, 3);
        txtEmail.setPrefWidth(280);
        txtPassword.setPrefWidth(280);

        HBox actionRow = new HBox(10, btnLogin, spinner);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Label subtitulo = AppTheme.etiqueta("Ingresa tus credenciales de XNET para firmar tus informes pendientes.");
        subtitulo.setWrapText(true);

        VBox card = new VBox(16,
                AppTheme.tituloSeccion("Iniciar sesion"),
                subtitulo,
                grid, actionRow, lblError);
        card.getStyleClass().add("card");
        card.setMaxWidth(340);
        card.setPadding(new Insets(24));

        StackPane center = new StackPane(card);
        center.setPadding(new Insets(30));

        BorderPane root = new BorderPane();
        root.setTop(AppTheme.header("XNET", "Firma electronica en linea"));
        root.setCenter(center);

        Scene scene = new Scene(root, 460, 420);
        AppTheme.apply(scene);
        setScene(scene);
    }

    private void doLogin() {
        String email = txtEmail.getText() == null ? "" : txtEmail.getText().trim();
        String password = txtPassword.getText() == null ? "" : txtPassword.getText();
        if (email.isBlank() || password.isBlank()) {
            lblError.setText("Debes ingresar correo y contrasena.");
            return;
        }

        lblError.setText("");
        btnLogin.setDisable(true);
        spinner.setVisible(true);

        Task<XnetSession> task = new Task<>() {
            @Override
            protected XnetSession call() throws Exception {
                XnetClient client = new XnetClient();
                LoginResponseDto resp = client.login(email, password);
                return new XnetSession(client, resp.csrfToken,
                        resp.usuario.id, resp.usuario.rut, resp.usuario.nombre);
            }
        };

        task.setOnSucceeded(ev -> {
            XnetSession session = task.getValue();
            ActivityLog.registrar("Login XNET OK usuario=" + session.rutUsuario());
            txtPassword.clear();
            new PendingListStage(appOwner, session, cachedCerts).show();
            close();
        });

        task.setOnFailed(ev -> {
            btnLogin.setDisable(false);
            spinner.setVisible(false);
            Throwable ex = task.getException();
            ActivityLog.registrar("Login XNET ERROR correo=" + email + " " + (ex != null ? ex.getMessage() : ""));
            lblError.setText(ex != null ? ex.getMessage() : "No fue posible iniciar sesion.");
        });

        new Thread(task, "xnet-login").start();
    }
}
