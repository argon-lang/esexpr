package esexpr

import scala.annotation.StaticAnnotation

final case class keyword(name: String = "") extends StaticAnnotation
