package kuku

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

object Counter {
	sealed interface Command
	data class Increment(val by: Int) : Command
	data object Reset : Command
	data class GetValue(val replyTo: KuActorRef<Int>) : Command

	fun behavior(currentValue: Int): KuBehavior<Command> = receiveMessage { msg ->
		when (msg) {
			is Increment -> {
				if (msg.by == 1) delay(1000)    // special case
				behavior(currentValue + msg.by)
			}
			is Reset -> behavior(0)
			is GetValue -> {
				msg.replyTo tell currentValue
				same()
			}
		}
	}
}

data class Answer(val msg: String)

object HappyActor {
	// single message, that takes a reference to the actor that will receive the answer
	data class Question(val replyTo: KuActorRef<Answer>, val content: String)

	val behavior: KuBehavior<Question> = receiveMessage { msg ->
		msg.replyTo tell Answer(msg.content + " Be happy!")
		same()
	}
}

object MainActor {

	val behavior: KuBehavior<Int> = setup { ctx ->
		val counterRef1 = ctx.spawn("counter1", Counter.behavior(0))
		val counterRef2 = ctx.spawn("counter2", Counter.behavior(0))

		val happyActor = ctx.spawn("happy", HappyActor.behavior)
		val answer = ctx.scope.ask(happyActor) { ref -> HappyActor.Question(ref, "Hey?") }

		counterRef2 tell Counter.Increment(1)   // trigger the special case, and continues immediately
		counterRef2 tell Counter.Increment(1)   // this one will be delayed too, but the main actor is not blocked
		counterRef2 tell Counter.GetValue(ctx.ref)  // after the above messages processed, we will ask for the value

		counterRef1 tell Counter.Increment(10)
		counterRef1 tell Counter.GetValue(ctx.ref)
		counterRef1 tell Counter.Reset
		counterRef1 tell Counter.GetValue(ctx.ref)

		println("The answer is: ${answer.await().msg}")     // block until the answer is received

		// setting up the main actor's behavior: when it receives a number (message), it prints it
		receiveMessage { msg ->
			println("The value is: $msg")
			same()
		}
	}
}


suspend fun main(): Unit = coroutineScope {
	kuActorSystem(MainActor.behavior)
}