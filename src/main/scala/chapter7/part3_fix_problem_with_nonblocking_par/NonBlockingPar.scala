package chapter7.part3_fix_problem_with_nonblocking_par

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{Callable, CountDownLatch, ExecutorService, Executors}

import com.google.common.util.concurrent.ThreadFactoryBuilder

trait CatFuture[A] {
  def apply(k: A => Unit): Unit
}

object ParPlus {

  type Par[A] = ExecutorService => CatFuture[A]

  def unit[A](a: A): Par[A] = es => (k: A => Unit) => k(a)

  def fork[A](pa: => Par[A]): Par[A] =
    es => (cb: A => Unit) => eval(es)(pa(es)(cb))

  def eval(es: ExecutorService)(r: => Unit): Unit =
    es.submit(new Callable[Unit] {
      def call: Unit = r
    })

  def lazyUnit[A](a: => A): Par[A] = fork(unit(a))

  def run[A](ex: ExecutorService)(pa: Par[A]): A = {
    val ref = new AtomicReference[A]
    val latch = new CountDownLatch(1)
    pa(ex)(a => {
      ref.set(a)
      latch.countDown()
    })
    latch.await()
    ref.get()
  }

  def sequence[A](ps: List[Par[A]]): Par[List[A]] = es => {
    val as: List[A] = ps.map(f => ParPlus.run(es)(f))
    unit(as)(es)
  }

  def sequence2[A](l: List[Par[A]]): Par[List[A]] =
    l.foldRight[Par[List[A]]](unit(List()))((h, t) => map2(h, t)(_ :: _))

  def map2[A,B,C](p: Par[A], p2: Par[B])(f: (A,B) => C): Par[C] =
    es => new CatFuture[C] {
      def apply(cb: C => Unit): Unit = {
        var ar: Option[A] = None
        var br: Option[B] = None
        val combiner = Actor[Either[A,B]](es) {
          case Left(a) => br match {
            case None => ar = Some(a)
            case Some(b) => eval(es)(cb(f(a, b)))
          }
          case Right(b) => ar match {
            case None => br = Some(b)
            case Some(a) => eval(es)(cb(f(a, b)))
          }
        }
        p(es)(a => combiner ! Left(a))
        p2(es)(b => combiner ! Right(b))
      }
    }


}


object BadMySequenceParPlus extends App {

  import ParPlus._

  val namedThreadFactory = new ThreadFactoryBuilder()
    .setNameFormat("noodle-%d")
    .build()

  val es: ExecutorService = Executors.newFixedThreadPool(2, namedThreadFactory)

  val one = lazyUnit(get(1))
  val two  = lazyUnit(get(2))

  //  val sequenced = fork(fork(sequence2(List(one, two))))

  val sequenced = lazyUnit(get(1))

  val res = run(es)(sequenced)
  println(res)
  es.shutdown()

  def get(i: Int): Int = {
    println(Thread.currentThread().getName)
    throw new RuntimeException
    //    i
  }

}
