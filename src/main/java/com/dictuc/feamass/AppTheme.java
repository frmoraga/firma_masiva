package com.dictuc.feamass;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tema visual compartido por todas las ventanas de la app (local y XNET). */
public final class AppTheme {

    private static final String[] ICONOS_RES = {
            "/icons/icon-16.png", "/icons/icon-32.png", "/icons/icon-64.png", "/icons/icon-128.png"
    };
    private static List<Image> iconosCache;

    private AppTheme() {}

    public static void apply(Scene scene) {
        scene.getStylesheets().add(
                Objects.requireNonNull(AppTheme.class.getResource("/styles/app.css")).toExternalForm());
    }

    /** Iconos de la app (16/32/64/128) para usar en cualquier Stage nueva. Se cargan una sola vez. */
    public static List<Image> iconos() {
        if (iconosCache == null) {
            List<Image> lista = new ArrayList<>();
            for (String res : ICONOS_RES) {
                var is = AppTheme.class.getResourceAsStream(res);
                if (is != null) {
                    lista.add(new Image(is));
                }
            }
            iconosCache = lista;
        }
        return iconosCache;
    }

    /** Aplica los iconos de la app a una Stage (ventana o dialogo) nueva. */
    public static void aplicarIcono(Stage stage) {
        stage.getIcons().addAll(iconos());
    }

    /** Barra oscura de encabezado con logo, titulo y subtitulo (misma linea visual que el frontend de XNET). */
    public static HBox header(String titulo, String subtitulo) {
        StackPane logo = new StackPane(new Label("D"));
        logo.getStyleClass().add("logo-marca");

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("titulo-app");
        Label lblSubtitulo = new Label(subtitulo);
        lblSubtitulo.getStyleClass().add("subtitulo-app");
        VBox textos = new VBox(1, lblTitulo, lblSubtitulo);

        HBox header = new HBox(12, logo, textos);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("barra-encabezado");
        return header;
    }

    public static Label etiqueta(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("subtitulo");
        return l;
    }

    public static Label tituloSeccion(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("titulo-seccion");
        return l;
    }

    /** Muestra un Alert con el mismo tema visual de la app (bloqueante, en el hilo FX). */
    public static void alert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setHeaderText(null);
        apply(alert.getDialogPane().getScene());
        aplicarIconoVentana(alert.getDialogPane().getScene().getWindow());
        alert.showAndWait();
    }

    /** Dialogo de confirmacion Si/No con el mismo tema visual. Devuelve true solo si se elige "Si". */
    public static boolean confirmar(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        apply(alert.getDialogPane().getScene());
        aplicarIconoVentana(alert.getDialogPane().getScene().getWindow());
        return alert.showAndWait().filter(b -> b == ButtonType.YES).isPresent();
    }

    private static void aplicarIconoVentana(Window window) {
        if (window instanceof Stage stage) {
            aplicarIcono(stage);
        }
    }
}
