package chapter7.part5_non_blocking_error_handling

import java.util.concurrent.{ExecutorService, Executors}

import chapter7.part5_non_blocking_error_handling.ParPlus._

import chapter7.part2_investigate_deadlock_problem.NamedThreadFactory._

object BadMySequenceParPlus extends App {

  val es = Executors.newFixedThreadPool(2, namedThreadFactory)

  val one = lazyUnit(get(1))

  val res = run(es)(one)
  println(res)
  es.shutdown()

  def get(i: Int): Int = {
    throw new RuntimeException
  }

}



