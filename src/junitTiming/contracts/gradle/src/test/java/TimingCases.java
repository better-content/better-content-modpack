import org.junit.jupiter.api.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimingCases {
    @Test void caseBody() {
        String mode = System.getProperty("control.mode");
        if (mode.equals("failure")) throw new IllegalStateException("original-control-failure");
        if (mode.equals("hang-hook")) Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HANGING_SHUTDOWN_HOOK_ENTERED");
            while (true) { try { Thread.sleep(1000); } catch (InterruptedException ignored) { } }
        }));
        if (mode.equals("timeout") || mode.equals("hang-hook")) while (true) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
        }
    }
    @ParameterizedTest @ValueSource(ints = {1, 2}) void parameters(int value) { Assertions.assertTrue(value > 0); }
    @Disabled("original skip remains skip") @Test void skipped() { throw new AssertionError("not reached"); }
    @TestFactory Stream<DynamicTest> dynamic() {
        return Stream.of(DynamicTest.dynamicTest("dynamic[1]", () -> Assertions.assertTrue(true)),
                         DynamicTest.dynamicTest("dynamic[2]", () -> Assertions.assertEquals(2, 1 + 1)));
    }
}
