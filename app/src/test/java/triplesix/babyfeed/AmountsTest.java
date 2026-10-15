package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.junit.Test;

public class AmountsTest {
    @Test
    public void groupsWithDots() {
        assertEquals("1.000.000 ml", Amounts.ml(1000000));
        assertEquals("1.200 ml", Amounts.ml(1200));
        assertEquals("960 ml", Amounts.ml(960));
    }

    @Test
    public void handlesLongTotals() {
        assertEquals("3.000.000.000 ml", Amounts.ml(3000000000L));
    }

    @Test
    public void formatsForSelectedLanguage() {
        assertEquals("1,200 ml", Amounts.ml(1200, java.util.Locale.ENGLISH));
        assertEquals("1.200 ml", Amounts.ml(1200, new java.util.Locale("vi", "VN")));
        assertEquals("3,000,000,000 ml", Amounts.ml(3000000000L, java.util.Locale.ENGLISH));
    }
}
