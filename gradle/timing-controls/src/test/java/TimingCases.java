import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;

public class TimingCases {
    @Test public void passingOriginalCase() {
        assertEquals("Test worker", Thread.currentThread().getName());
    }
    @Test public void conditionalOriginalFailure() {
        if (Boolean.getBoolean("timing.control.fail"))
            throw new IllegalArgumentException("original-junit-control-failure");
    }
    @Ignore("original skip remains a skip")
    @Test public void skippedOriginalCase() {
        fail("must remain skipped");
    }
}
