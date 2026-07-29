import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * Small JDI helper for deterministic crash-window rehearsals.
 *
 * It attaches to an already-running JDWP target, suspends the entire target at
 * the requested method entry and waits for an explicit "resume" line. This is
 * test tooling only; production services do not gain fault-injection hooks.
 */
public final class JdwpBreakpointProbe {

    private JdwpBreakpointProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: JdwpBreakpointProbe <host> <port> <class> <method>");
        }

        VirtualMachine vm = attach(args[0], args[1]);
        String className = args[2];
        String methodName = args[3];

        List<ReferenceType> loaded = vm.classesByName(className);
        if (loaded.isEmpty()) {
            ClassPrepareRequest prepare = vm.eventRequestManager().createClassPrepareRequest();
            prepare.addClassFilter(className);
            prepare.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            prepare.enable();
            System.out.printf("BREAKPOINT_DEFERRED %s.%s%n", className, methodName);
        } else {
            armBreakpoint(vm, loaded.get(0), methodName);
        }

        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            EventSet events = vm.eventQueue().remove();
            boolean resume = true;
            for (Event event : events) {
                if (event instanceof ClassPrepareEvent prepared) {
                    armBreakpoint(vm, prepared.referenceType(), methodName);
                } else if (event instanceof BreakpointEvent breakpoint) {
                    resume = false;
                    System.out.printf("BREAKPOINT_REACHED %s:%d%n",
                            breakpoint.location().declaringType().name(),
                            breakpoint.location().lineNumber());
                    System.out.flush();
                    if ("resume".equals(input.readLine())) {
                        resume = true;
                    }
                } else if (event instanceof VMDisconnectEvent) {
                    return;
                }
            }
            if (resume) {
                events.resume();
            }
        }
    }

    private static VirtualMachine attach(String host, String port) throws Exception {
        AttachingConnector connector = Bootstrap.virtualMachineManager()
                .attachingConnectors()
                .stream()
                .filter(candidate -> "com.sun.jdi.SocketAttach".equals(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SocketAttach connector is unavailable"));
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue(host);
        arguments.get("port").setValue(port);
        return connector.attach(arguments);
    }

    private static void armBreakpoint(VirtualMachine vm, ReferenceType type, String methodName) {
        var methods = type.methodsByName(methodName);
        if (methods.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one " + type.name() + "." + methodName
                            + " method, found " + methods.size());
        }
        BreakpointRequest breakpoint = vm.eventRequestManager()
                .createBreakpointRequest(methods.get(0).location());
        breakpoint.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        breakpoint.enable();
        System.out.printf("BREAKPOINT_ARMED %s.%s%n", type.name(), methodName);
        System.out.flush();
    }
}
