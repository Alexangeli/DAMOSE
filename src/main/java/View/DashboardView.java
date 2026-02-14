package View;

import Model.User.Session;
import config.AppConfig;

import Controller.SearchMode.SearchMode;
import View.Map.LineStopsView;
import View.Map.MapView;
import View.SearchBar.SearchBarView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Dashboard:
 * - SearchBarView sempre visibile (compact=true)
 * - Pill Fermata/Linea come floating button (visibile quando l'overlay è "aperto")
 * - Card bianca dei risultati (Dettagli) visibile SOLO quando ci sono elementi
 * - Filtri bus/tram/metro visibili solo in modalità LINE (come floating buttons)
 * - ★ sempre visibile (floating)
 */
public class DashboardView extends JPanel {

    private final MapView mapView;

    // searchbar reale (backend invariato) - compact: mostra solo barra
    private final SearchBarView searchBarView;

    private final LineStopsView lineStopsView;

    private final JButton favoritesButton;
    private int favoritesCount = 0;

    private boolean overlayVisible = false;

    // Card bianca con ombra: deve comparire SOLO quando ci sono risultati
    private final JPanel overlayCard;
    private final JPanel resultsBody;
    // contenitore per mostrare placeholder finché non arrivano elementi
    private final JPanel detailsContainer;
    private final JLabel detailsPlaceholder;
    // header moderno per la card Dettagli
    private final JPanel detailsHeader;
    private final JLabel detailsTitleLabel;
    private final JLabel detailsSubtitleLabel;

    // ✅ pill Fermata/Linea FLOATING (non dentro la card)
    private final JToggleButton floatingModeToggle;

    // filtri (floating) OFF/ON (solo in LINE)
    private final IconToggleButton busBtn;
    private final IconToggleButton tramBtn;
    private final IconToggleButton metroBtn;

    // ★ floating
    private final JButton overlayStarBtn;
    private final JButton floatingStarBtn;

    private final JLayeredPane layeredPane;
    private boolean clickAwayInstalled = false;

    // Callback per richiedere login (apre AuthDialog). Viene impostato dal Main.
    private Runnable onRequireAuth;

    // Callback per aprire la finestra Preferiti (★ in basso a destra). Viene impostato dal Main.
    private Runnable onOpenFavorites;

    // Sync ★ stato (abilitata solo se c'è un elemento selezionato)
    private final Timer starSyncTimer;

    public DashboardView() {
        setLayout(new BorderLayout());
        setBackground(AppConfig.BACKGROUND_COLOR);

        // ===================== MAPPA =====================
        mapView = new MapView();
        mapView.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ===================== COMPONENTI (backend invariato) =====================
        searchBarView = new SearchBarView(true); // compact=true => solo barra visibile
        lineStopsView = new LineStopsView();
        // LineStopsView ha un suo header interno ("Dettagli", "Linee che passano per..."):
        // qui lo nascondiamo perché usiamo l'header moderno della card.
        stripInternalHeaderFromLineStopsView();

        // quando clicchi la X rossa nella searchbar, resetta "Dettagli"
        searchBarView.setOnClear(() -> {
            lineStopsView.clear();
            syncDetailsVisibilityFromContent();
        });

        // ===================== FLOATING BUTTON (preferiti in basso a dx) =====================
        favoritesButton = createFloatingFavoritesButton();

        // ===================== PILL (floating) Fermata/Linea =====================
        floatingModeToggle = new PillToggleButton("Fermata");
        floatingModeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        floatingModeToggle.addActionListener(e -> {
            SearchMode next = (searchBarView.getCurrentMode() == SearchMode.STOP)
                    ? SearchMode.LINE
                    : SearchMode.STOP;

            searchBarView.setMode(next);
            syncOverlayFromSearchBar();
        });
        // per default è nascosto finché non apro overlay (click sulla searchbar)
        floatingModeToggle.setVisible(false);

        // ===================== FILTRI (floating) + STAR (floating) =====================
        int floatBtnSize = 54;
        Dimension toggleSize = new Dimension(floatBtnSize, floatBtnSize);

        busBtn   = new IconToggleButton("/icons/bus.png",   "/icons/busblu.png",     toggleSize, "Bus");
        tramBtn  = new IconToggleButton("/icons/tram.png",  "/icons/tramverde.png",  toggleSize, "Tram");
        metroBtn = new IconToggleButton("/icons/metro.png", "/icons/metrorossa.png", toggleSize, "Metro");

        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        busBtn.addActionListener(e -> pushLineFiltersToSearchBar());
        tramBtn.addActionListener(e -> pushLineFiltersToSearchBar());
        metroBtn.addActionListener(e -> pushLineFiltersToSearchBar());

        overlayStarBtn = createOverlayStarButton();
        floatingStarBtn = overlayStarBtn;

        floatingStarBtn.setPreferredSize(new Dimension(floatBtnSize, floatBtnSize));
        floatingStarBtn.setMinimumSize(new Dimension(floatBtnSize, floatBtnSize));
        floatingStarBtn.setMaximumSize(new Dimension(floatBtnSize, floatBtnSize));
        // di default nascosta finché non apro overlay (click sulla searchbar)
        floatingStarBtn.setVisible(false);

        // ===================== OVERLAY CARD (SOLO risultati) =====================
        overlayCard = new RoundedShadowPanel();
        overlayCard.setLayout(new BorderLayout());
        overlayCard.setOpaque(false);

        // ===================== HEADER MODERNO (Dettagli) =====================
        detailsTitleLabel = new JLabel("Dettagli");
        detailsTitleLabel.setOpaque(false);
        detailsTitleLabel.setForeground(new Color(25, 25, 25));
        detailsTitleLabel.setFont(detailsTitleLabel.getFont().deriveFont(Font.BOLD, 20f));

        detailsSubtitleLabel = new JLabel("Seleziona una fermata o una linea");
        detailsSubtitleLabel.setOpaque(false);
        detailsSubtitleLabel.setForeground(new Color(90, 90, 90));
        detailsSubtitleLabel.setFont(detailsSubtitleLabel.getFont().deriveFont(Font.PLAIN, 14.5f));

        detailsHeader = new DetailsHeaderPanel(detailsTitleLabel, detailsSubtitleLabel);
        detailsHeader.setOpaque(false);

        JPanel headerWrap = new JPanel(new BorderLayout());
        headerWrap.setOpaque(false);
        // più aria a sinistra/alto: look “app” e niente attaccato ai bordi
        headerWrap.setBorder(BorderFactory.createEmptyBorder(18, 22, 12, 18));
        headerWrap.add(detailsHeader, BorderLayout.CENTER);

        JSeparator headerSep = new JSeparator(SwingConstants.HORIZONTAL);
        headerSep.setForeground(new Color(225, 225, 225));
        headerSep.setOpaque(false);
        headerWrap.add(headerSep, BorderLayout.SOUTH);

        overlayCard.add(headerWrap, BorderLayout.NORTH);

        resultsBody = new JPanel(new BorderLayout());
        resultsBody.setOpaque(false);
        // spazio coerente con headerWrap
        resultsBody.setBorder(BorderFactory.createEmptyBorder(10, 22, 18, 18));


        // Placeholder mostrato quando c'è una selezione ma la lista non è ancora popolata
        detailsPlaceholder = new JLabel("Dettagli in caricamento…", SwingConstants.CENTER);
        detailsPlaceholder.setOpaque(false);
        detailsPlaceholder.setForeground(new Color(70, 70, 70));
        detailsPlaceholder.setFont(detailsPlaceholder.getFont().deriveFont(Font.BOLD, 16.5f));

        // CardLayout: placeholder <-> risultati
        detailsContainer = new JPanel(new CardLayout());
        detailsContainer.setOpaque(false);
        detailsContainer.add(detailsPlaceholder, "EMPTY");
        // LineStopsView contiene già la propria scroll area: evitare doppia scrollbar
        detailsContainer.add(lineStopsView, "LIST");

        resultsBody.add(detailsContainer, BorderLayout.CENTER);

        // rende la lista dentro LineStopsView più "app-like" (font grandi, righe alte, padding)
        applyModernDetailsListStyle();
        updateDetailsHeaderText();

        // la card contiene SOLO il body
        overlayCard.add(resultsBody, BorderLayout.CENTER);
        overlayCard.setVisible(false); // nasce nascosta

        // ===================== LAYERED PANE =====================
        layeredPane = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();

                mapView.setBounds(0, 0, w, h);

                // searchbar fissa
                int barW = Math.min(520, Math.max(320, (int) (w * 0.36)));
                int barH = 70;
                int x = 24;
                int y = 24;
                searchBarView.setBounds(x, y, barW, barH);

                // ===== Floating: Switch (pill) tra searchbar e ★ + ★ + filtri =====
                int gap = 10;
                int btnSize = 54;

                // pill tra searchbar e ★ (stessa altezza della ★)
                int pillW = 120;
                int pillH = btnSize; // uguale al pulsante preferiti (★)
                int pillX = x + barW + gap;
                int pillY = y + (barH - pillH) / 2;
                floatingModeToggle.setBounds(pillX, pillY, pillW, pillH);

                // ★ a destra della pill
                int starX = pillX + pillW + gap;
                // allineamento verticale perfetto con searchbar e pill
                int starY = y + (barH - btnSize) / 2;
                floatingStarBtn.setBounds(starX, starY, btnSize, btnSize);

                // Filtri: Bus+Tram sotto la pill, Metro sotto la ★ (preferiti)
                int gridGap = 14; // spazio tra i bottoni
                int gridX = pillX; // allineata a sinistra con la pill
                int gridY = starY + btnSize + gap;

                // riga 1 sotto la pill
                busBtn.setBounds(gridX, gridY, btnSize, btnSize);
                tramBtn.setBounds(gridX + btnSize + gridGap, gridY, btnSize, btnSize);

                // Metro: sotto/colonna della ★ ma ALLA STESSA ALTEZZA di Bus/Tram
                metroBtn.setBounds(starX, gridY, btnSize, btnSize);

                // Clamp a destra: se star esce, trasla pill + star + filtri
                int maxX = w - btnSize - 12;
                if (starX > maxX) {
                    int shift = starX - maxX;

                    floatingModeToggle.setLocation(pillX - shift, pillY);
                    floatingStarBtn.setLocation(starX - shift, starY);

                    busBtn.setLocation(gridX - shift, gridY);
                    tramBtn.setLocation(gridX - shift + btnSize + gridGap, gridY);
                    metroBtn.setLocation(starX - shift, gridY);
                }

                // ===== Card risultati sotto la pill (altezza dinamica) =====
                if (overlayCard.isVisible()) {
                    int ogap = 12;
                    int cardW = barW;

                    int topY = y + barH + ogap;
                    int availH = h - topY - 60;

                    // Altezza dinamica in base al contenuto (righe) + padding interno.
                    int items = getLineStopsItemCount();

                    // stima: altezza riga moderna (Renderer) ~ 76px (fixedCellHeight)
                    int rowH = 76;

                    // base (titolo / padding / scroll bar breathing)
                    int baseH = 120;

                    int contentH = (items <= 0) ? 220 : (baseH + (items * rowH));

                    int minH = 220;
                    int maxCap = 560;
                    int cardH = Math.max(minH, contentH);
                    cardH = Math.min(cardH, Math.min(maxCap, availH));

                    overlayCard.setBounds(x, topY, cardW, cardH);
                }

                // bottone preferiti in basso a destra
                int baseSize = 76;
                int minSide = Math.min(w, h);
                double scaleFactor = minSide / 900.0;
                scaleFactor = Math.max(0.6, Math.min(1.2, scaleFactor));
                int favBtnSize = (int) Math.round(baseSize * scaleFactor);

                int margin = 24;
                int fx = w - favBtnSize - margin;
                int fy = h - favBtnSize - margin;
                favoritesButton.setBounds(fx, fy, favBtnSize, favBtnSize);
            }
        };
        layeredPane.setOpaque(false);

        layeredPane.add(mapView, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(searchBarView, JLayeredPane.PALETTE_LAYER);

        // floating pill + card
        layeredPane.add(floatingModeToggle, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(overlayCard, JLayeredPane.PALETTE_LAYER);

        // floating buttons
        layeredPane.add(floatingStarBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(busBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(tramBtn, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(metroBtn, JLayeredPane.PALETTE_LAYER);

        layeredPane.add(favoritesButton, JLayeredPane.PALETTE_LAYER);
        // Assicura che la ★ dei preferiti sia sempre sopra tutto e cliccabile
        layeredPane.setLayer(favoritesButton, JLayeredPane.DRAG_LAYER);
        layeredPane.moveToFront(favoritesButton);

        add(layeredPane, BorderLayout.CENTER);

        // click searchbar -> apri overlay (pill visibile, card solo se risultati)
        installExpandOnClick(searchBarView);
        // ENTER nella searchbar: apre overlay e mostra Dettagli appena arrivano risultati
        installShowDetailsOnEnter();
        

        // click-away -> chiudi overlay (nasconde pill + card)
        installGlobalClickAwayOnce();

        // stato iniziale coerente
        syncOverlayFromSearchBar();
        setOverlayVisible(false);
        // sicurezza: se il backend popola subito la lista, riallinea la card
        syncDetailsVisibilityFromContent();

        // Timer: ★ + visibilità card quando cambia contenuto
        starSyncTimer = new Timer(120, e -> {
            boolean hasSel = hasAnyCurrentSelection();
            if (overlayStarBtn.isEnabled() != hasSel) {
                overlayStarBtn.setEnabled(hasSel);
            }
            if (overlayVisible) overlayStarBtn.repaint();
            if (overlayVisible) syncDetailsVisibilityFromContent();
        });
        starSyncTimer.setRepeats(true);
        starSyncTimer.start();
    }

    // ============================================================
    // REGOLE DI VISIBILITÀ (questa è la parte chiave)
    // ============================================================

    /**
     * La card bianca DEVE comparire SOLO se:
     * - overlayVisible == true (ho cliccato la searchbar)
     * - ci sono risultati (lista non vuota)
     */
    private void syncDetailsVisibilityFromContent() {
        boolean hasItems = getLineStopsItemCount() > 0;
        boolean hasSelection = hasCurrentSearchSelection();

        // La card deve comparire quando l'overlay è aperto e l'utente ha selezionato qualcosa,
        // anche se la lista non è ancora popolata (placeholder).
        boolean shouldShowCard = overlayVisible && (hasItems || hasSelection);
        if (overlayVisible) updateDetailsHeaderText();

        if (overlayCard.isVisible() != shouldShowCard) {
            overlayCard.setVisible(shouldShowCard);
        }

        // Se la card è visibile, scegli cosa mostrare: placeholder oppure lista
        if (shouldShowCard) {
            showDetailsCardState(hasItems);
        }

        overlayCard.revalidate();
        overlayCard.repaint();
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    /**
     * Evita errori se LineStopsView non ha getItemCount():
     * prova con reflection vari metodi; se fallisce ritorna 0.
     */
    private int getLineStopsItemCount() {
        if (lineStopsView == null) return 0;

        // 1) Prova metodi espliciti, se presenti
        String[] candidates = {
                "getItemCount",
                "getItemsCount",
                "getCount",
                "size",
                "getSize",
                "getRowCount",
                "getStopsCount",
                "getLinesCount"
        };

        for (String m : candidates) {
            try {
                Object out = lineStopsView.getClass().getMethod(m).invoke(lineStopsView);
                if (out instanceof Integer i) return Math.max(0, i);
            } catch (Exception ignored) {}
        }

        // 2) Cerca una JList dentro LineStopsView e usa la size del suo model
        try {
            int fromJList = findFirstJListModelSize(lineStopsView);
            if (fromJList >= 0) return fromJList;
        } catch (Exception ignored) {}

        // 3) Fallback: se LineStopsView espone un getModel(), usa quello
        try {
            Object model = lineStopsView.getClass().getMethod("getModel").invoke(lineStopsView);
            if (model instanceof ListModel<?> lm) return lm.getSize();
        } catch (Exception ignored) {}

        return 0;
    }

    /**
     * Cerca ricorsivamente la prima JList dentro un container e ritorna la size del suo model.
     * Ritorna -1 se non trova nessuna JList.
     */
    private int findFirstJListModelSize(Component root) {
        if (root instanceof JList<?> jl) {
            ListModel<?> m = jl.getModel();
            return (m != null) ? m.getSize() : 0;
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                int v = findFirstJListModelSize(child);
                if (v >= 0) return v;
            }
        }
        return -1;
    }

    /**
     * Utility: mostra la LISTA quando almeno un elemento è presente, altrimenti placeholder.
     */
    private void showDetailsCardState(boolean hasItems) {
        if (detailsContainer == null) return;
        CardLayout cl = (CardLayout) detailsContainer.getLayout();
        cl.show(detailsContainer, hasItems ? "LIST" : "EMPTY");
    }

    // ===================== STATE =====================

    private void setOverlayVisible(boolean visible) {
        if (this.overlayVisible == visible) return;
        this.overlayVisible = visible;

        // quando overlay è aperto -> pill visibile
        floatingModeToggle.setVisible(visible);
        // quando overlay è aperto -> anche ★ visibile
        floatingStarBtn.setVisible(visible);

        if (visible) {
            syncOverlayFromSearchBar();
            syncDetailsVisibilityFromContent(); // card solo se ci sono risultati
        } else {
            // chiudendo: spariscono card e filtri
            overlayCard.setVisible(false);
            setLineFiltersVisible(false);
            floatingStarBtn.setVisible(false);
        }

        revalidate();
        repaint();
    }

    private void syncOverlayFromSearchBar() {
        boolean isLine = (searchBarView.getCurrentMode() == SearchMode.LINE);

        // aggiorna pill floating
        if (isLine) {
            floatingModeToggle.setText("Linea");
            floatingModeToggle.setToolTipText("Clicca per passare a ricerca per FERMATA");
        } else {
            floatingModeToggle.setText("Fermata");
            floatingModeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        }
        floatingModeToggle.revalidate();
        floatingModeToggle.repaint();

        // filtri visibili solo se LINE + overlay aperto
        setLineFiltersVisible(isLine && overlayVisible);

        // allinea stato filtri dal backend
        pullLineFiltersFromSearchBar();

        overlayStarBtn.setEnabled(hasAnyCurrentSelection());
        overlayStarBtn.repaint();

        syncDetailsVisibilityFromContent();
    }

    private void installExpandOnClick(Component root) {
        attachClickListenerRecursive(root, new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!overlayVisible) setOverlayVisible(true);
                else syncOverlayFromSearchBar();

                searchBarView.getSearchField().requestFocusInWindow();
            }
        });
    }

    /**
     * ENTER nella searchbar: deve aprire l'overlay (pill + ★) e far comparire la card Dettagli
     * non appena i risultati vengono popolati.
     */
    private void installShowDetailsOnEnter() {
        try {
            JTextField field = searchBarView.getSearchField();
            if (field == null) return;

            // Evita doppio binding se per qualche motivo il costruttore venisse richiamato più volte
            for (var l : field.getActionListeners()) {
                if (l != null && l.getClass().getName().contains("DashboardView")) {
                    // non affidabile ma evita casi strani; comunque non interrompe se non trova
                }
            }

            field.addActionListener(e -> openOverlayAndWaitForDetails());
        } catch (Exception ignored) {
            // Se SearchBarView non espone la JTextField, non facciamo nulla.
        }
    }

    /**
     * Apre overlay (pill + ★ + filtri in LINE) e fa comparire Dettagli appena la lista ha elementi.
     * Usa un polling leggero per coprire il caso in cui il backend aggiorni la lista in asincrono.
     */
    private void openOverlayAndWaitForDetails() {
        // ENTER deve sempre aprire l'overlay
        if (!overlayVisible) {
            setOverlayVisible(true);
        } else {
            syncOverlayFromSearchBar();
        }

        // prova subito (magari la lista è già pronta)
        syncDetailsVisibilityFromContent();
        updateDetailsHeaderText();

        final long start = System.currentTimeMillis();
        final int timeoutMs = 1200;

        Timer t = new Timer(60, null);
        t.addActionListener(ev -> {
            // riallinea pill/filtri e visibilità card
            syncOverlayFromSearchBar();
            syncDetailsVisibilityFromContent();

            layeredPane.revalidate();
            layeredPane.repaint();

            boolean hasItems = getLineStopsItemCount() > 0;
            boolean timeout = (System.currentTimeMillis() - start) > timeoutMs;
            // se arrivano elementi smettiamo subito; se non arrivano, dopo timeout lasciamo il placeholder
            if (hasItems || timeout) t.stop();
        });
        t.setRepeats(true);
        t.start();
    }

    /** Aggiorna titolo/sottotitolo della card Dettagli in base a modalità e testo selezionato. */
    /**
     * LineStopsView (storico) mostra un header interno (es. "Dettagli" + "Linee che passano per...").
     * Ora che la card ha un header moderno, lo nascondiamo per evitare doppioni.
     */
    private void stripInternalHeaderFromLineStopsView() {
        try {
            // togli bordi esterni (se presenti)
            lineStopsView.setBorder(BorderFactory.createEmptyBorder());
            lineStopsView.setOpaque(false);

            // nasconde label tipiche dell'header interno
            hideLabelsByText(lineStopsView,
                    "Dettagli",
                    "Linee che passano per",
                    "Fermate della linea",
                    "Linee che passano",
                    "Fermate che passano",
                    "Linee:");

            // se l'header è un pannello in cima con BorderLayout, spesso è il primo componente: se dopo aver
            // nascosto le label resta un pannello vuoto, nascondiamolo.
            hideEmptyHeaderPanels(lineStopsView);
        } catch (Exception ignored) {}
    }

    private void hideLabelsByText(Component root, String... needles) {
        if (root instanceof JLabel jl) {
            String t = jl.getText();
            if (t != null) {
                String tt = t.trim();
                for (String n : needles) {
                    if (n == null) continue;
                    if (tt.equalsIgnoreCase(n) || tt.toLowerCase().startsWith(n.toLowerCase())) {
                        jl.setVisible(false);
                        jl.setText("");
                        break;
                    }
                }
            }
        }
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                hideLabelsByText(child, needles);
            }
        }
    }

    /** Nasconde pannelli che risultano vuoti (tutti i figli invisibili) dopo la rimozione dell'header. */
    private void hideEmptyHeaderPanels(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof Container cc) {
                hideEmptyHeaderPanels(cc);

                boolean anyVisible = false;
                for (Component grand : cc.getComponents()) {
                    if (grand.isVisible()) { anyVisible = true; break; }
                }

                // euristica: se è un pannello "alto" e vuoto, probabilmente era l'header
                if (!anyVisible && cc.getPreferredSize() != null && cc.getPreferredSize().height >= 22) {
                    cc.setVisible(false);
                }
            }
        }
    }
    private void updateDetailsHeaderText() {
        if (detailsTitleLabel == null || detailsSubtitleLabel == null) return;

        boolean isLine = (searchBarView.getCurrentMode() == SearchMode.LINE);
        String q = "";
        try {
            JTextField f = searchBarView.getSearchField();
            if (f != null && f.getText() != null) q = f.getText().trim();
        } catch (Exception ignored) {}

        detailsTitleLabel.setText("Dettagli");

        if (q.isBlank()) {
            detailsSubtitleLabel.setText(isLine ? "Seleziona una linea" : "Seleziona una fermata");
        } else {
            detailsSubtitleLabel.setText((isLine ? "Linea: " : "Fermata: ") + q);
        }

        detailsHeader.revalidate();
        detailsHeader.repaint();
    }

    /** Cerca la prima JList dentro LineStopsView e le applica uno stile moderno. */
    private void applyModernDetailsListStyle() {
        try {
            JList<?> jl = findFirstJList(lineStopsView);
            if (jl == null) return;

            jl.setOpaque(false);
            jl.setBackground(new Color(0, 0, 0, 0));
            jl.setSelectionBackground(new Color(230, 230, 230));
            jl.setSelectionForeground(new Color(25, 25, 25));
            // più alta: evita che “Tocca per vedere i dettagli” venga tagliato
            jl.setFixedCellHeight(76);
            jl.setCellRenderer(new ModernListCellRenderer());
            jl.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

            // rimuovi bordi/padding inutili dal viewport
            Container p = jl.getParent();
            if (p instanceof JViewport vp) {
                vp.setOpaque(false);
                vp.setBorder(null);
            }
            // Se esiste uno JScrollPane interno (in LineStopsView), uniforma look ed evita bordi doppi
            JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, jl);
            if (sp != null) {
                sp.setBorder(BorderFactory.createEmptyBorder());
                sp.setViewportBorder(BorderFactory.createEmptyBorder());
                if (sp.getViewport() != null) {
                    sp.getViewport().setOpaque(false);
                    sp.getViewport().setBorder(null);
                }
                sp.setOpaque(false);
                sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            }
        } catch (Exception ignored) {}
    }

    private JList<?> findFirstJList(Component root) {
        if (root instanceof JList<?> jl) return jl;
        if (root instanceof Container c) {
            for (Component child : c.getComponents()) {
                JList<?> v = findFirstJList(child);
                if (v != null) return v;
            }
        }
        return null;
    }

    private void attachClickListenerRecursive(Component c, MouseAdapter l) {
        c.addMouseListener(l);
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                attachClickListenerRecursive(child, l);
            }
        }
    }

    private void installGlobalClickAwayOnce() {
        if (clickAwayInstalled) return;
        clickAwayInstalled = true;

        Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent me)) return;
            if (me.getID() != MouseEvent.MOUSE_PRESSED) return;

            Object src = me.getSource();
            if (!(src instanceof Component srcComp)) return;

            Point p = SwingUtilities.convertPoint(srcComp, me.getPoint(), layeredPane);

            // ✅ FIX deterministico: se il click cade nei bounds della ★ Preferiti,
            // eseguiamo SEMPRE l'azione del bottone e consumiamo l'evento.
            // Questo copre sia il caso di overlay/glasspane sia il caso di altri listener globali
            // (es. map drag) che consumano MOUSE_PRESSED prima che JButton generi l'ActionEvent.
            if (favoritesButton != null && favoritesButton.isShowing() && favoritesButton.getBounds().contains(p)) {
                me.consume();
                SwingUtilities.invokeLater(favoritesButton::doClick);
                return;
            }

            // Se overlay non è aperto, non fare altro
            if (!overlayVisible) return;

            Component at = SwingUtilities.getDeepestComponentAt(layeredPane, p.x, p.y);

            if (at == null) {
                setOverlayVisible(false);
                return;
            }

            // clic dentro searchbar / pill / card -> non chiudere
            if (SwingUtilities.isDescendingFrom(at, searchBarView)) return;
            if (SwingUtilities.isDescendingFrom(at, floatingModeToggle)) return;
            if (SwingUtilities.isDescendingFrom(at, overlayCard)) return;

            // clic sui floating buttons -> non chiudere
            if (SwingUtilities.isDescendingFrom(at, floatingStarBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, busBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, tramBtn)) return;
            if (SwingUtilities.isDescendingFrom(at, metroBtn)) return;

            // clic sul bottone Preferiti (★ in basso a destra) -> non chiudere
            if (SwingUtilities.isDescendingFrom(at, favoritesButton)) return;

            // altrimenti chiudi overlay
            setOverlayVisible(false);
        }, AWTEvent.MOUSE_EVENT_MASK);
    }

    // ===================== BINDING FILTRI =====================

    private void setLineFiltersVisible(boolean visible) {
        busBtn.setVisible(visible);
        tramBtn.setVisible(visible);
        metroBtn.setVisible(visible);

        revalidate();
        repaint();
    }

    private void pushLineFiltersToSearchBar() {
        searchBarView.setLineFilters(busBtn.isSelected(), tramBtn.isSelected(), metroBtn.isSelected());
    }

    private void pullLineFiltersFromSearchBar() {
        busBtn.setSelected(searchBarView.isBusSelected());
        tramBtn.setSelected(searchBarView.isTramSelected());
        metroBtn.setSelected(searchBarView.isMetroSelected());
    }

    // ===================== UI HELPERS =====================

    private JButton createOverlayStarButton() {
        return new JButton() {
            private boolean hover = false;

            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFocusable(false);

                setPreferredSize(new Dimension(40, 40));
                setToolTipText("Aggiungi/Rimuovi dai preferiti");

                addActionListener(e -> {
                    if (!hasAnyCurrentSelection()) return;

                    if (!Session.isLoggedIn()) {
                        if (onRequireAuth != null) onRequireAuth.run();
                        return;
                    }

                    searchBarView.clickStar();
                    SwingUtilities.invokeLater(this::repaint);
                });

                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                int arc = 14;
                g2.setColor(hover ? new Color(245, 245, 245) : Color.WHITE);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                g2.setColor(new Color(200, 200, 200));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                boolean isFav = hasAnyCurrentSelection() && isCurrentSelectionFavorite();
                String s = isFav ? "★" : "☆";

                Color onC = new Color(0xFF, 0x7A, 0x00);
                Color offC = new Color(60, 60, 60);
                Color disabledC = new Color(150, 150, 150);
                g2.setColor(!isEnabled() ? disabledC : (isFav ? onC : offC));

                g2.setFont(getFont().deriveFont(Font.BOLD, 20f));
                FontMetrics fm = g2.getFontMetrics();

                int tx = (w - fm.stringWidth(s)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(s, tx, ty);

                g2.dispose();
            }
        };
    }

    /** True se c'è una selezione valida (lista risultati oppure selezione interna SearchBarView). */
    private boolean hasAnyCurrentSelection() {
        if (lineStopsView != null && lineStopsView.hasSelection()) return true;
        return hasCurrentSearchSelection();
    }

    private boolean hasCurrentSearchSelection() {
        String[] boolCandidates = {
                "hasSelection",
                "hasSelectedItem",
                "hasCurrentSelection",
                "isItemSelected",
                "isSelectionValid"
        };

        for (String m : boolCandidates) {
            try {
                Object out = searchBarView.getClass().getMethod(m).invoke(searchBarView);
                if (out instanceof Boolean b) return b;
            } catch (Exception ignored) {}
        }

        String[] objCandidates = {
                "getSelectedStop",
                "getSelectedLine",
                "getSelectedItem",
                "getCurrentSelection",
                "getSelectedResult"
        };

        for (String m : objCandidates) {
            try {
                Object out = searchBarView.getClass().getMethod(m).invoke(searchBarView);
                if (out != null) return true;
            } catch (Exception ignored) {}
        }

        return false;
    }

    private boolean isCurrentSelectionFavorite() {
        if (!hasAnyCurrentSelection()) return false;

        String[] candidates = {
                "isSelectedFavorite",
                "isCurrentSelectionFavorite",
                "isCurrentFavorite",
                "isStarred",
                "isStarSelected",
                "isFavoriteSelected"
        };

        for (String m : candidates) {
            try {
                Object out = searchBarView.getClass().getMethod(m).invoke(searchBarView);
                if (out instanceof Boolean b) return b;
            } catch (Exception ignored) {}
        }

        return false;
    }

    private JButton createFloatingFavoritesButton() {
        return new JButton("★") {

            private boolean hover = false;
            private double scale = 1.0;
            private double targetScale = 1.0;

            private final Timer animTimer;

            {
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFocusable(false);

                setPreferredSize(new Dimension(76, 76));
                setToolTipText("Apri preferiti");
                
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        // bypass totale: se qualcuno consuma MOUSE_PRESSED prima dell'ActionEvent,
                        // forziamo comunque il click logico del JButton.
                        if (SwingUtilities.isLeftMouseButton(e) && isEnabled()) {
                            e.consume();
                            SwingUtilities.invokeLater(() -> doClick(0));
                        }
                    }
                });

                addActionListener(e -> {
                    // Se NON loggato → apri AuthDialog
                    if (!Session.isLoggedIn()) {
                        if (onRequireAuth != null) {
                            onRequireAuth.run();
                        }
                        return;
                    }

                    // Se loggato → apri finestra Preferiti
                    if (onOpenFavorites != null) {
                        onOpenFavorites.run();
                    }
                });

                animTimer = new Timer(16, e -> {
                    double diff = targetScale - scale;
                    if (Math.abs(diff) < 0.01) {
                        scale = targetScale;
                        repaint();
                        return;
                    }
                    scale += diff * 0.2;
                    repaint();
                });
                animTimer.start();

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        targetScale = 1.08;

                        // Se qualche componente (es. MapView) imposta il cursore a livello finestra,
                        // il cursore del singolo JButton viene ignorato. Forziamo quindi la manina
                        // anche sull'ancestor Window.
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        Window win = SwingUtilities.getWindowAncestor(DashboardView.this);
                        if (win != null) win.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        targetScale = 1.0;

                        // Ripristina cursore default sulla finestra.
                        setCursor(Cursor.getDefaultCursor());
                        Window win = SwingUtilities.getWindowAncestor(DashboardView.this);
                        if (win != null) win.setCursor(Cursor.getDefaultCursor());
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                int shadowOffset = 4;
                int size = Math.min(w - shadowOffset, h - shadowOffset);
                if (size <= 0) { g2.dispose(); return; }

                int arc = (int) (size * 0.30);
                arc = Math.max(16, Math.min(arc, 26));

                int cx = size / 2;
                int cy = size / 2;

                g2.translate(shadowOffset / 2.0, shadowOffset / 2.0);
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                Color base = new Color(0xFF, 0x7A, 0x00);
                Color hoverColor = new Color(0xFF, 0x8F, 0x33);

                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, size, size, arc, arc);

                g2.setColor(new Color(255, 255, 255, 210));
                g2.setStroke(new BasicStroke(Math.max(1.5f, size * 0.03f)));
                g2.drawRoundRect(1, 1, size - 2, size - 2, arc, arc);

                float starSize = (float) (size * 0.45);
                g2.setFont(getFont().deriveFont(Font.BOLD, starSize));
                g2.setColor(Color.WHITE);

                FontMetrics fm = g2.getFontMetrics();
                String text = "★";
                int tx = (size - fm.stringWidth(text)) / 2;
                int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);

                if (DashboardView.this.favoritesCount > 0) {
                    int badgeSize = (int) Math.max(14, size * 0.26);
                    int bx = size - badgeSize - 4;
                    int by = 4;

                    g2.setColor(new Color(220, 20, 60));
                    g2.fillOval(bx, by, badgeSize, badgeSize);

                    g2.setColor(Color.WHITE);
                    float badgeFontSize = (float) Math.max(9, badgeSize * 0.45);
                    g2.setFont(getFont().deriveFont(Font.BOLD, badgeFontSize));
                    String countStr = String.valueOf(DashboardView.this.favoritesCount);

                    FontMetrics bfm = g2.getFontMetrics();
                    int btx = bx + (badgeSize - bfm.stringWidth(countStr)) / 2;
                    int bty = by + (badgeSize - bfm.getHeight()) / 2 + bfm.getAscent();
                    g2.drawString(countStr, btx, bty);
                }

                g2.dispose();
            }
        };
    }

    // ========= GETTERS =========

    public MapView getMapView() { return mapView; }
    public SearchBarView getSearchBarView() { return searchBarView; }
    public LineStopsView getLineStopsView() { return lineStopsView; }
    public JButton getFavoritesButton() { return favoritesButton; }

    public void setFavoritesCount(int count) {
        this.favoritesCount = Math.max(0, count);
        if (favoritesButton != null) favoritesButton.repaint();
    }

    private static class RoundedShadowPanel extends JPanel {
        @Override public boolean isOpaque() { return false; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setComposite(AlphaComposite.SrcOver);

            int w = getWidth();
            int h = getHeight();
            int arc = 28;

            // shadow morbida
            for (int i = 0; i < 10; i++) {
                float alpha = (10 - i) / 200f; // soft
                g2.setColor(new Color(0, 0, 0, Math.round(alpha * 255)));
                g2.fillRoundRect(6 + i, 6 + i, w - 12 - (i * 2), h - 12 - (i * 2), arc, arc);
            }

            // card surface
            int inset = 10;
            int cw = w - inset;
            int ch = h - inset;

            g2.setColor(new Color(250, 250, 250, 245));
            g2.fillRoundRect(0, 0, cw, ch, arc, arc);

            // border leggero
            g2.setColor(new Color(210, 210, 210, 170));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, cw - 1, ch - 1, arc, arc);

            g2.dispose();

            super.paintComponent(g);
        }

        @Override
        public Insets getInsets() {
            return new Insets(0, 0, 10, 10);
        }
    }

    private static class PillToggleButton extends JToggleButton {
        private boolean hover = false;

        PillToggleButton(String text) {
            super(text);

            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);

            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(getFont().deriveFont(Font.BOLD, 16f));

            Dimension fixed = new Dimension(120, 40);
            setPreferredSize(fixed);
            setMinimumSize(fixed);
            setMaximumSize(fixed);

            setMargin(new Insets(6, 14, 6, 14));
            setFocusable(false);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            Color base = Color.WHITE;
            Color over = new Color(245, 245, 245);

            g2.setColor(hover ? over : base);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.setColor(new Color(170, 170, 170));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc);

            g2.setColor(new Color(25, 25, 25));
            g2.setFont(getFont());

            FontMetrics fm = g2.getFontMetrics();
            String t = getText();
            int tx = (w - fm.stringWidth(t)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(t, tx, ty);

            g2.dispose();
        }
    }

    private static class IconToggleButton extends JToggleButton {
        private final Image iconOff;
        private final Image iconOn;
        private boolean hover = false;

        IconToggleButton(String iconOffPath, String iconOnPath, Dimension size, String tooltip) {
            setToolTipText(tooltip);

            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);

            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);

            iconOff = load(iconOffPath);
            iconOn  = load(iconOnPath);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        private Image load(String path) {
            try {
                var url = DashboardView.class.getResource(path);
                if (url != null) return new ImageIcon(url).getImage();
            } catch (Exception ignored) {}
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = Math.max(12, Math.min(w, h) / 3);

            Shape rr = new RoundRectangle2D.Double(0, 0, w, h, arc, arc);

            g2.setColor(Color.WHITE);
            g2.fill(rr);

            g2.setColor(hover ? new Color(165, 165, 165) : new Color(190, 190, 190));
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(rr);

            Image img = isSelected() ? iconOn : iconOff;
            if (img != null) {
                int pad = Math.max(9, Math.min(w, h) / 4);
                int iw = Math.max(1, w - pad * 2);
                int ih = Math.max(1, h - pad * 2);

                int sw = img.getWidth(null);
                int sh = img.getHeight(null);

                if (sw > 0 && sh > 0) {
                    double s = Math.min((double) iw / sw, (double) ih / sh);
                    int dw = (int) Math.round(sw * s);
                    int dh = (int) Math.round(sh * s);
                    int x = (w - dw) / 2;
                    int y = (h - dh) / 2;
                    g2.drawImage(img, x, y, dw, dh, null);
                }
            }

            g2.dispose();
        }
    }

    /**
     * Impostato dal Main: se l'utente clicca ★ senza essere loggato, apriamo l'AuthDialog.
     */
    public void setOnRequireAuth(Runnable onRequireAuth) {
        this.onRequireAuth = onRequireAuth;
    }

    /**
     * Impostato dal Main: quando l'utente clicca la ★ in basso a destra, apriamo la finestra Preferiti.
     */
    public void setOnOpenFavorites(Runnable onOpenFavorites) {
        this.onOpenFavorites = onOpenFavorites;
    }

    /** Header con barra accent a sinistra, in stile "app". */
    private static class DetailsHeaderPanel extends JPanel {
        private final JLabel title;
        private final JLabel subtitle;

        DetailsHeaderPanel(JLabel title, JLabel subtitle) {
            super(new BorderLayout());
            this.title = title;
            this.subtitle = subtitle;
            setOpaque(false);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(title);
            text.add(Box.createVerticalStrut(4));
            text.add(subtitle);

            add(text, BorderLayout.CENTER);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int h = getHeight();
            int barW = 6;
            int arc = 10;
            int barX = 6;     // stacca la barra dal bordo della card
            int topPad = 6;   // stacca dall’alto

            // accent bar (orange)
            g2.setColor(new Color(0xFF, 0x7A, 0x00));
            g2.fillRoundRect(barX, topPad, barW, Math.max(10, h - (topPad * 2)), arc, arc);

            g2.dispose();
        }

        @Override
        public Insets getInsets() {
            // lascia spazio alla barra accent + aria a sinistra/alto
            return new Insets(4, 22, 4, 0);
        }
    }

    /** Renderer moderno: riga alta, testo grande, badge/dot a sinistra. */
    private static class ModernListCellRenderer extends JPanel implements ListCellRenderer<Object> {

        private final JLabel main;
        private final JLabel sub;
        private boolean selected = false;

        ModernListCellRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout());

            main = new JLabel();
            main.setOpaque(false);
            main.setForeground(new Color(20, 20, 20));
            main.setFont(main.getFont().deriveFont(Font.BOLD, 19f));

            sub = new JLabel();
            sub.setOpaque(false);
            sub.setForeground(new Color(110, 110, 110));
            sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 14.5f));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0)); // spazio per il dot vicino al titolo
            text.add(main);
            text.add(Box.createVerticalStrut(2));
            text.add(sub);

            add(text, BorderLayout.CENTER);
            // padding complessivo: ordinato e con aria, senza tagliare il sottotitolo
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 18));
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            this.selected = isSelected;

            String s = (value == null) ? "" : value.toString();

            // split semplice: se c'è una parentesi, mettiamo il codice nel sottotitolo
            String title = s;
            // placeholder: verrà sostituito con dati realtime dal backend
            String subtitle = "Prossimo: tra —";
            int p = s.lastIndexOf('(');
            if (p > 0 && s.endsWith(")")) {
                title = s.substring(0, p).trim();
                subtitle = s.substring(p).trim();
            }

            main.setText(title);
            sub.setText(subtitle);

            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 16;

            // background card-row
            if (selected) {
                g2.setColor(new Color(230, 230, 230));
            } else {
                g2.setColor(new Color(255, 255, 255, 0));
            }
            g2.fillRoundRect(4, 2, w - 8, h - 4, arc, arc);

            // dot vicino al titolo (allineato alla prima riga, non tra titolo e sottotitolo)
            int dot = 10;
            int dx = 14; // vicino al testo, coerente con il padding sinistro
            FontMetrics tfm = getFontMetrics(main.getFont());
            int ascent = tfm.getAscent();
            int topPad = 12; // deve combaciare con il padding top della border
            int dy = topPad + Math.max(0, (ascent - dot) / 2);
            g2.setColor(new Color(0xFF, 0x7A, 0x00));
            g2.fillOval(dx, dy, dot, dot);

            g2.dispose();
            super.paintComponent(g);
        }
    }
}