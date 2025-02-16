package esexpr.sjs

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSImport, JSName}

trait WrappedNull extends js.Any {
  @JSName(WrappedNull.wrappedNullLevelSymbol)
  val level: Int
}

object WrappedNull {
  @JSImport("@argon-lang/esexpr")
  @js.native
  val wrappedNullLevelSymbol: js.Symbol = js.native
}

