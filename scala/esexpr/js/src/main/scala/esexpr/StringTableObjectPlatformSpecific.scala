package esexpr

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

trait StringTableObjectPlatformSpecific {

  def fromJS(st: ESExprBinaryEncoder.StringPoolEncoded): StringTable =
    StringTable(st.values.toSeq)

  def toJS(st: StringTable): ESExprBinaryEncoder.StringPoolEncoded =
    new js.Object with ESExprBinaryEncoder.StringPoolEncoded {
      override val values: js.Array[String] = st.values.toJSArray
    }

}
