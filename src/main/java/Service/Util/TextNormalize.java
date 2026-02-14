package Service.Util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalize {
    private TextNormalize() {}

    public static String norm(String s) {
        if (s == null) return "";
        String out = s.trim().toLowerCase(Locale.ROOT);
        out = Normalizer.normalize(out, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", ""); // rimuove accenti
        out = out.replaceAll("[^a-z0-9\\s]", " "); // punteggiatura -> spazio
        out = out.replaceAll("\\s+", " ").trim(); // spazi multipli
        return out;
    }

    public static boolean isMostlyNumeric(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;
        int digits = 0;
        int letters = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) digits++;
            else if (Character.isLetter(c)) letters++;
        }
        return digits > 0 && letters == 0; // tutto numerico (es. "905")
    }
}