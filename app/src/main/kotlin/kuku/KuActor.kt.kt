package kuku

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext

internal class KuActor<T>(
	name: String,
	private val receiveChannel: Channel<T>,
	job: Job,
	scope: CoroutineScope,
) {
	private val ctx =
		KuActorContext(KuActorRef(receiveChannel), name, job, scope)

	/**
	 * The main loop of the actor.
	 */
	suspend fun run(behavior: KuBehavior<T>) {
		when (behavior) {
			is KuBehaviorSetup -> {
				val setupBehaviour = behavior(ctx, Unit as T)   // todo remove message
				//changeBehavior(behavior, setupBehaviour)
				// it always should change behavior
				run(setupBehaviour)
			}

			is KuBehaviorBase -> {
				val msg = receiveChannel.receive()
				val newBehavior = behavior(ctx, msg)
				when (newBehavior) {
					is KuBehaviorSame -> run(behavior)
					else -> run(newBehavior)
				}
			}

			is KuBehaviorSame -> {
				throw IllegalStateException("Can't be the first behavior")
			}

			is KuBehaviorStop -> {
				receiveChannel.close()
				ctx.job.cancelAndJoin()
			}
		}
	}
}

class KuActorContext<T> internal constructor(
	val ref: KuActorRef<T>,
	val name: String,
	internal val job: Job,
	internal val scope: CoroutineScope,
) {

	/**
	 * Spawns a new actor with the given initial behavior.
	 */
	fun <T> spawn(
		name: String,
		behavior: KuBehavior<T>,
	): KuActorRef<T> {
		return spawnKuActor(
			name,
			behavior,
			scope,
			buildActorContext(name, job)
		)
	}

	private fun buildActorContext(
		name: String,
		parentJob: Job?,
	): CoroutineContext {
		val job = Job(parentJob)
		val dispatcher = Dispatchers.Default
		return CoroutineName("kuActor-$name") + job + dispatcher
	}

	/**
	 * FIFO queue of messages.
	 * The pool router is created with a routee Behavior and spawns a number of children with that behavior which it will then forward messages to.
	 * @see spawnKuActor
	 */
	fun <T> router(
		name: String,
		poolSize: Int,
		behavior: KuBehavior<T>,
	): KuActorRef<T> {
		val job = Job(job)
		val mailbox = Channel<T>(capacity = Channel.UNLIMITED)

		repeat(poolSize) {
			val context = CoroutineName("kuActor-router-$name-$it") + job
			scope.launch(context) {
				KuActor(name, mailbox, coroutineContext.job, this).run(behavior)
			}
		}
		return KuActorRef(mailbox)
	}

}

/**
 * Creates a new actor system, a top-level actor.
 */
fun <T> CoroutineScope.kuActorSystem(behavior: KuBehavior<T>): KuActorRef<T> {
	return spawnKuActor(
		"kukuActor",
		behavior,
		this,
		CoroutineName("kukuActor"),
	)
}

/**
 * Spawns a new actor with the given initial behavior.
 */
private fun <T> spawnKuActor(
	name: String,
	behavior: KuBehavior<T>,
	scope: CoroutineScope,
	context: CoroutineContext,
): KuActorRef<T> {

	// Creates a mailbox
	val mailbox = Channel<T>(capacity = Channel.UNLIMITED)
	// Launches an actor and runs it in coroutine
	scope.launch(context) {
		KuActor(name, mailbox, coroutineContext.job, scope)
			.run(behavior)
	}
		//.invokeOnCompletion() //todo add final completion
	return KuActorRef(mailbox)
}

/**
 * Request-Response with ask between two actors.
 * In an interaction where there is a 1:1 mapping between a request and
 * a response we can use ask on the ActorContext to interact with another actor.
 */
fun <T, R> CoroutineScope.ask(
	targetActorRef: KuActorRef<T>,
	timeoutInMillis: Long = 1000L,
	listener: (ref: KuActorRef<R>) -> T,
): Deferred<R> {
	val mailbox = Channel<R>(capacity = Channel.RENDEZVOUS)
	val result =
		async {
			try {
				withTimeout(timeoutInMillis) {
					targetActorRef.tell(listener(KuActorRef(mailbox)))
					mailbox.receive()   // returned value
				}
			} finally {
				mailbox.close()
			}
		}
	return result
}
