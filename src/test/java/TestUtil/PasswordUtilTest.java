package TestUtil;

import Util.PasswordUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class PasswordUtilTest {

    @Test
    public void hashShouldNotBeNullOrEmpty() {
        String hash = PasswordUtil.hash("password123");

        assertNotNull(hash);
        assertFalse(hash.trim().isEmpty());
    }

    @Test
    public void samePasswordShouldProduceDifferentHashes() {
        String hash1 = PasswordUtil.hash("password123");
        String hash2 = PasswordUtil.hash("password123");

        assertNotEquals(hash1, hash2);
    }

    @Test
    public void verifyShouldReturnTrueForCorrectPassword() {
        String password = "super-secret";
        String hash = PasswordUtil.hash(password);

        assertTrue(PasswordUtil.verify(password, hash));
    }

    @Test
    public void verifyShouldReturnFalseForWrongPassword() {
        String hash = PasswordUtil.hash("correct-password");

        assertFalse(PasswordUtil.verify("wrong-password", hash));
    }

    @Test
    public void verifyShouldFailWithGarbageHash() {
        assertFalse(PasswordUtil.verify("password", "not-a-real-hash"));
    }
}

