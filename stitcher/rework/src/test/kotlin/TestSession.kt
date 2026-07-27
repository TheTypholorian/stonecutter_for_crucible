import de.infix.testBalloon.framework.core.TestCompartment
import de.infix.testBalloon.framework.core.TestSession

class ConcurrentTestSession : TestSession(
    defaultCompartment = { TestCompartment.Concurrent }
)