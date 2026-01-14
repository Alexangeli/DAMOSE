package Util;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class PasswordUtil {

    private PasswordUtil() {}

    public static String hash(String plain) {
        int costFactor = 12;
        return BCrypt.withDefaults()
                .hashToString(costFactor, plain.toCharArray());
    }

    public static boolean verify(String plain, String hash) {
        return BCrypt.verifyer()
                .verify(plain.toCharArray(), hash)
                .verified;
    }
}
