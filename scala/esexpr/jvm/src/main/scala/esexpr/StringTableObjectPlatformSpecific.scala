package esexpr

import scala.jdk.CollectionConverters.*
import com.google.common.collect.ImmutableList

trait StringTableObjectPlatformSpecific {
  
  def toJava(st: StringTable): dev.argon.esexpr.StringTable =
    dev.argon.esexpr.StringTable(ImmutableList.copyOf(st.values.asJava))
    
  def fromJava(st: dev.argon.esexpr.StringTable): StringTable =
    StringTable(st.values().asScala.toSeq)
  
}
