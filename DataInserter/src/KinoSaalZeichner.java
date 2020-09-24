/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class KinoSaalZeichner extends JPanel {
    private int selection = 1;

    public KinoSaalZeichner() {
        setLayout(new BorderLayout());

        // Rechtes Panel
        JPanel rightWrapperPanel = new JPanel(new BorderLayout());
        add(rightWrapperPanel, BorderLayout.EAST);

        JPanel rightPanel = new JPanel(new GridLayout(0, 1, 0, 5));
        rightPanel.setBorder(new TitledBorder(new LineBorder(Color.BLACK, 2), "Sitze zeichen"));
        rightWrapperPanel.add(rightPanel, BorderLayout.NORTH);

        for (int i = 1; i <= 5; i++) {
            Kategorie tempKat = Kategorie.getByNummer(i);
            if (tempKat == null) continue;
            JButton tempBtn = new JButton(tempKat.bezeichnung);
            tempBtn.addActionListener((e) -> selection = tempKat.nummer);
            rightPanel.add(tempBtn);
        }

        JPanel buttonsWrapper = new JPanel(new GridLayout(0, 1, 0, 5));
        buttonsWrapper.setBorder(new TitledBorder(new LineBorder(Color.BLACK, 2), "Aktionen"));
        rightWrapperPanel.add(buttonsWrapper, BorderLayout.SOUTH);

        JButton save = new JButton("Speichern");
        buttonsWrapper.add(save);

        // ZeichenPanel
        JPanel centerPanel = new JPanel();
        this.add(centerPanel, BorderLayout.CENTER);
        centerPanel.setBorder(new LineBorder(Color.BLACK, 2));
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("KinoSaalZeichner");
        frame.setContentPane(new KinoSaalZeichner());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private enum Kategorie {
        PARKETT(1, "Parkett"),
        LOGE(2, "Loge"),
        PREMIUM(3, "Premium"),
        LOVESEAT(4, "Loveseat"),
        BARRIEREFREI(5, "Barrierefrei");

        public final String bezeichnung;
        public final int nummer;

        Kategorie(int nummer, String bezeichnung) {
            this.nummer = nummer;
            this.bezeichnung = bezeichnung;
        }

        static Kategorie getByNummer(int nummer) {
            for (Kategorie kategorie : Kategorie.values()) {
                if (kategorie.nummer == nummer) return kategorie;
            }
            return null;
        }
    }
}
