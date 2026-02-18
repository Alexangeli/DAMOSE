package Service.Util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Utility per la normalizzazione del testo nelle ricerche.
 *
 * Responsabilità:
 * - trasformare stringhe in una forma "canonizzata" per confronti case-insensitive
 * - rimuovere accenti e punteggiatura
 * - semplificare spazi multipli
 * - identificare input prevalentemente numerici (utile per distinguere codice fermata vs nome)
 *
 * Contesto:
 * - usata principalmente negli indici di ricerca (es. StopSearchIndexV2)
 * - garantisce che confronti e match siano coerenti e indipendenti da maiuscole/minuscole o accenti
 */
public final class TextNormalize {

    /**
     * Classe di sole utility statiche: costruttore privato.
     */
    private TextNormalize() {
    }

    /**
     * Normalizza una stringa per uso in ricerche testuali.
     *
     * Trasformazioni applicate:
     * - trim iniziale/finale
     * - lowercase con {@link Locale#ROOT}
     * - rimozione accenti (es. "Pietà" -> "pieta")
     * - sostituzione punteggiatura con spazio
     * - compressione spazi multipli
     *
     * @param s stringa di input
     * @return stringa normalizzata (mai null)
     */
    public static String norm(String s) {
        if (s == null) {
            return "";
        }

        String out = s.trim().toLowerCase(Locale.ROOT);

        // Rimuove accenti (forma decomposed + rimozione mark Unicode)
        out = Normalizer.normalize(out, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        // Punteggiatura -> spazio
        out = out.replaceAll("[^a-z0-9\\s]", " ");

        // Spazi multipli -> singolo spazio
        out = out.replaceAll("\\s+", " ").trim();

        return out;
    }

    /**
     * Verifica se una stringa è composta esclusivamente (o quasi) da cifre.
     *
     * Uso tipico:
     * - distinguere tra ricerca per codice fermata (es. "905")
     *   e ricerca per nome (es. "prenestina").
     *
     * @param s stringa di input
     * @return true se contiene solo cifre (almeno una) e nessuna lettera
     */
    public static boolean isMostlyNumeric(String s) {
        if (s == null) {
            return false;
        }

        String t = s.trim();
        if (t.isEmpty()) {
            return false;
        }

        int digits = 0;
        int letters = 0;

        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
            } else if (Character.isLetter(c)) {
                letters++;
            }
        }

        return digits > 0 && letters == 0;
    }
}