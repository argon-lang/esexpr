package esexpr

import dev.argon.util.async.{AsyncIterableTools, TypedArrayUtil}
import dev.argon.util.async.AsyncIterableTools.AsyncIterable
import zio.*
import zio.stream.*

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

object ESExprBinaryEncoder {
  private trait StringPool extends js.Object {
    def get(i: Int): String
    def lookup(s: String): Int
  }

  trait StringPoolEncoded extends js.Object {
    val values: js.Array[String]
  }

  @js.native
  @JSImport("@argon-lang/esexpr/binary_format.js")
  private class ArrayStringPool(values: js.Array[String]) extends StringPool {
    override def get(i: RuntimeFlags): String = js.native
    override def lookup(s: String): RuntimeFlags = js.native
    def toEncoded(): StringPoolEncoded = js.native
  }

  @js.native
  @JSImport("@argon-lang/esexpr/binary_format.js")
  private object ArrayStringPool extends js.Object {
    def fromEncoded(encoded: StringPoolEncoded): ArrayStringPool = js.native
  }

  @js.native
  @JSImport("@argon-lang/esexpr/binary_format.js")
  private class StringPoolBuilder() extends js.Object {
    def adapter(): StringPool = js.native
    def toStringPool(): ArrayStringPool = js.native
  }

  @js.native
  @JSImport("@argon-lang/esexpr/binary_format.js")
  private def writeExpr(e: sjs.ESExpr, stringPool: StringPool): AsyncIterable[Uint8Array] = js.native

  def buildStringTable[R, E](exprs: ZStream[R, E, ESExpr]): ZIO[R, E, StringTable] =
    ZIO.succeed { StringPoolBuilder() }
      .tap { builder =>
        exprs.foreach { expr =>
          AsyncIterableTools.asyncIterableToZStreamRaw(writeExpr(ESExpr.toJS(expr), builder.adapter()))
            .orDie
            .runDrain
        }
      }
      .flatMap { builder =>
        ZIO.succeed { builder.toStringPool() }
      }
      .map(sp => StringTable.fromJS(sp.toEncoded()))

  def write(stringTable: StringTable, expr: ESExpr): UStream[Byte] =
    AsyncIterableTools.asyncIterableToZStreamRaw(writeExpr(ESExpr.toJS(expr), ArrayStringPool.fromEncoded(StringTable.toJS(stringTable))))
      .orDie
      .map(TypedArrayUtil.toByteChunk)
      .flattenChunks

  def writeWithSymbolTable(expr: ESExpr): UStream[Byte] =
    ZStream.unwrap(
      buildStringTable(ZStream(expr)).map { st =>
        write(StringTable(Seq.empty), summon[ESExprCodec[StringTable]].encode(st)) ++
          write(st, expr)
      }
    )
}
