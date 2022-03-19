package part1_recap

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object ScalaRecap extends App {
  val aCondition: Boolean = false
  def myFunction(x: Int) = {
    // code here
    if (x > 4) 42 else 65
  }
  // instructions vs expressions
  // types + type inference

  // OO features of Scala
  class Animal
  trait Carnivore {
    def ear(a: Animal): Unit
  }
  object Carnivore

  // generics
  abstract class MyList[+A]

  // method notations
  1 + 2 // infix notation
  1.+(2)

  // FP
  val anIncrementer: Int => Int = (x: Int) => x + 1
  anIncrementer(1)

  List(1,2,3).map(anIncrementer)
  // Higher order functions: map, flatMap, filter
  // for-comprehensions

  // Monads: Options, Try

  // Pattern matching
  val unknown: Any = 2
  val order = unknown match {
    case 1 => "first"
    case 2 => "second"
    case _ => "unknown"
  }

  try {
    // code that can throw exception
    throw new RuntimeException
  } catch {
    case e: Exception => println("I caught one!")
  }

  /**
   * Scala Advanced
   */
  // multithreading
  val future = Future {
    // long computation
    // executed on some other thread
    42
  }
  // map, flatMap, filter + other niceties e.g. recover/recoverWith

  future.onComplete {
    case Success(value) => println(s"I found success value: $value")
    case Failure(exception) => println(s"Exception occured: ${exception.getMessage}")
  } // executed on SOME thread

  val partialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 65
    case _ => 1000
  } // will throw MatchError when none matches

  // type aliases
  type AkkaReceive = PartialFunction[Any, Unit]
  def receive: AkkaReceive = {
    case 1 => println("Hello!")
    case _ => println("confused...")
  }

  // Implicits!
  implicit val timeout = 3000
  def setTimeout(f: () => Unit)(implicit timeout: Int): Unit = f()

  setTimeout(() => println("Timeout"))  // other arg list injected by the compiler

  // conversions
  // 1) implicit methods
  case class Person(name: String) {
    def greet: String = s"Hi, My name is $name"
  }

  implicit def fromStringToPerson(name: String): Person = Person(name)
  "Mukesh".greet // will work because of implicit method
  // fromStringToPerson("Mukesh").great

  // 2) implicit classes
  implicit class Dog(name: String) {
    def bark = println("Bark!")
  }
  "Lassie".bark
  // new Dog("Lassie").bark

  // implicit organization
  // 1. local scope
  implicit val numberOrdering: Ordering[Int] = Ordering.fromLessThan(_ > _)
  List(1,2,3).sorted//(numberOrdering) => List(3,2,1)

  // 2. imported scope
  // 3. companion object of the types involved in the call
  object Person {
    implicit val personOrdering: Ordering[Person] = Ordering.fromLessThan((a,b) => a.name.compareTo(b.name) < 0)
  }
  List(Person("Bob"), Person("Alice")).sorted // (Person.personOrdering)
  // => List(Person("Alice"), Person("Bob"))
}