package esexpr

import scala.annotation.StaticAnnotation

final case class defaultValue[+A](value: A) extends StaticAnnotation
