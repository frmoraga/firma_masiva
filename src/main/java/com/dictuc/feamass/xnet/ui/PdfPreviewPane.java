package com.dictuc.feamass.xnet.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Visor de las paginas de un PDF ya renderizadas a imagenes en memoria (ver PdfPreviewRenderer),
 * con navegacion y las protecciones de siempre: no se puede arrastrar la imagen fuera de la app
 * ni abrir el menu contextual sobre ella.
 *
 * Lo comparten la revision de un informe (ReviewSignStage) y la confirmacion de la firma masiva
 * (BulkReviewStage): asi las dos ventanas muestran -y protegen- el contenido de la misma forma,
 * en vez de tener dos copias del mismo visor que se desincronizan con el tiempo.
 */
final class PdfPreviewPane extends VBox {

    private final ImageView imageView = new ImageView();
    private final Label lblPagina = new Label();
    private final Button btnPrev = new Button("< Anterior");
    private final Button btnNext = new Button("Siguiente >");

    private List<Image> paginas;
    private int indice = 0;

    PdfPreviewPane(double anchoImagen, double altoVisor) {
        super(10);

        imageView.setPreserveRatio(true);
        imageView.setFitWidth(anchoImagen);
        // Defensa en profundidad: no permitir arrastrar la imagen ni abrir menu contextual.
        imageView.setOnDragDetected(javafx.event.Event::consume);
        imageView.setOnContextMenuRequested(javafx.event.Event::consume);

        ScrollPane scroll = new ScrollPane(imageView);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(altoVisor);
        scroll.getStyleClass().add("visor-preview");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        btnPrev.getStyleClass().add("button-secundario");
        btnNext.getStyleClass().add("button-secundario");
        btnPrev.setOnAction(e -> mostrarPagina(indice - 1));
        btnNext.setOnAction(e -> mostrarPagina(indice + 1));
        lblPagina.getStyleClass().add("subtitulo");

        HBox nav = new HBox(10, btnPrev, lblPagina, btnNext);
        nav.setAlignment(Pos.CENTER);

        getChildren().addAll(nav, scroll);
        limpiar();
    }

    /** Carga las paginas y muestra la primera. */
    void mostrar(List<Image> paginas) {
        this.paginas = paginas;
        this.indice = 0;
        if (paginas == null || paginas.isEmpty()) {
            limpiar();
            return;
        }
        mostrarPagina(0);
    }

    /** Suelta las imagenes: el PDF solo vive en memoria y no debe quedar mas de lo necesario. */
    void limpiar() {
        paginas = null;
        indice = 0;
        imageView.setImage(null);
        lblPagina.setText("");
        btnPrev.setDisable(true);
        btnNext.setDisable(true);
    }

    boolean tienePaginas() {
        return paginas != null && !paginas.isEmpty();
    }

    private void mostrarPagina(int idx) {
        if (!tienePaginas()) return;
        indice = Math.max(0, Math.min(idx, paginas.size() - 1));
        imageView.setImage(paginas.get(indice));
        lblPagina.setText("Pagina " + (indice + 1) + " / " + paginas.size());
        btnPrev.setDisable(indice == 0);
        btnNext.setDisable(indice == paginas.size() - 1);
    }

    /** Defensa en profundidad: bloquea guardar/imprimir/copiar sobre una ventana con vista previa. */
    static void protegerAtajos(Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (KeyCombination.keyCombination("Shortcut+S").match(ev)
                    || KeyCombination.keyCombination("Shortcut+P").match(ev)
                    || KeyCombination.keyCombination("Shortcut+C").match(ev)
                    || ev.getCode() == KeyCode.PRINTSCREEN) {
                ev.consume();
            }
        });
    }
}
