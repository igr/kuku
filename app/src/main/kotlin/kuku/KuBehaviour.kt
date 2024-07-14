package kuku

/**
 * Actor behavior is a function that handles messages
 * and returns the new behavior of the actor.
 * It is a transition function from the current
 * behavior to the next behavior, based on a message.
 */
sealed interface KuBehavior<T> {
    suspend operator fun invoke(ctx: KuActorContext<T>, msg: T): KuBehavior<T>
}

/**
 * Special behaviour that returns itself.
 * Indicates that the actor should keep the current behavior.
 */
internal data object KuBehaviorSame : KuBehavior<Nothing> {
    override suspend fun invoke(ctx: KuActorContext<Nothing>, msg: Nothing) = this
}

/**
 * Special behaviour that indicates that the actor should stop.
 */
internal data object KuBehaviorStop : KuBehavior<Nothing> {
    override suspend fun invoke(ctx: KuActorContext<Nothing>, msg: Nothing) = this
}

/**
 * This is a base class for all behaviors. Essentially, just
 * adding a constructor to the interface and wiring it to the invoke method.
 * Not used outside the library.
 */
internal class KuBehaviorBase<T>(
    private val receivedBehaviour: suspend (ctx: KuActorContext<T>, msg: T) -> KuBehavior<T>
) : KuBehavior<T> {
    override suspend fun invoke(ctx: KuActorContext<T>, msg: T) = receivedBehaviour(ctx, msg)
}

/**
 * Special behavior that is used to setup the actor.
 * It is used to initialize the actor with the first behavior.
 */
internal class KuBehaviorSetup<T>(
    private val setupBehavior: suspend (ctx: KuActorContext<T>) -> KuBehavior<T>
) : KuBehavior<T> {
    // todo remove interface function and add a setup function here that take only ctx
    override suspend fun invoke(ctx: KuActorContext<T>, msg: T): KuBehavior<T> = setupBehavior(ctx)
}


/**
 * Setup constructor for actor's behavior.
 * @see KuBehaviorSetup
 */
fun <T> setup(setupBehaviour: suspend (ctx: KuActorContext<T>) -> KuBehavior<T>): KuBehavior<T> =
    KuBehaviorSetup(setupBehaviour)

/**
 * Receive constructor for actor's behavior.
 * Receives both the context and the message.
 */
fun <T> receive(receivedBehaviour: suspend (ctx: KuActorContext<T>, msg: T) -> KuBehavior<T>): KuBehavior<T> =
    KuBehaviorBase(receivedBehaviour)

/**
 * Receive constructor for actor's behavior.
 * Receives only the message.
 */
fun <T> receiveMessage(receivedBehaviour: suspend (msg: T) -> KuBehavior<T>): KuBehavior<T> =
    receive { _, msg -> receivedBehaviour(msg) }

@Suppress("UNCHECKED_CAST")
fun <T> same(): KuBehavior<T> = KuBehaviorSame as KuBehavior<T>
@Suppress("UNCHECKED_CAST")
fun <T> stopped(): KuBehavior<T> = KuBehaviorStop as KuBehavior<T>
