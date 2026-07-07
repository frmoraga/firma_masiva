package com.dictuc.feamass.xnet.ui;

import com.dictuc.feamass.AppTheme;
import com.dictuc.feamass.certs.CertInfo;
import com.dictuc.feamass.xnet.XnetConfig;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.List;

/** Punto de entrada del flujo de firma en linea contra XNET, invocado desde MainFx. */
public final class XnetFlow {

    private final Stage appOwner;
    private final List<CertInfo> cachedCerts;

    public XnetFlow(Stage appOwner, List<CertInfo> cachedCerts) {
        this.appOwner = appOwner;
        this.cachedCerts = cachedCerts;
    }

    public void start() {
        if (!XnetConfig.isConfigured()) {
            AppTheme.alert(Alert.AlertType.ERROR, "XNET no esta configurado. Define xnet.base.url en config.properties.");
            return;
        }
        String inseguro = XnetConfig.advertenciaInseguridad();
        if (inseguro != null) {
            AppTheme.alert(Alert.AlertType.ERROR, inseguro);
            return;
        }
        new LoginStage(appOwner, cachedCerts).show();
    }
}
