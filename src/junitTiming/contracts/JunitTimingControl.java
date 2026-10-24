package bc.nonshipping.junittiming;

import org.junit.platform.engine.*;
import org.junit.platform.engine.support.descriptor.*;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Inert non-Minecraft custom-engine control; deliberately not a production workload/baseline. */
public final class JunitTimingControl {
    private static final RuntimeException ORIGINAL = new RuntimeException("original identity");
    public static void main(String[] args) {
        String mode = args[0]; long caller = Thread.currentThread().getId(); AtomicLong callbacks = new AtomicLong();
        if (mode.equals("hang-hook")) Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HANGING_SHUTDOWN_HOOK_ENTERED"); System.out.flush();
            while (true) { try { Thread.sleep(1000); } catch (InterruptedException ignored) { } }
        }));
        Launcher launcher = LauncherFactory.create(LauncherConfig.builder().enableTestEngineAutoRegistration(false)
                .addTestEngines(new Engine(mode, caller)).build());
        launcher.execute(LauncherDiscoveryRequestBuilder.request().build(), new TestExecutionListener() {
            @Override public void executionFinished(TestIdentifier test, TestExecutionResult result) {
                if (!test.isTest()) return;
                if (Thread.currentThread().getId() != caller) throw new AssertionError("Changed invocation thread");
                if (mode.equals("failure") && result.getThrowable().orElse(null) != ORIGINAL) throw new AssertionError("Changed original exception");
                callbacks.incrementAndGet();
            }
        });
        if (!mode.equals("skip") && callbacks.get() != (mode.equals("dynamic") ? 2 : 1)) throw new AssertionError("Missing original reporter callback");
        System.out.println("ORIGINAL_RESULT_PRESERVED " + mode);
    }
    private static final class Case extends AbstractTestDescriptor {
        Case(UniqueId id, String name) { super(id, name, MethodSource.from("control.Cases", "case")); }
        @Override public Type getType() { return Type.TEST; }
    }
    private static final class Engine implements TestEngine {
        final String mode; final long caller;
        Engine(String mode, long caller) { this.mode = mode; this.caller = caller; }
        public String getId() { return "bc-control"; }
        public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId id) {
            EngineDescriptor root = new EngineDescriptor(id, "control-engine"); root.addChild(new Case(id.append("test", "case"), "case")); return root;
        }
        public void execute(ExecutionRequest request) {
            var listener = request.getEngineExecutionListener(); var root = request.getRootTestDescriptor();
            listener.executionStarted(root);
            if (mode.equals("skip")) listener.executionSkipped(root, "container control");
            else {
                var test = root.getChildren().iterator().next(); run(listener, test);
                if (mode.equals("dynamic")) {
                    Case dynamic = new Case(root.getUniqueId().append("dynamic", "1"), "dynamic[1]");
                    root.addChild(dynamic); listener.dynamicTestRegistered(dynamic); run(listener, dynamic);
                }
                listener.executionFinished(root, TestExecutionResult.successful());
            }
        }
        void run(EngineExecutionListener listener, TestDescriptor test) {
            if (Thread.currentThread().getId() != caller) throw new AssertionError("Engine moved thread");
            listener.executionStarted(test);
            if (mode.equals("timeout") || mode.equals("hang-hook")) while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
            }
            listener.executionFinished(test, mode.equals("failure") ? TestExecutionResult.failed(ORIGINAL) :
                    mode.equals("aborted") ? TestExecutionResult.aborted(ORIGINAL) : TestExecutionResult.successful());
        }
    }
}
