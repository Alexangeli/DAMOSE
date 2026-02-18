package Model.User;

/**
 * Gestisce la sessione utente corrente nell’applicazione desktop.
 *
 * Non si tratta di una sessione web, ma di un semplice
 * contenitore statico che mantiene l’utente autenticato
 * per tutta la durata dell’esecuzione del programma.
 *
 * Viene utilizzata dai controller e dalla GUI
 * per verificare se un utente è loggato
 * e per accedere ai suoi dati.
 */
public final class Session {

    /**
     * Utente attualmente autenticato.
     * È volatile per garantire visibilità tra thread.
     */
    private static volatile User currentUser;

    /**
     * Costruttore privato per evitare istanziazione.
     * La classe è pensata come utility statica.
     */
    private Session() {
        throw new UnsupportedOperationException("Utility class, cannot be instantiated");
    }

    /**
     * Imposta l’utente come autenticato
     * per la sessione corrente dell’applicazione.
     *
     * @param user utente da autenticare
     */
    public static void login(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user cannot be null");
        }
        currentUser = user;
    }

    /**
     * Termina la sessione corrente (logout).
     */
    public static void logout() {
        currentUser = null;
    }

    /**
     * Indica se è presente un utente autenticato.
     */
    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Restituisce l’utente attualmente autenticato.
     *
     * @return utente loggato oppure null se nessuno è autenticato
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Metodo di utilità per ottenere rapidamente l’id dell’utente.
     *
     * @return id dell’utente loggato, oppure -1 se nessun utente è autenticato
     */
    public static int getCurrentUserId() {
        return currentUser != null ? currentUser.getId() : -1;
    }
}
