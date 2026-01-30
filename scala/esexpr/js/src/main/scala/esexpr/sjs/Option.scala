package esexpr.sjs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

type Option[+A] = Option.Some[A] | Null
object Option {
  opaque type Some[+A] = A | WrappedNull

  def some[A](value: A): Some[A] = JSOptionObject.some(value)
  def get[A](value: Some[A]): A = JSOptionObject.get(value)
  
  def fromScalaOption[A](o: scala.Option[A]): Option[A] =
    o.orNull
  
  def toScalaOption[A](o: Option[A]): scala.Option[A] =
    scala.Option(o).map(get)

  @JSImport("@argon-lang/esexpr", "Option")
  @js.native
  private object JSOptionObject extends js.Object {
    def some[A](value: A): Some[A] = js.native
    def get[A](value: Some[A]): A = js.native
  }

}
