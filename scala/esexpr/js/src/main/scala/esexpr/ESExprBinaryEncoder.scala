package esexpr

import dev.argon.util.async.{AsyncIterableTools, TypedArrayUtil, ErrorWrapper}
import dev.argon.util.async.AsyncIterableTools.AsyncIterable
import zio.*
import zio.stream.*

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

object ESExprBinaryEncoder {
  
  trait StringPoolEncoded extends js.Object {
    val values: js.Array[String]
  }


  @js.native
  @JSImport("@argon-lang/esexpr/binary_format.js")
  private def writeExprs(e: AsyncIterable[sjs.ESExpr] | js.Iterable[sjs.ESExpr]): AsyncIterable[Uint8Array] = js.native

  def writeAll[R, E](exprs: ZStream[R, E, ESExpr])(using Runtime[R], ErrorWrapper[E]): ZStream[R, E, Byte] =
    ZStream.fromZIO(ZIO.runtime[R]).flatMap { runtime =>
      AsyncIterableTools.asyncIterableToZStreamRaw(
        writeExprs(
          AsyncIterableTools.zstreamToAsyncIterable(
            exprs.map(ESExpr.toJS)
          )
        )
      )
        .orDie
        .map(TypedArrayUtil.toByteChunk)
        .flattenChunks
    }

}
