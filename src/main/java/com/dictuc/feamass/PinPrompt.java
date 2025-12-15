package com.dictuc.feamass;

import javax.swing.*;

public final class PinPrompt {
    public static char[] ask() {
        JPasswordField pf = new JPasswordField();
        int ok = JOptionPane.showConfirmDialog(null, pf, "PIN del token",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) return pf.getPassword();
        return null;
    }
}
