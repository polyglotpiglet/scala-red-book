package chapter7.part1_implement_par

import java.util.concurrent._

import com.google.common.util.concurrent.ThreadFactoryBuilder

object Par {

  type Par[A] = ExecutorService => Future[A]

  private case class UnitFuture[A](get: A) extends Future[A] {
    def isDone = true

    def get(timeout: Long, units: TimeUnit): A = get

    def isCancelled = false

    def cancel(evenIfRunning: Boolean): Boolean = false
  }

  def unit[A](a: A): Par[A] = (_: ExecutorService) => UnitFuture(a)

  def lazyUnit[A](a: => A): Par[A] = fork(unit(a))

  def fork[A](a: => Par[A]): Par[A] = es => {
    es.submit(new Callable[A] {
      def call: A = a(es).get
    })
  }

  def run[A](ex: ExecutorService)(pa: Par[A]): Future[A] = pa(ex)

  /* 7.1 give the signature for map2 */

  def map2[A, B, C](pa: Par[A], pb: Par[B])(f: (A, B) => C): Par[C] = es => {
    val a: Future[A] = pa(es)
    val b: Future[B] = pb(es)
    UnitFuture(f(a.get, b.get))
  }

  /* 7.3 implement map2 respecting timeouts */

  def map2_respectingTimeouts[A, B, C](pa: Par[A], pb: Par[B])(f: (A, B) => C): Par[C] = es => {
    val a: Future[A] = pa(es)
    val b: Future[B] = pb(es)

    new Future[C] {
      override def cancel(mayInterruptIfRunning: Boolean): Boolean = a.cancel(mayInterruptIfRunning) && b.cancel(mayInterruptIfRunning)

      override def isCancelled: Boolean = a.isCancelled || b.isCancelled

      override def isDone: Boolean = a.isDone && b.isDone

      override def get(): C = f(a.get(), b.get())

      override def get(timeout: Long, unit: TimeUnit): C = {
        val startTime = System.nanoTime()
        val aa = a.get(timeout, unit)
        val endTime = System.nanoTime()

        val timeTaken = endTime - startTime
        val totalNumberOfNanosAvailable = unit.convert(timeout, TimeUnit.NANOSECONDS)

        val timeforB = totalNumberOfNanosAvailable - timeTaken
        val bb = b.get(timeforB, TimeUnit.NANOSECONDS)
        f(aa, bb)
      }
    }
  }

  /* 7.4 write asyncF in terms of lazy unit*/

  def asyncF[A, B](f: A => B): A => Par[B] = a => lazyUnit(f(a))

  def map[A, B](pa: Par[A])(f: A => B): Par[B] =
    map2(pa, unit(()))((a, _) => f(a))

  /* 7.5 write sequence */

  def sequence[A](ps: List[Par[A]]): Par[List[A]] = es => {
    UnitFuture(ps.map(a => a(es)).map(_.get))
  }

  def sequence2[A](l: List[Par[A]]): Par[List[A]] =
    l.foldRight[Par[List[A]]](unit(List()))((h, t) => map2(h, t)(_ :: _))

  //  def sequenceBalanced[A](l: List[Par[A]]): Par[List[A]] = {
  //
  //  }

  def parMap[A, B](ps: List[A])(f: A => B): Par[List[B]] = fork {
    val fbs: List[Par[B]] = ps.map(asyncF(f))
    sequence(fbs)
  }

  /* 7.6 implement parFilter */

  def parFilter[A](as: List[A])(f: A => Boolean): Par[List[A]] = {
    val pars: List[Par[List[A]]] = as.map(asyncF((a: A) => if (f(a)) List(a) else List()))
    val ps: Par[List[List[A]]] = sequence(pars)
    map(ps)(_.flatten)
  }

  /* 7.7 given map(y)(id) = y, prove map(map(y)(g))(f) == map(y)(f compose g) */

  /*
     map(y)(id) == y

     map(y)(g) = g(y) = x                                <- (1)

     map(x)(f) = f(x)
     map(map(y)(g))(f) = f(g(y))                         <- using (1)

     (f compose g) = h                                   <- (2)

     map(map(y)(g))(f) = h(y)

     map(map(y)(g))(f) = map(y)(h)                       <- using (1)

     map(map(y)(g))(f) == map(y)(f compose g)            <- using(2)

 */

  def equal[A](e: ExecutorService)(p: Par[A], p2: Par[A]): Boolean =
    p(e).get == p2(e).get

  def delay[A](a: => Par[A]): Par[A] = es => a(es)
}


object ParDifferentSequences extends App {
  val namedThreadFactory = new ThreadFactoryBuilder()
    .setNameFormat("es-thread-%d")
    .build()

  val es: ExecutorService = Executors.newFixedThreadPool(2, namedThreadFactory)

  val one = Par.lazyUnit(get(1))
  val two = Par.lazyUnit(get(2))
  val three = Par.lazyUnit(get(3))
  val four = Par.lazyUnit(get(4))
  val five = Par.lazyUnit(get(5))
  val six = Par.lazyUnit(get(6))

  val sequenced = Par.sequence2(List(one, two, three, four, five, six))

  val res = Par.run(es)(sequenced)
  println(res.get)

  def get(i: Int): Int = {
    val thread = Thread.currentThread().getName
    val time1 = System.currentTimeMillis() / 100 % 1000
    println(s"$thread | $time1 | Starting | $i ")
    Thread.sleep(i * 3000)
    val time2 = System.currentTimeMillis() / 100 % 1000
    println(s"$thread | $time2 | Finishing | $i ")
    println("-----------------------------------------------")
    i
  }

  es.shutdown()
}

object BadMySequence extends App {

  val namedThreadFactory = new ThreadFactoryBuilder()
    .setNameFormat("noodle-%d")
    .build()

  val es: ExecutorService = Executors.newFixedThreadPool(2, namedThreadFactory)

  val one = Par.lazyUnit(get(1))
  val two = Par.lazyUnit(get(2))

  val sequenced = Par.fork(Par.fork(Par.sequence2(List(one, two))))

  val res = Par.run(es)(sequenced)
  println(res.get)
  es.shutdown()

  def get(i: Int): Int = i

}

object ParSequence extends App {
  val namedThreadFactory = new ThreadFactoryBuilder()
    .setNameFormat("es-thread-%d")
    .build()

  val es: ExecutorService = Executors.newFixedThreadPool(2, namedThreadFactory)

  //  val one = Par.fork(Par.unit(get(1)))
  //  val two = Par.fork(Par.unit(get(2)))

  val one = Par.lazyUnit(get(1))
  val two = Par.lazyUnit(get(2))

  //  val one = Par.unit(get(1))
  //  val two  = Par.unit(get(2))

  val sequenced = Par.sequence(List(one, two))

  val res = Par.run(es)(sequenced)
  println(res.get)

  def get(i: Int): Int = {
    val thread = Thread.currentThread().getName;
    println(thread + ": Getting " + i)
    Thread.sleep(5000)
    i
  }
}

import java.util.concurrent.ThreadFactory

class CatThreadFactory extends ThreadFactory {
  override def newThread(r: Runnable) = new Thread(r, "Meow")
}



