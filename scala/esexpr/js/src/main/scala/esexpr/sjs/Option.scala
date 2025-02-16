package esexpr.sjs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

type Option[+A] = Option.Some[A] | Null
object Option {
  opaque type Some[+A] = A | WrappedNull
  
  @JSImport("@argon-lang/esexpr")
  @js.native
  def some[A](value: A): Some[A] = js.native
  
  @JSImport("@argon-lang/esexpr")
  @js.native
  def get[A](value: Some[A]): A = js.native
  
  def fromScalaOption[A](o: scala.Option[A]): Option[A] =
    o.orNull
  
  def toScalaOption[A](o: Option[A]): scala.Option[A] =
    if o.asInstanceOf[AnyRef | Null] eq null then
      None
    else
      Some(get(o.asInstanceOf[Some[A]]))
      
}
