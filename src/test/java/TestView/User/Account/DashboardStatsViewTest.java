package TestView.User.Account;

import View.User.Account.DashboardStatsView;
import View.User.Account.AccountSettingsDialog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class DashboardStatsViewTest {

    private DashboardStatsView dashboardView;
    private AtomicBoolean supplierCalled;

    /**
     * Test della classe DashboardStatsView.
     *
     * In questo test verifichiamo:
     * 1) la corretta costruzione della view senza aprire finestre GUI,
     * 2) che il supplier di dati venga invocato correttamente,
     * 3) che il metodo refresh() imposti i dati sulla PieChart senza errori.
     *
     * L’obiettivo è garantire la corretta funzionalità della dashboard
     * mantenendo i test headless e riproducibili.
     */
    @Before
    public void setUp() {
        supplierCalled = new AtomicBoolean(false);

        dashboardView = new DashboardStatsView(() -> {
            supplierCalled.set(true);
            return new AccountSettingsDialog.DashboardData(3, 5, 2);
        });
    }

    @After
    public void tearDown() {
        dashboardView = null;
        supplierCalled = null;
    }

    @Test
    public void testConstruction() {
        assertNotNull("DashboardStatsView should be instantiated", dashboardView);
    }

    @Test
    public void testRefreshInvokesSupplierAndSetsData() {
        // Chiama refresh
        dashboardView.refresh();

        // Verifica che il supplier sia stato invocato
        assertTrue("Data supplier should have been called", supplierCalled.get());

        // Non possiamo leggere direttamente la PieChart interna,
        // ma possiamo verificare che il metodo refresh non generi eccezioni.
        // Se serve testare valori, si può aggiungere getter nella PieChart.
    }

    @Test
    public void testRefreshWithNullSupplier() {
        // Crea una dashboard con supplier nullo
        dashboardView = new DashboardStatsView(null);

        // Chiama refresh: deve impostare valori default senza errori
        dashboardView.refresh();
    }
}