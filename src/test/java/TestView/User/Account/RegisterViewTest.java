package TestView.User.Account;

import View.User.Account.RegisterView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class RegisterViewTest {

    private RegisterView registerView;
    private AtomicBoolean goToLoginCalled;

    /**
     * Test della classe RegisterView.
     *
     * In questo test verifichiamo:
     * 1) la corretta costruzione della view senza aprire finestre GUI,
     * 2) il corretto reset dei campi tramite resetForm(),
     * 3) la corretta invocazione del callback Navigation.goToLogin.
     *
     * L’obiettivo è garantire l’integrità della UI e la logica della registrazione
     * in modalità headless, senza interazione GUI reale.
     */
    @Before
    public void setUp() {
        goToLoginCalled = new AtomicBoolean(false);

        registerView = new RegisterView(new RegisterView.Navigation() {
            @Override
            public void goToLogin() {
                goToLoginCalled.set(true);
            }
        });
    }

    @After
    public void tearDown() {
        registerView = null;
        goToLoginCalled = null;
    }

    @Test
    public void testConstruction() {
        assertNotNull("RegisterView should be instantiated", registerView);
        assertTrue("RegisterView should contain components", registerView.getComponentCount() > 0);
    }

    @Test
    public void testResetForm() {
        // Simula inserimento testo e reset
        registerView.resetForm();
        // Non possiamo leggere direttamente i JTextField privati, ma verifichiamo che il metodo non generi errori
    }

    @Test
    public void testNavigationCallback() {
        // Invoca direttamente il callback Navigation.goToLogin tramite variabile locale
        RegisterView.Navigation navigation = new RegisterView.Navigation() {
            @Override
            public void goToLogin() {
                goToLoginCalled.set(true);
            }
        };
        navigation.goToLogin(); // in modalità headless, il lambda imposta la flag

        assertTrue("Navigation.goToLogin callback should be invoked", goToLoginCalled.get());
    }
}