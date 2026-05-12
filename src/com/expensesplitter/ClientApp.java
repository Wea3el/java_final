package com.expensesplitter;

import com.expensesplitter.gui.MainFrame;
import javax.swing.SwingUtilities;

public class ClientApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainFrame::new);
    }
}
