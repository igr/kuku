package kuku

import kotlinx.coroutines.channels.SendChannel

/**
 * A reference to an actor.
 */
class KuActorRef<T> internal constructor(
	private val mailbox: SendChannel<T>,
) {

	/**
	 * The fundamental way to interact with an actor is through actorRef.tell(message).
	 * Sending a message with tell can safely be done from any thread.
	 *
	 * Tell is asynchronous which means that the method returns right away.
	 * After the statement is executed there is no guarantee that the message
	 * has been processed by the recipient yet. It also means there is no way
	 * to know if the message was received, the processing succeeded or failed.
	 *
	 * Useful when:
	 * + It is not critical to be sure that the message was processed
	 * + There is no way to act on non successful delivery or processing
	 * + We want to minimize the number of messages created to get higher throughput
	 */
	suspend infix fun tell(msg: T) {
		mailbox.send(msg)
	}

}
