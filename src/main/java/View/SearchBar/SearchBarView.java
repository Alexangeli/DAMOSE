package View.SearchBar;

import Controller.SearchMode.SearchMode;
import Model.Favorites.FavoriteItem;
import Model.Favorites.FavoriteType;
import Model.Map.RouteDirectionOption;
import Model.Points.StopModel;
import Model.User.Session;
import Service.User.Fav.FavoritesService;
import View.User.Account.AuthDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionListener;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Barra di ricerca principale della dashboard.
 *
 * Responsabilità:
 * - gestisce due modalità di ricerca: per fermata (STOP) e per linea (LINE)
 * - mostra suggerimenti in tempo reale con debounce e navigazione da tastiera
 * - permette di filtrare i risultati in modalità LINE (bus/tram/metro)
 * - gestisce il bottone preferiti (stella) con integrazione login e FavoritesService
 *
 * Note di progetto:
 * - la richiesta suggerimenti parte solo dopo 3 caratteri e con debounce (500 ms)
 * - in modalità compact i suggerimenti vengono mostrati in un popup sotto la search bar
 */
public class SearchBarView extends JPanel {

    // ========================== UI COMPONENTS ==========================

    /** Toggle pill per cambiare modalità (Fermata/Linea). */
    private final JToggleButton modeToggle;

    /** Campo di input con placeholder custom. */
    private final JTextField searchField;

    /** Bottone lente per avviare ricerca o confermare suggerimento. */
    private final JButton searchButton;

    /** View che contiene lista suggerimenti e logica di selezione. */
    private final SuggestionsView suggestions;

    /** Bottone X per pulire testo (visibile solo quando c'è input). */
    private final JButton clearButton;

    /** Bottone stella per aggiungere/rimuovere preferiti. */
    private final JButton favStarBtn;

    /** Pannello filtri (visibile solo in modalità LINE e non in compact). */
    private final JPanel lineFiltersPanel;

    /** Filtro bus per suggerimenti linea. */
    private final IconToggleButton busBtn;

    /** Filtro tram per suggerimenti linea. */
    private final IconToggleButton tramBtn;

    /** Filtro metro per suggerimenti linea. */
    private final IconToggleButton metroBtn;

    /** Service locale per gestione preferiti. */
    private final FavoritesService favoritesService = new FavoritesService();

    // ============================ CALLBACKS ============================

    /** Callback cambio modalità STOP/LINE. */
    private Consumer<SearchMode> onModeChanged;

    /** Callback ricerca "manuale" (quando non si conferma un suggerimento). */
    private Consumer<String> onSearch;

    /** Callback testo cambiato (debounced) per richiedere suggerimenti al controller. */
    private Consumer<String> onTextChanged;

    /** Callback selezione suggerimento fermata. */
    private Consumer<StopModel> onSuggestionSelected;

    /** Callback selezione suggerimento linea+direzione. */
    private Consumer<RouteDirectionOption> onRouteDirectionSelected;

    // ============================ STATE ===============================

    /** Modalità corrente. */
    private SearchMode currentMode = SearchMode.STOP;

    /** Oggetto attualmente "stellabile" (StopModel o RouteDirectionOption). */
    private Object currentStarTarget = null;

    /**
     * Flag per evitare eventi testo quando il campo viene aggiornato programmaticamente
     * (es. quando si conferma un suggerimento e si scrive il testo nel field).
     */
    private boolean suppressTextEvents = false;

    /** Timer di debounce per chiamare onTextChanged con ritardo controllato. */
    private final Timer debounceTimer;

    /** Cache delle opzioni linea più recenti (serve per refilter/sort senza richiamare il controller). */
    private List<RouteDirectionOption> lastLineOptions = List.of();

    /** Se true: mostra solo la riga di ricerca (suggerimenti in popup). */
    private final boolean compactOnlySearchRow;

    // popup suggerimenti (solo compact)
    /** Popup che contiene SuggestionsView quando la searchbar è in modalità compact. */
    private JPopupMenu suggestionsPopup;

    /** Container grafico arrotondato del popup suggerimenti. */
    private JComponent suggestionsPopupContainer;

    /** Componente usato come ancora per posizionare il popup (rounded search panel). */
    private Component popupAnchor;

    /**
     * Marker usato per evitare di installare più volte lo stesso listener globale
     * sulla finestra owner (click-away).
     */
    private interface ClickAwayMarker extends MouseListener {}

    /**
     * Aggiorna la visibilità del bottone clear (X) in base al testo presente nel campo.
     * Viene chiamato su ogni variazione del documento.
     */
    private void updateClearButtonVisibility() {
        String t = searchField.getText();
        boolean show = (t != null && !t.isBlank());
        if (clearButton.isVisible() != show) {
            clearButton.setVisible(show);
            clearButton.revalidate();
            clearButton.repaint();
        }
    }

    /** Crea una search bar in modalità normale (non compact). */
    public SearchBarView() {
        this(false);
    }

    /**
     * Crea una search bar.
     *
     * @param compactOnlySearchRow se true, mostra solo la riga ricerca e usa popup per suggerimenti
     */
    public SearchBarView(boolean compactOnlySearchRow) {
        this.compactOnlySearchRow = compactOnlySearchRow;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setOpaque(false);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        // ===================== ROW MODALITÀ + FILTRI + STAR =====================
        JPanel modeRow = new JPanel(new BorderLayout());
        modeRow.setOpaque(false);

        JPanel modeLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modeLeft.setOpaque(false);

        modeToggle = new PillToggleButton("Fermata");
        modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        modeToggle.addActionListener(e -> toggleMode());
        modeLeft.add(modeToggle);

        lineFiltersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineFiltersPanel.setOpaque(false);

        Dimension toggleSize = new Dimension(40, 40);

        // OFF = bianco, ON = colorato
        busBtn   = new IconToggleButton("/icons/bus.png",   "/icons/busblu.png",     toggleSize, "Bus");
        tramBtn  = new IconToggleButton("/icons/tram.png",  "/icons/tramverde.png",  toggleSize, "Tram");
        metroBtn = new IconToggleButton("/icons/metro.png", "/icons/metrorossa.png", toggleSize, "Metro");

        busBtn.setSelected(true);
        tramBtn.setSelected(true);
        metroBtn.setSelected(true);

        busBtn.addActionListener(e -> refilterVisibleLineSuggestions());
        tramBtn.addActionListener(e -> refilterVisibleLineSuggestions());
        metroBtn.addActionListener(e -> refilterVisibleLineSuggestions());

        lineFiltersPanel.add(busBtn);
        lineFiltersPanel.add(tramBtn);
        lineFiltersPanel.add(metroBtn);

        JPanel leftAndFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftAndFilters.setOpaque(false);
        leftAndFilters.add(modeLeft);
        leftAndFilters.add(lineFiltersPanel);

        favStarBtn = createRoundedAnimatedStarButton();
        JPanel starRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        starRight.setOpaque(false);
        starRight.add(favStarBtn);

        modeRow.add(leftAndFilters, BorderLayout.WEST);
        modeRow.add(starRight, BorderLayout.EAST);

        // ===================== ROW SEARCH =====================
        searchField = new PlaceholderTextField("Cerca...");
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        searchField.setOpaque(false);

        searchButton = new MagnifierButton();
        searchButton.setToolTipText("Cerca");

        // Bottone X
        clearButton = new ClearButton();
        clearButton.setToolTipText("Pulisci");
        clearButton.setVisible(false);
        clearButton.addActionListener(e -> {
            suppressTextEvents = true;
            searchField.setText("");
            suppressTextEvents = false;

            hideSuggestions();
            setStarTarget(null);
            updateClearButtonVisibility();

            searchField.requestFocusInWindow();
            if (onClear != null) onClear.run();
        });

        RoundedSearchPanel searchPanel = new RoundedSearchPanel();
        searchPanel.setLayout(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(searchField, BorderLayout.CENTER);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightButtons.setOpaque(false);
        rightButtons.add(clearButton);
        rightButtons.add(searchButton);

        searchPanel.add(rightButtons, BorderLayout.EAST);

        // Se non compact, mostriamo anche la riga modalità+filtri+stella.
        if (!this.compactOnlySearchRow) {
            topPanel.add(modeRow);
            topPanel.add(Box.createVerticalStrut(8));
        }
        topPanel.add(searchPanel);

        add(topPanel, BorderLayout.NORTH);

        // ===================== SUGGESTIONS VIEW =====================
        suggestions = new SuggestionsView();

        if (!this.compactOnlySearchRow) {
            add(suggestions.getPanel(), BorderLayout.CENTER);
        } else {
            installSuggestionsPopup(searchPanel);
        }

        // Keybindings (frecce, enter, esc) sul campo testo.
        installSearchFieldKeyBindings();

        setStarTarget(null);
        updateLineFiltersVisibility();

        // ====================== DEBOUNCE ======================
        debounceTimer = new Timer(500, e -> {
            if (onTextChanged == null) return;

            String text = searchField.getText();
            if (text == null || text.isBlank()) {
                hideSuggestions();
                return;
            }

            String trimmed = text.trim();
            if (trimmed.length() < 3) {
                hideSuggestions();
                return;
            }

            onTextChanged.accept(trimmed);
        });
        debounceTimer.setRepeats(false);

        // ====================== EVENTS ======================

        searchButton.addActionListener(e -> handleSearchButton());

        // Selezione in SuggestionsView: aggiorna target stella e notifica controller in base alla modalità.
        suggestions.addListSelectionListener((ListSelectionListener) e -> {
            if (e.getValueIsAdjusting()) return;
            Object value = suggestions.getSelectedValue();
            if (value == null) return;

            setStarTarget(value);

            if (currentMode == SearchMode.STOP && value instanceof StopModel stop) {
                if (onSuggestionSelected != null) onSuggestionSelected.accept(stop);
            } else if (currentMode == SearchMode.LINE && value instanceof RouteDirectionOption opt) {
                if (onRouteDirectionSelected != null) onRouteDirectionSelected.accept(opt);
            }
        });

        // Doppio click: conferma selezione suggerimento.
        suggestions.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) confirmSelectedSuggestion();
            }
        });

        // Listener testo: gestisce clear button + debounce + condizioni minime (>= 3 char).
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onDocChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onDocChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onDocChanged(); }

            private void onDocChanged() {
                updateClearButtonVisibility();
                if (suppressTextEvents) return;

                String text = searchField.getText();
                if (text == null || text.isBlank()) {
                    hideSuggestions();
                    debounceTimer.stop();
                    return;
                }

                String trimmed = text.trim();
                if (trimmed.length() < 3) {
                    hideSuggestions();
                    debounceTimer.stop();
                    return;
                }

                if (debounceTimer.isRunning()) debounceTimer.stop();
                debounceTimer.start();
            }
        });
    }

    // ==================================================================
    // KEYBINDINGS SU SEARCHFIELD (FRECCE + ENTER + ESC)
    // ==================================================================

    /**
     * Installa i comandi da tastiera sul campo di ricerca:
     * - frecce su/giù per muovere la selezione nei suggerimenti
     * - invio per confermare suggerimento o lanciare ricerca
     * - esc per chiudere i suggerimenti
     */
    private void installSearchFieldKeyBindings() {
        InputMap im = searchField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = searchField.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "sbv_down");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "sbv_up");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sbv_enter");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "sbv_esc");

        am.put("sbv_down", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!suggestions.hasSuggestions()) return;

                if (compactOnlySearchRow && suggestionsPopup != null && !suggestionsPopup.isVisible()) {
                    showSuggestionsPopupUnderAnchor();
                }

                moveSuggestionSelection(+1);
            }
        });

        am.put("sbv_up", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!suggestions.hasSuggestions()) return;

                if (compactOnlySearchRow && suggestionsPopup != null && !suggestionsPopup.isVisible()) {
                    showSuggestionsPopupUnderAnchor();
                }

                moveSuggestionSelection(-1);
            }
        });

        am.put("sbv_enter", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (suggestions.hasSuggestions()) {
                    if (suggestions.getSelectedIndex() < 0) suggestions.selectFirstIfNone();
                    confirmSelectedSuggestion();
                } else {
                    triggerSearch();
                }
            }
        });

        am.put("sbv_esc", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                hideSuggestions();
            }
        });
    }

    /**
     * Muove la selezione nella lista suggerimenti in modo ciclico.
     *
     * @param delta +1 avanti, -1 indietro
     */
    private void moveSuggestionSelection(int delta) {
        int size = suggestions.size();
        if (size <= 0) return;

        int idx = suggestions.getSelectedIndex();
        if (idx < 0) idx = 0;

        idx = (idx + delta) % size;
        if (idx < 0) idx = size - 1;

        suggestions.setSelectedIndex(idx);
        setStarTarget(suggestions.getSelectedValue());
    }

    // ==================================================================
    // MODE (toggle Fermata/Linea)
    // ==================================================================

    /**
     * Alterna la modalità di ricerca tra STOP e LINE e notifica il controller.
     * Resetta suggerimenti e selezione stella per evitare incongruenze.
     */
    private void toggleMode() {
        if (currentMode == SearchMode.STOP) {
            currentMode = SearchMode.LINE;
            modeToggle.setText("Linea");
            modeToggle.setToolTipText("Clicca per passare a ricerca per FERMATA");
        } else {
            currentMode = SearchMode.STOP;
            modeToggle.setText("Fermata");
            modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        }

        updateLineFiltersVisibility();
        hideSuggestions();
        setStarTarget(null);

        if (onModeChanged != null) onModeChanged.accept(currentMode);

        modeToggle.revalidate();
        modeToggle.repaint();
    }

    /**
     * Mostra/nasconde i filtri linea.
     * In compact non vengono mostrati per mantenere la barra minimale.
     */
    private void updateLineFiltersVisibility() {
        if (compactOnlySearchRow) {
            lineFiltersPanel.setVisible(false);
            return;
        }
        lineFiltersPanel.setVisible(currentMode == SearchMode.LINE);
        revalidate();
        repaint();
    }

    /**
     * @return modalità di ricerca corrente
     */
    public SearchMode getCurrentMode() {
        return currentMode;
    }

    // ==================================================================
    // SEARCH / CONFIRM
    // ==================================================================

    /**
     * Gestione click lente:
     * - se ci sono suggerimenti visibili, conferma il selezionato
     * - altrimenti esegue la ricerca sul testo attuale
     */
    private void handleSearchButton() {
        if (suggestions.isVisible() && suggestions.hasSuggestions()) {
            if (suggestions.getSelectedIndex() < 0) suggestions.selectFirstIfNone();
            confirmSelectedSuggestion();
        } else {
            triggerSearch();
        }
    }

    /**
     * Avvia la ricerca delegando al controller, usando il testo corrente del campo.
     */
    private void triggerSearch() {
        if (onSearch == null) return;
        String text = searchField.getText();
        if (text == null || text.isBlank()) return;
        onSearch.accept(text.trim());
    }

    /**
     * Conferma il suggerimento selezionato e aggiorna il campo testo con un testo "canonico".
     * In base alla modalità, invoca la callback corretta.
     */
    private void confirmSelectedSuggestion() {
        Object value = suggestions.getSelectedValue();
        if (value == null) return;

        setStarTarget(value);

        if (currentMode == SearchMode.STOP && value instanceof StopModel stop) {
            hideSuggestions();

            suppressTextEvents = true;
            searchField.setText(stop.getName());
            suppressTextEvents = false;

            if (onSuggestionSelected != null) onSuggestionSelected.accept(stop);

            searchField.requestFocusInWindow();
            searchField.setCaretPosition(searchField.getText().length());

        } else if (currentMode == SearchMode.LINE && value instanceof RouteDirectionOption opt) {
            hideSuggestions();

            String lineText = safe(opt.getRouteShortName());
            if (opt.getHeadsign() != null && !opt.getHeadsign().isBlank()) {
                lineText += " → " + opt.getHeadsign();
            }

            suppressTextEvents = true;
            searchField.setText(lineText);
            suppressTextEvents = false;

            if (onRouteDirectionSelected != null) onRouteDirectionSelected.accept(opt);

            searchField.requestFocusInWindow();
            searchField.setCaretPosition(searchField.getText().length());
        }
    }

    // ==================================================================
    // CONTROLLER -> VIEW
    // ==================================================================

    /**
     * Nasconde i suggerimenti (e chiude il popup se siamo in modalità compact).
     */
    public void hideSuggestions() {
        suggestions.hide();
        if (compactOnlySearchRow && suggestionsPopup != null) {
            suggestionsPopup.setVisible(false);
        }
    }

    /**
     * Mostra suggerimenti di fermate, rimuovendo duplicati locali.
     *
     * @param stops lista fermate suggerite dal controller
     */
    public void showStopSuggestions(List<StopModel> stops) {
        List<StopModel> dedup = dedupStops(stops);
        suggestions.showStops(dedup);
        setStarTarget(suggestions.getSelectedValue());
        ensureSuggestionsVisibleIfAny();
    }

    /**
     * Mostra suggerimenti di linee, con dedup + filtri + ordinamento.
     *
     * @param options lista opzioni linea/direzione suggerite dal controller
     */
    public void showLineSuggestions(List<RouteDirectionOption> options) {
        List<RouteDirectionOption> dedup = dedupLines(options);
        lastLineOptions = (dedup == null) ? List.of() : new ArrayList<>(dedup);
        applyLineFiltersAndSorting();
        ensureSuggestionsVisibleIfAny();
    }

    /**
     * Se ci sono suggerimenti, assicura la loro visibilità.
     * In compact apre il popup sotto l'ancora, altrimenti aggiorna il layout.
     */
    private void ensureSuggestionsVisibleIfAny() {
        if (!suggestions.hasSuggestions()) {
            hideSuggestions();
            return;
        }

        if (compactOnlySearchRow) {
            showSuggestionsPopupUnderAnchor();
        } else {
            revalidate();
            repaint();
        }
    }

    // ==================================================================
    // DEDUP HELPERS
    // ==================================================================

    /**
     * Dedup fermate: preferisce una chiave stabile (code) se disponibile, altrimenti usa il nome normalizzato.
     *
     * @param in lista input
     * @return lista deduplicata mantenendo l'ordine di prima occorrenza
     */
    private List<StopModel> dedupStops(List<StopModel> in) {
        if (in == null || in.isEmpty()) return List.of();

        Map<String, StopModel> map = new LinkedHashMap<>();
        for (StopModel s : in) {
            if (s == null) continue;

            String key = (s.getCode() != null && !s.getCode().isBlank())
                    ? ("code:" + s.getCode().trim())
                    : ("name:" + safe(s.getName()).trim().toLowerCase());

            map.putIfAbsent(key, s);
        }
        return new ArrayList<>(map.values());
    }

    /**
     * Dedup linee: usa routeId + directionId + headsign come chiave; se routeId manca usa routeShortName.
     *
     * @param in lista input
     * @return lista deduplicata mantenendo l'ordine di prima occorrenza
     */
    private List<RouteDirectionOption> dedupLines(List<RouteDirectionOption> in) {
        if (in == null || in.isEmpty()) return List.of();

        Map<String, RouteDirectionOption> map = new LinkedHashMap<>();
        for (RouteDirectionOption o : in) {
            if (o == null) continue;

            String key = safe(o.getRouteId()).trim()
                    + "|" + o.getDirectionId()
                    + "|" + safe(o.getHeadsign()).trim();

            if (key.startsWith("|")) {
                key = safe(o.getRouteShortName()).trim()
                        + "|" + o.getDirectionId()
                        + "|" + safe(o.getHeadsign()).trim();
            }

            map.putIfAbsent(key, o);
        }
        return new ArrayList<>(map.values());
    }

    // ==================================================================
    // FILTRI + SORT LINEE
    // ==================================================================

    /**
     * Ricalcola i suggerimenti visibili in modalità LINE applicando i filtri selezionati.
     */
    private void refilterVisibleLineSuggestions() {
        if (currentMode != SearchMode.LINE) return;
        applyLineFiltersAndSorting();
        ensureSuggestionsVisibleIfAny();
    }

    /**
     * Applica filtri bus/tram/metro e poi ordina i risultati con una strategia "smart" basata sulla query.
     */
    private void applyLineFiltersAndSorting() {
        List<RouteDirectionOption> filtered = filterLineOptions(lastLineOptions);

        String q = (searchField.getText() == null) ? "" : searchField.getText().trim();
        filtered = new ArrayList<>(filtered);
        filtered.sort(lineSmartComparator(q));

        suggestions.showLineOptions(filtered);
        setStarTarget(suggestions.getSelectedValue());
    }

    /** Tipologia linea derivata dal route_type GTFS. */
    private enum LineKind { BUS, TRAM, METRO, OTHER }

    /**
     * Classifica un'opzione linea in base al route_type (0 tram, 1 metro, 3 bus).
     *
     * @param opt opzione linea
     * @return categoria linea
     */
    private LineKind classify(RouteDirectionOption opt) {
        if (opt == null) return LineKind.OTHER;
        int t = opt.getRouteType();
        return switch (t) {
            case 0 -> LineKind.TRAM;
            case 1 -> LineKind.METRO;
            case 3 -> LineKind.BUS;
            default -> LineKind.OTHER;
        };
    }

    /**
     * Applica i filtri selezionati alla lista di opzioni.
     * Se nessun filtro è attivo, ritorna lista vuota.
     *
     * @param in lista opzioni
     * @return lista filtrata
     */
    private List<RouteDirectionOption> filterLineOptions(List<RouteDirectionOption> in) {
        if (in == null || in.isEmpty()) return List.of();

        boolean busOn = busBtn.isSelected();
        boolean tramOn = tramBtn.isSelected();
        boolean metroOn = metroBtn.isSelected();

        if (!busOn && !tramOn && !metroOn) return List.of();

        List<RouteDirectionOption> out = new ArrayList<>(in.size());
        for (RouteDirectionOption opt : in) {
            LineKind kind = classify(opt);
            boolean keep =
                    (kind == LineKind.BUS && busOn) ||
                            (kind == LineKind.TRAM && tramOn) ||
                            (kind == LineKind.METRO && metroOn);
            if (keep) out.add(opt);
        }
        return out;
    }

    /**
     * Comparator per ordinare suggerimenti linea in modo più utile quando la query è numerica.
     * Priorità:
     * - bucket "migliore match" (esatto, prefisso, ecc.)
     * - poi numero iniziale della linea
     * - poi short name e headsign per stabilità
     *
     * @param queryText testo ricerca
     * @return comparator per RouteDirectionOption
     */
    private Comparator<RouteDirectionOption> lineSmartComparator(String queryText) {
        final String q = (queryText == null) ? "" : queryText.trim();
        final Integer qNum = tryParseInt(q);

        return Comparator
                .comparingInt((RouteDirectionOption o) -> bucketFor(o, q, qNum))
                .thenComparingInt(o -> parseLeadingInt(safe(o.getRouteShortName())))
                .thenComparing(o -> safe(o.getRouteShortName()))
                .thenComparing(o -> safe(o.getHeadsign()));
    }

    /**
     * Calcola un bucket di ordinamento per query numerica:
     * - 0 match esatto (solo numeri)
     * - 1/2/3 prefisso (più corto = più vicino)
     * - 9/999 per casi non numerici o non matchabili
     */
    private int bucketFor(RouteDirectionOption opt, String q, Integer qNum) {
        if (qNum == null) return 999;
        if (opt == null) return 999;

        String shortName = safe(opt.getRouteShortName()).trim();
        int n = parseLeadingInt(shortName);
        if (n < 0) return 999;

        String qStr = String.valueOf(qNum);
        String nStr = String.valueOf(n);

        if (shortName.matches("^\\d+$") && shortName.equals(qStr)) return 0;

        if (nStr.startsWith(qStr)) {
            int diff = nStr.length() - qStr.length();
            if (diff == 1) return 1;
            if (diff == 2) return 2;
            return 3;
        }

        return 9;
    }

    /**
     * Estrae l'intero iniziale da una stringa (es. "64 Express" -> 64).
     *
     * @param s stringa da analizzare
     * @return intero iniziale oppure -1 se assente/non valido
     */
    private int parseLeadingInt(String s) {
        if (s == null) return -1;
        s = s.trim();
        if (s.isEmpty()) return -1;

        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == 0) return -1;

        try {
            return Integer.parseInt(s.substring(0, i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Prova a interpretare una stringa come numero intero composto solo da cifre.
     *
     * @param s stringa input
     * @return intero parsato o null se non è un numero puro
     */
    private Integer tryParseInt(String s) {
        if (s == null) return null;
        s = s.trim();
        if (!s.matches("^\\d+$")) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param s stringa (può essere null)
     * @return stringa non nulla
     */
    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    // ==================================================================
    // ★ STAR BUTTON
    // ==================================================================

    /**
     * Crea il bottone stella con animazione leggera (hover scale) e paint custom.
     * La stella piena/vuota dipende da login e dalla presenza nei preferiti.
     *
     * @return bottone configurato
     */
    private JButton createRoundedAnimatedStarButton() {
        return new JButton() {

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

                setPreferredSize(new Dimension(42, 42));
                setMinimumSize(new Dimension(42, 42));
                setMaximumSize(new Dimension(42, 42));

                setToolTipText("Aggiungi/Rimuovi dai preferiti");

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
                    @Override public void mouseEntered(MouseEvent e) { hover = true; targetScale = 1.06; }
                    @Override public void mouseExited(MouseEvent e)  { hover = false; targetScale = 1.0; }
                });

                addActionListener(e -> onStarClicked());
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                int shadowOffset = 2;
                int size = Math.min(w - shadowOffset, h - shadowOffset);
                if (size <= 0) { g2.dispose(); return; }

                int arc = (int) (size * 0.30);
                arc = Math.max(12, Math.min(arc, 18));

                int cx = size / 2;
                int cy = size / 2;

                g2.translate(shadowOffset / 2.0, shadowOffset / 2.0);
                g2.translate(cx, cy);
                g2.scale(scale, scale);
                g2.translate(-cx, -cy);

                Color base = Color.WHITE;
                Color hoverColor = new Color(245, 245, 245);

                g2.setColor(hover ? hoverColor : base);
                g2.fillRoundRect(0, 0, size, size, arc, arc);

                g2.setColor(new Color(190, 190, 190));
                g2.setStroke(new BasicStroke(Math.max(1.2f, size * 0.045f)));
                g2.drawRoundRect(1, 1, size - 2, size - 2, arc, arc);

                String star = getStarGlyph();
                float starSize = (float) (size * 0.62);
                g2.setFont(getFont().deriveFont(Font.PLAIN, starSize));

                Color starColor = isEnabled() ? new Color(60, 60, 60) : new Color(170, 170, 170);
                g2.setColor(starColor);

                FontMetrics fm = g2.getFontMetrics();
                int tx = (size - fm.stringWidth(star)) / 2;
                int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(star, tx, ty);

                g2.dispose();
            }

            /**
             * Determina quale glifo mostrare:
             * - stella vuota se non c'è target o se non loggati
             * - stella piena se il target è nei preferiti
             */
            private String getStarGlyph() {
                if (currentStarTarget == null) return "☆";
                if (!Session.isLoggedIn()) return "☆";
                return isFavorite(currentStarTarget) ? "★" : "☆";
            }
        };
    }

    /**
     * Aggiorna il target su cui lavora la stella e abilita/disabilita il bottone.
     *
     * @param value target corrente (StopModel o RouteDirectionOption)
     */
    private void setStarTarget(Object value) {
        currentStarTarget = value;
        favStarBtn.setEnabled(currentStarTarget != null);
        favStarBtn.repaint();
    }

    /**
     * Gestione click sulla stella:
     * - se non loggati apre la dialog di autenticazione
     * - se loggati, alterna presenza nei preferiti
     */
    private void onStarClicked() {
        if (currentStarTarget == null) return;

        if (!Session.isLoggedIn()) {
            Window w = SwingUtilities.getWindowAncestor(this);
            AuthDialog dlg = new AuthDialog(w, () -> favStarBtn.repaint());
            dlg.setVisible(true);
            return;
        }

        toggleFavorite(currentStarTarget);
        favStarBtn.repaint();
    }

    /**
     * Verifica se il target è presente nei preferiti, delegando a FavoritesService.
     *
     * @param value target (StopModel o RouteDirectionOption)
     * @return true se presente nei preferiti
     */
    private boolean isFavorite(Object value) {
        if (value instanceof StopModel stop) {
            String code = stop.getCode();
            if (code == null || code.isBlank()) return false;

            return favoritesService.getStops().stream().anyMatch(f ->
                    f.getType() == FavoriteType.STOP && code.equals(f.getStopId())
            );
        }

        if (value instanceof RouteDirectionOption opt) {
            return favoritesService.getLines().stream().anyMatch(f ->
                    f.getType() == FavoriteType.LINE
                            && safeEq(f.getRouteId(), opt.getRouteId())
                            && f.getDirectionId() == opt.getDirectionId()
                            && safeEq(f.getHeadsign(), opt.getHeadsign())
            );
        }
        return false;
    }

    /**
     * Aggiunge o rimuove un elemento dai preferiti a seconda dello stato corrente.
     *
     * @param value target (StopModel o RouteDirectionOption)
     */
    private void toggleFavorite(Object value) {
        if (value instanceof StopModel stop) {
            String code = stop.getCode();
            if (code == null || code.isBlank()) return;

            FavoriteItem item = FavoriteItem.stop(code, stop.getName());
            if (isFavorite(stop)) favoritesService.remove(item);
            else favoritesService.add(item);
            return;
        }

        if (value instanceof RouteDirectionOption opt) {
            FavoriteItem item = FavoriteItem.fromLine(opt);
            if (isFavorite(opt)) favoritesService.remove(item);
            else favoritesService.add(item);
        }
    }

    /**
     * Confronto null-safe tra stringhe.
     *
     * @param a prima stringa
     * @param b seconda stringa
     * @return true se uguali (anche entrambe null)
     */
    private static boolean safeEq(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    // ==================================================================
    // BRIDGE METHODS (Dashboard)
    // ==================================================================

    /**
     * @return true se esiste una selezione (stop/linea) su cui la stella può lavorare
     */
    public boolean hasCurrentSelection() {
        return currentStarTarget != null;
    }

    /**
     * @return true se la selezione corrente è già nei preferiti
     */
    public boolean isCurrentSelectionFavorite() {
        return currentStarTarget != null && isFavorite(currentStarTarget);
    }

    /**
     * Imposta la modalità dall'esterno (es. ripristino stato dashboard).
     * Esegue gli stessi reset del toggle manuale.
     *
     * @param mode modalità desiderata
     */
    public void setMode(SearchMode mode) {
        if (mode == null) return;
        if (this.currentMode == mode) return;

        this.currentMode = mode;

        if (mode == SearchMode.LINE) {
            modeToggle.setText("Linea");
            modeToggle.setToolTipText("Clicca per passare a ricerca per FERMATA");
        } else {
            modeToggle.setText("Fermata");
            modeToggle.setToolTipText("Clicca per passare a ricerca per LINEA");
        }

        updateLineFiltersVisibility();
        hideSuggestions();
        setStarTarget(null);

        if (onModeChanged != null) onModeChanged.accept(currentMode);

        modeToggle.revalidate();
        modeToggle.repaint();
    }

    /**
     * Imposta lo stato dei filtri linee e aggiorna i suggerimenti visibili.
     *
     * @param bus filtro bus
     * @param tram filtro tram
     * @param metro filtro metro
     */
    public void setLineFilters(boolean bus, boolean tram, boolean metro) {
        busBtn.setSelected(bus);
        tramBtn.setSelected(tram);
        metroBtn.setSelected(metro);
        refilterVisibleLineSuggestions();
    }

    /** @return true se filtro bus è attivo */
    public boolean isBusSelected() { return busBtn.isSelected(); }

    /** @return true se filtro tram è attivo */
    public boolean isTramSelected() { return tramBtn.isSelected(); }

    /** @return true se filtro metro è attivo */
    public boolean isMetroSelected() { return metroBtn.isSelected(); }

    /**
     * Forza un click sulla stella (usato dalla dashboard in alcune interazioni).
     */
    public void clickStar() { favStarBtn.doClick(); }

    /** @return riferimento al campo di testo (usato da controller per focus o test) */
    public JTextField getSearchField() { return searchField; }

    /** @return riferimento al bottone cerca (usato da controller per test o binding) */
    public JButton getSearchButton() { return searchButton; }

    // ==================================================================
    // CALLBACK SETTERS
    // ==================================================================

    /** @param onModeChanged callback cambio modalità */
    public void setOnModeChanged(Consumer<SearchMode> onModeChanged) { this.onModeChanged = onModeChanged; }

    /** @param onSearch callback ricerca manuale */
    public void setOnSearch(Consumer<String> onSearch) { this.onSearch = onSearch; }

    /** @param onTextChanged callback testo cambiato (debounced) */
    public void setOnTextChanged(Consumer<String> onTextChanged) { this.onTextChanged = onTextChanged; }

    /** @param onSuggestionSelected callback selezione fermata */
    public void setOnSuggestionSelected(Consumer<StopModel> onSuggestionSelected) { this.onSuggestionSelected = onSuggestionSelected; }

    /** @param onRouteDirectionSelected callback selezione linea+direzione */
    public void setOnRouteDirectionSelected(Consumer<RouteDirectionOption> onRouteDirectionSelected) { this.onRouteDirectionSelected = onRouteDirectionSelected; }

    /** Callback invocata quando viene premuto il bottone clear (X). */
    private Runnable onClear;

    /**
     * Imposta callback sul clear (X).
     *
     * @param onClear azione da eseguire quando l'utente pulisce il campo
     */
    public void setOnClear(Runnable onClear) {
        this.onClear = onClear;
    }

    // ==================================================================
    // POPUP SUGGERIMENTI (solo compact)
    // ==================================================================

    /**
     * Prepara un JPopupMenu che contiene la SuggestionsView.
     * Usato solo quando la search bar è in modalità compact.
     *
     * @param anchor componente sotto cui posizionare il popup
     */
    private void installSuggestionsPopup(Component anchor) {
        this.popupAnchor = anchor;

        suggestionsPopup = new JPopupMenu();
        suggestionsPopup.setBorder(BorderFactory.createEmptyBorder());
        suggestionsPopup.setOpaque(false);

        suggestionsPopupContainer = new RoundedPopupContainer();
        suggestionsPopupContainer.setLayout(new BorderLayout());
        suggestionsPopupContainer.add(suggestions.getPanel(), BorderLayout.CENTER);

        suggestionsPopup.add(suggestionsPopupContainer);

        addGlobalClickAwayListener();
    }

    /**
     * Mostra il popup suggerimenti sotto l'ancora.
     * La larghezza viene allineata alla searchbar per risultare "full width".
     */
    private void showSuggestionsPopupUnderAnchor() {
        if (suggestionsPopup == null || popupAnchor == null) return;

        Dimension pref = suggestions.getPanel().getPreferredSize();

        int w = (popupAnchor != null ? popupAnchor.getWidth() : 0);
        if (w <= 0) w = SearchBarView.this.getWidth();
        w = Math.max(260, Math.min(900, w));

        int h = Math.max(180, Math.min(420, (pref.height > 0 ? pref.height : 260)));

        suggestionsPopupContainer.setPreferredSize(new Dimension(w, h));
        suggestionsPopup.pack();

        Point p = SwingUtilities.convertPoint(popupAnchor, 0, popupAnchor.getHeight(), SearchBarView.this);

        int gapY = 15;
        suggestionsPopup.show(SearchBarView.this, p.x, p.y + gapY);
        suggestionsPopup.setVisible(true);

        // Il focus resta nel campo, così frecce/enter continuano a funzionare.
        searchField.requestFocusInWindow();
    }

    /**
     * Installa un listener "click-away" sulla finestra owner per chiudere il popup
     * quando l'utente clicca fuori dalla SearchBarView e dal popup stesso.
     */
    private void addGlobalClickAwayListener() {
        SwingUtilities.invokeLater(() -> {
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w == null) return;

            for (MouseListener ml : w.getMouseListeners()) {
                if (ml instanceof ClickAwayMarker) return;
            }

            w.addMouseListener(new ClickAwayListener());
        });
    }

    /**
     * Listener che chiude il popup suggerimenti se il click avviene fuori dalla search bar e dal popup.
     */
    private class ClickAwayListener extends MouseAdapter implements ClickAwayMarker {
        @Override
        public void mousePressed(MouseEvent e) {
            if (suggestionsPopup == null || !suggestionsPopup.isVisible()) return;

            Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), SearchBarView.this);
            Component at = SwingUtilities.getDeepestComponentAt(SearchBarView.this, p.x, p.y);
            if (at != null && SwingUtilities.isDescendingFrom(at, SearchBarView.this)) return;

            Point pp = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), suggestionsPopup);
            Component insidePopup = SwingUtilities.getDeepestComponentAt(suggestionsPopup, pp.x, pp.y);
            if (insidePopup != null) return;

            suggestionsPopup.setVisible(false);
        }
    }

    /**
     * Container grafico arrotondato per il popup suggerimenti.
     * Gestisce ombra, sfondo e bordo con paint custom.
     */
    private static class RoundedPopupContainer extends JComponent {
        @Override public boolean isOpaque() { return false; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 18;

            g2.setColor(new Color(0, 0, 0, 25));
            g2.fillRoundRect(4, 4, w - 8, h - 8, arc, arc);

            g2.setColor(new Color(245, 245, 245, 245));
            g2.fillRoundRect(0, 0, w - 8, h - 8, arc, arc);

            g2.setColor(new Color(210, 210, 210, 200));
            g2.setStroke(new BasicStroke(1.1f));
            g2.drawRoundRect(0, 0, w - 8, h - 8, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Insets getInsets() {
            return new Insets(6, 6, 10, 10);
        }
    }

    // ==================================================================
    // TOGGLE ICON (filtri bus/tram/metro)
    // ==================================================================

    /**
     * Toggle iconico per filtri linea (bus/tram/metro).
     * Cambia immagine tra stato selezionato e non selezionato e mostra un bordo con hover.
     */
    private static class IconToggleButton extends JToggleButton {

        private final Image iconOff;
        private final Image iconOn;
        private boolean hover = false;

        /**
         * @param iconOffPath path icona "spenta"
         * @param iconOnPath path icona "accesa"
         * @param size dimensione fissa del componente
         * @param tooltip tooltip di descrizione
         */
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
            iconOn = load(iconOnPath);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        /**
         * Carica un'immagine dalle risorse.
         *
         * @param path path risorsa
         * @return immagine o null se non trovata
         */
        private Image load(String path) {
            try {
                var url = SearchBarView.class.getResource(path);
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

    // ==================================================================
    // TOGGLE "PILL" (modalità Fermata/Linea)
    // ==================================================================

    /**
     * Toggle in stile "pill" per cambiare modalità di ricerca.
     * Il testo viene gestito dalla view in base al mode corrente.
     */
    private static class PillToggleButton extends JToggleButton {

        private boolean hover = false;

        /**
         * @param text testo iniziale del toggle
         */
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

    // ==================================================================
    // SEARCHBAR CUSTOM: placeholder + lente + rounded panel
    // ==================================================================

    /**
     * Bottone "clear" (X rossa) mostrato solo quando il campo contiene testo.
     */
    private static class ClearButton extends JButton {
        private boolean hover = false;

        ClearButton() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);

            setPreferredSize(new Dimension(46, 46));

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

            if (hover) {
                g2.setColor(new Color(240, 240, 240));
                g2.fillRoundRect(6, 6, w - 12, h - 12, 14, 14);
            }

            g2.setColor(new Color(220, 40, 40));
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int pad = 16;
            g2.drawLine(pad, pad, w - pad, h - pad);
            g2.drawLine(w - pad, pad, pad, h - pad);

            g2.dispose();
        }
    }

    /**
     * JTextField con placeholder disegnato manualmente quando il testo è vuoto.
     */
    private static class PlaceholderTextField extends JTextField {
        private final String placeholder;

        /**
         * @param placeholder testo placeholder mostrato quando il campo è vuoto
         */
        PlaceholderTextField(String placeholder) {
            this.placeholder = placeholder;
            setFont(getFont().deriveFont(Font.PLAIN, 16f));
            setForeground(new Color(35, 35, 35));
            setCaretColor(new Color(35, 35, 35));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (placeholder == null) return;
            String t = getText();
            if (t != null && !t.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(120, 120, 120));
            g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));

            Insets in = getInsets();
            FontMetrics fm = g2.getFontMetrics();
            int x = in.left;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(placeholder, x, y);

            g2.dispose();
        }
    }

    /**
     * Bottone lente con hover, usato per avviare la ricerca.
     */
    private static class MagnifierButton extends JButton {
        private boolean hover = false;

        MagnifierButton() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(false);

            setPreferredSize(new Dimension(46, 46));

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

            if (hover) {
                g2.setColor(new Color(240, 240, 240));
                g2.fillRoundRect(6, 6, w - 12, h - 12, 14, 14);
            }

            g2.setColor(new Color(40, 40, 40));
            g2.setStroke(new BasicStroke(2.2f));

            int cx = w / 2;
            int cy = h / 2 - 1;
            int r = 10;

            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            g2.drawLine(cx + r - 1, cy + r - 1, cx + r + 7, cy + r + 7);

            g2.dispose();
        }
    }

    /**
     * Pannello arrotondato che contiene il campo di ricerca e i bottoni a destra.
     * Gestisce disegno di background e bordo con paint custom.
     */
    private static class RoundedSearchPanel extends JPanel {
        @Override public boolean isOpaque() { return false; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 18;

            g2.setColor(new Color(245, 245, 245, 235));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setColor(new Color(190, 190, 190));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(520, 56);
        }

        @Override
        public Insets getInsets() {
            return new Insets(6, 10, 6, 10);
        }
    }
}
