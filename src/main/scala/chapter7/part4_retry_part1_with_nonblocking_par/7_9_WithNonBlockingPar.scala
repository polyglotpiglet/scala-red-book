package chapter7.part4_retry_part1_with_nonblocking_par

import java.util.concurrent.{ExecutorService, Executors}

import chapter7.part2_investigate_deadlock_problem.NamedThreadFactory._
import chapter7.part3_fix_problem_with_nonblocking_par.ParPlus._


object DoesntDeadlock_1Thread extends App{

  val es: ExecutorService = Executors.newFixedThreadPool(1, namedThreadFactory)

  val one = fork(lazyUnit(1))
  val res = run(es)(one)

  println(res)

  es.shutdown()

}

object DoesntDeadLock_2Threads extends App{

  val es: ExecutorService = Executors.newFixedThreadPool(2, namedThreadFactory)

  val one = fork(fork(lazyUnit(1)))
  val res = run(es)(one)

  println(res)

  es.shutdown()

}



