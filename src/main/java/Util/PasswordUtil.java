package Util;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Utility per la gestione sicura delle password tramite algoritmo BCrypt.
 *
 * Responsabilità:
 * - generare hash sicuri a partire da password in chiaro
 * - verificare una password in chiaro contro un hash salvato
 *
 * Scelte di sicurezza:
 * - utilizzo di BCrypt (algoritmo adattivo, resistente a brute-force)
 * - cost factor configurato a 12 (buon compromesso tra sicurezza e performance in ambito universitario/prototipale)
 *
 * Nota:
 * - la password in chiaro non viene mai salvata.
 * - il metodo {@link #hash(String)} restituisce una stringa che contiene già salt e cost factor.
 */
public final class PasswordUtil {

    /**
     * Classe di sole utility statiche: costruttore privato.
     */
    private PasswordUtil() {
    }

    /**
     * Genera un hash BCrypt a partire da una password in chiaro.
     *
     * @param plain password in chiaro
     * @return stringa hash contenente salt e cost factor
     *
     * @throws NullPointerException se plain è null
     */
    public static String hash(String plain) {
        int costFactor = 12;

        return BCrypt.withDefaults()
                .hashToString(costFactor, plain.toCharArray());
    }

    /**
     * Verifica se una password in chiaro corrisponde all'hash salvato.
     *
     * @param plain password inserita dall'utente
     * @param hash hash salvato nel database
     * @return true se la password è corretta, false altrimenti
     */
    public static boolean verify(String plain, String hash) {
        return BCrypt.verifyer()
                .verify(plain.toCharArray(), hash)
                .verified;
    }
}