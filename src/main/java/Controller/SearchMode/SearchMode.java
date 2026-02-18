package Controller.SearchMode;

/**
 * Modalità di ricerca selezionabile dalla UI.
 *
 * Determina quale logica di ricerca viene applicata nella SearchBar:
 * - STOP: ricerca e suggerimenti sulle fermate.
 * - LINE: ricerca e suggerimenti sulle linee (route) e relative direzioni.
 *
 * Creatore: Simone Bonuso
 */
public enum SearchMode {

    /** Modalità "Fermata": la ricerca opera sulle fermate (stop). */
    STOP,

    /** Modalità "Linea": la ricerca opera sulle linee (route) e sulle direzioni disponibili. */
    LINE
}