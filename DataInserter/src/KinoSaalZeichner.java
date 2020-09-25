/*
 * Copyright (c) DHBW Mannheim - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Noah Hoelterhoff <noah.hoelterhoff@gmail.com>, 9 2020
 */

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Klasse für das Erstellen von Kinosälen.
 *
 * @author Noah Hoelterhoff
 * @version 25.09.2020
 * @since 23.09.2020
 */
public class KinoSaalZeichner extends JPanel {
    private final static String HOST = "https://dhbw-kino.de:4567";
    private final static int ROUND_TO = 5;
    private final JPanel drawPanel;
    private final List<Kategorie> kategorien = Collections.synchronizedList(new ArrayList<>());
    private final List<Sitz> sitze = Collections.synchronizedList(new ArrayList<>());
    private Sitz draggedData = null;
    private int draggedDataDx = 0, draggedDataDy = 0;
    private Kategorie selection;

    /**
     * ID des Kinosaals, der geladen werden soll. {@code 0}, falls keine geladen werden soll.
     */
    public KinoSaalZeichner(int id) throws IOException {
        JsonObject data = new JsonObject();
        // Daten laden
        if (id != 0) data = loadDataFromServer(id);

        // Panel Einstellungen
        setLayout(new BorderLayout());

        // Rechtes Panel
        JPanel rightWrapperPanel = new JPanel(new BorderLayout());
        add(rightWrapperPanel, BorderLayout.EAST);

        JPanel rightUpperPanel = new JPanel(new GridLayout(0, 1, 0, 5));
        rightUpperPanel.setBorder(new TitledBorder(new LineBorder(Color.BLACK, 2), "Sitze zeichen"));
        rightWrapperPanel.add(rightUpperPanel, BorderLayout.NORTH);

        final ArrayList<JButton> buttons = new ArrayList<>();
        for (Kategorie kategorie : kategorien) {
            JButton tempBtn = new JButton(kategorie.name);
            tempBtn.setBackground(kategorie.bg_color);
            tempBtn.setForeground(Color.WHITE);
            tempBtn.addActionListener((e) -> {
                selection = kategorie;
                buttons.forEach((btn) -> btn.setBorder(new EmptyBorder(5, 5, 5, 5)));
                tempBtn.setBorder(new LineBorder(Color.GREEN, 5));
                buttons.forEach(Component::repaint);
            });
            buttons.add(tempBtn);
            rightUpperPanel.add(tempBtn);
        }
        buttons.forEach(btn -> btn.setBorder(new EmptyBorder(5, 5, 5, 5)));

        JPanel rightLowerPanel = new JPanel(new GridLayout(0, 1, 0, 5));
        rightLowerPanel.setBorder(new TitledBorder(new LineBorder(Color.BLACK, 2), "Aktionen"));
        rightWrapperPanel.add(rightLowerPanel, BorderLayout.SOUTH);

        JButton reset = new JButton("Reset");
        rightLowerPanel.add(reset);

        JButton save = new JButton("Speichern");
        save.addActionListener(e -> System.out.println("EXPORT"));
        rightLowerPanel.add(save);


        // ZeichenPanel
        JPanel centerPanel = new JPanel(new BorderLayout());
        this.add(centerPanel, BorderLayout.CENTER);
        centerPanel.setBorder(new EmptyBorder(7, 5, 2, 5));

        drawPanel = new DrawPanel(data);
        centerPanel.add(drawPanel, BorderLayout.CENTER);
    }

    /*public void export() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("width", drawPanel.getWidth());
        jsonObject.addProperty("height", drawPanel.getHeight());

        JsonArray kategorien = new JsonArray();
        for (Kategorie k : Kategorie.values()) {
            JsonObject temp = new JsonObject();
            temp.addProperty("id", k.nummer);
            temp.addProperty("bezeichnung", k.bezeichnung);
            temp.addProperty("preis", k.getPreis());
            temp.addProperty("width", k.width);
            temp.addProperty("height", 20);
            kategorien.add(temp);
        }
        jsonObject.add("kategorien", kategorien);

        JsonArray sitze = new JsonArray();
        for (Sitz d : this.sitze) {
            JsonObject temp = new JsonObject();
            temp.addProperty("reihe", d.reihe);
            temp.addProperty("platz", d.platz);
            temp.addProperty("kategorie", d.kategorie.nummer);
            temp.addProperty("x", d.x);
            temp.addProperty("y", d.y);
            sitze.add(temp);
        }
        jsonObject.add("sitze", sitze);

        System.out.println(jsonObject.toString());
    }*/

    private Kategorie findKategorieById(int pId) {
        for (Kategorie kategorie : kategorien) {
            if (kategorie.id == pId) return kategorie;
        }
        return null;
    }

    private JsonElement get(String pPath, boolean isArray) throws IOException {
        URL url = new URL(HOST + pPath);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        System.out.println(url.toString() + " " + conn.getResponseCode());
        if (conn.getResponseCode() != 200) {
            JOptionPane.showMessageDialog(KinoSaalZeichner.this, conn.getResponseMessage());
            return (isArray ? new JsonArray() : new JsonObject());
        }
        InputStreamReader in = new InputStreamReader(conn.getInputStream());
        BufferedReader br = new BufferedReader(in);

        StringBuilder builder = new StringBuilder();
        String output;
        while ((output = br.readLine()) != null) {
            builder.append(output);
        }
        conn.disconnect();
        return new Gson().fromJson(builder.toString(), (isArray ? JsonArray.class : JsonObject.class));
    }

    private JsonObject loadDataFromServer(int pId) throws IOException {
        JsonArray jsonKategorien = (JsonArray) get("/get-kategorien", true);
        jsonKategorien.forEach(jsonKategorie -> new Kategorie((JsonObject) jsonKategorie));

        JsonObject jsonSaalplan = (JsonObject) get("/get-saalplan/" + pId, false);
        JsonArray jsonSitze = jsonSaalplan.get("sitze").getAsJsonArray();
        jsonSitze.forEach(jsonSitz -> sitze.add(new Sitz((JsonObject) jsonSitz)));

        return jsonSaalplan;
    }

    public static void main(String[] args) throws IOException {
        int saal = Integer.parseInt(JOptionPane.showInputDialog("ID"));

        JFrame frame = new JFrame("KinoSaalZeichner");
        frame.setContentPane(new KinoSaalZeichner(saal));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static int round(int input) {
        int temp = input % ROUND_TO;
        if (ROUND_TO - temp >= temp) return input - temp;
        else return input + (ROUND_TO - temp);
    }

    /**
     * Ein Panel, auf dem gezeichnet werden kann
     */
    private class DrawPanel extends JPanel {
        public DrawPanel(JsonObject pJson) {
            super();
            setBorder(new LineBorder(Color.BLACK, 2));
            setPreferredSize(new Dimension(pJson.get("width").getAsInt(), pJson.get("height").getAsInt()));

            // Mouse Adapter hinzufügen
            DrawPanelMouseAdapter mouseAdapter = new DrawPanelMouseAdapter();
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
            sitze.forEach((sitz) -> {
                g2d.setColor(sitz.kategorie.bg_color);
                g2d.fillRect(sitz.x, sitz.y, sitz.kategorie.width, sitz.kategorie.height);
                g2d.setColor(Color.WHITE);
                g2d.drawString(sitz.reihe + "" + sitz.platz, sitz.x + 1, sitz.yW() - 5);
            });
        }
    }

    private class DrawPanelMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent event) {
            int x = event.getX();
            int y = event.getY();

            switch (event.getButton()) {
                case MouseEvent.BUTTON1: // Sitz hinzufügen
                    int placeAtX = round(x - selection.width / 2);
                    int placeAtY = round(y - selection.height / 2);
                    String input = JOptionPane.showInputDialog(KinoSaalZeichner.this, "Platzcode eingeben (<Buchstabe><Zahl>):");
                    try {
                        Sitz sitz = new Sitz(placeAtX, placeAtY, input.charAt(0), Integer.parseInt(input.substring(1)));
                        sitze.add(sitz);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(KinoSaalZeichner.this, "Ungültige Eingabe!", "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                    break;
                case MouseEvent.BUTTON2: // Sitz umbennen
                    for (Sitz sitz : sitze) {
                        if (sitz.x <= x && sitz.xW() >= x && sitz.y <= y && sitz.yW() >= y) {
                            // TODO Platz Name ändern
                        }
                    }
                    break;
                case MouseEvent.BUTTON3: // Sitz löschen
                    Sitz temp = null;
                    for (Sitz sitz : sitze) {
                        if (sitz.x <= x && sitz.xW() >= x && sitz.y <= y && sitz.yW() >= y) {
                            temp = sitz;
                        }
                    }
                    if (temp != null) sitze.remove(temp);
                    break;
            }
            drawPanel.repaint();
        }

        /**
         * Wird aufgerufen, wenn die Maus beim gedrückt halten bewegt
         *
         * @param event Event (inkl. Koordinaten etc.)
         */
        @Override
        public void mouseDragged(MouseEvent event) {
            if (draggedData == null) return;
            draggedData.x = round(event.getX() - draggedDataDx);
            draggedData.y = round(event.getY() - draggedDataDy);
            repaint();
        }

        @Override
        public void mousePressed(MouseEvent event) {
            // Event Daten abfragen
            int mouseX = event.getX();
            int mouseY = event.getY();

            // Obersten SItz finden
            Sitz temp = null;
            for (Sitz sitz : sitze) {
                if (sitz.x <= mouseX && sitz.xW() >= mouseX && sitz.y <= mouseY && sitz.yW() >= mouseY) temp = sitz;
            }

            // Sitz als im Drag markieren
            if (temp != null) {
                draggedData = temp;
                draggedDataDx = mouseX - temp.x;
                draggedDataDy = mouseY - temp.y;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            draggedData = null;
        }
    }

    /**
     * Repräsentiert eine Kategorie von Sitzen
     */
    private class Kategorie {
        public final Color bg_color;
        public final int id, width, height;
        public final String name;

        public Kategorie(JsonObject jsonObject) {
            name = jsonObject.get("name").getAsString();
            id = jsonObject.get("kategorieid").getAsInt();
            width = jsonObject.get("width").getAsInt();
            height = jsonObject.get("height").getAsInt();
            bg_color = Color.decode(jsonObject.get("color_hex").getAsString());
            kategorien.add(this);
        }
    }

    /**
     * Repäsentiert einen Sitz in dem Kino
     */
    private class Sitz {
        private final Kategorie kategorie;
        private int platz, x, y;
        private char reihe;

        public Sitz(JsonObject pJson) {
            x = pJson.get("x").getAsInt();
            y = pJson.get("y").getAsInt();
            platz = pJson.get("platz").getAsInt();
            reihe = pJson.get("reihe").getAsString().charAt(0);
            kategorie = findKategorieById(pJson.get("kategorie").getAsInt());
        }

        public Sitz(int x, int y, char reihe, int platz) {
            this.x = x;
            this.y = y;
            this.reihe = reihe;
            this.platz = platz;
            kategorie = selection;
        }

        private int xW() {
            return x + kategorie.width;
        }

        private int yW() {
            return y + 20;
        }
    }
}
