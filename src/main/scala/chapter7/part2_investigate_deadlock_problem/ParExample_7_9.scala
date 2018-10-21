package chapter7.part2_investigate_deadlock_problem

import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import chapter7.part1_implement_par.Par._
import chapter7.part2_investigate_deadlock_problem.NamedThreadFactory._
import com.google.common.util.concurrent.ThreadFactoryBuilder

/* 7.9

Show that any fixed-size thread pool can be made to deadlock given this imple-
mentation of fork

*/

object WeCanMakeThisDeadLock_1Thread extends App{

  val es: ExecutorService = Executors.newFixedThreadPool(1, namedThreadFactory)

  val one = fork(lazyUnit(1))
  val res = run(es)(one)

  println(res.get)

  es.shutdown()

}

object WeCanMakeThisDeadLock_2Threads extends App{

  val es: ExecutorService = Executors.newFixedThreadPool(2, namedThreadFactory)

  val one = fork(fork(lazyUnit(1)))
  val res = run(es)(one)

  println(res.get)

  es.shutdown()

}

object NamedThreadFactory {
  val namedThreadFactory: ThreadFactory = new ThreadFactoryBuilder()
    .setNameFormat("es-thread-%d")
    .build()
}


