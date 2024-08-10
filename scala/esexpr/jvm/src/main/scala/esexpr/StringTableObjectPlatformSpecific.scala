package esexpr

import scala.jdk.CollectionConverters.*

trait StringTableObjectPlatformSpecific {
  
  def toJava(st: StringTable): dev.argon.esexpr.StringTable =
    dev.argon.esexpr.StringTable(st.values.asJava)
    
  def fromJava(st: dev.argon.esexpr.StringTable): StringTable =
    StringTable(st.values().asScala.toSeq)
  
}
