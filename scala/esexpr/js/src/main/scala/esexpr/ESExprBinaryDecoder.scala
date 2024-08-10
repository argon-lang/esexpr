package esexpr

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import dev.argon.util.async.ErrorWrapper
import dev.argon.util.async.AsyncIterableTools
import dev.argon.util.async.AsyncIterableTools.AsyncIterable
import dev.argon.util.async.TypedArrayUtil
import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.JavaScriptException
import scala.scalajs.js.JSConverters.*

import java.io.IOException

import zio.{ZIO, Cause, Runtime}
import zio.stream.ZStream
import scala.reflect.TypeTest

object ESExprBinaryDecoder {
  
  @JSImport("@argon-lang/esexpr/binary_format.js")
  @js.native
  private class ESExprFormatError(message: String = js.native) extends js.Error


  @JSImport("@argon-lang/esexpr/binary_format.js")
  @js.native
  private def readExprStream(data: AsyncIterable[Uint8Array], stringPool: StringPool): AsyncIterable[esexpr.sjs.ESExpr] = js.native


  @JSImport("@argon-lang/esexpr/binary_format.js")
  @js.native
  private def readExprStreamEmbeddedStringPool(data: AsyncIterable[Uint8Array]): AsyncIterable[esexpr.sjs.ESExpr] = js.native

  @js.native
  private trait StringPool extends js.Object {
    def get(i: Int): String
    def lookup(s: String): Int
  }

  @JSImport("@argon-lang/esexpr/binary_format.js")
  @js.native
  private object StringPool extends js.Object {
    def fromArray(values: js.Array[String]): StringPool = js.native
  }


  def readAll[R, E >: IOException | ESExprFormatException](data: ZStream[R, E, Byte])(stringTable: Seq[String])(using ErrorWrapper[E]): ZStream[R, E | IOException | ESExprFormatException, ESExpr] =
    readWith(data) { b => readExprStream(b, StringPool.fromArray(stringTable.toJSArray)) }

  def readEmbeddedStringTable[R, E >: IOException | ESExprFormatException](data: ZStream[R, E, Byte])(using ErrorWrapper[E]): ZStream[R, E | IOException | ESExprFormatException, ESExpr] =
    readWith(data)(readExprStreamEmbeddedStringPool)
    


  private def readWith[R, E >: IOException | ESExprFormatException, EX <: Throwable](data: ZStream[R, E, Byte])(f: AsyncIterable[Uint8Array] => AsyncIterable[esexpr.sjs.ESExpr])(using errorWrapper: ErrorWrapper[E]): ZStream[R, E, ESExpr] =
    ZStream.unwrap(
        ZIO.runtime[R]
          .flatMap { rt =>
            given Runtime[R] = rt
            ZIO.attempt {
              val iter = AsyncIterableTools.zstreamToAsyncIterable(data.chunks.map(TypedArrayUtil.fromByteChunk))
              f(iter)
            }
          }
          .refineOrDie[ESExprFormatException] {
            case JavaScriptException(e) if e.isInstanceOf[ESExprFormatError] => ESExprFormatException(e.asInstanceOf[ESExprFormatError].message)
          }
          .map { s =>
            AsyncIterableTools.asyncIterableToZStreamRaw(s)
              .refineOrDie[ESExprFormatException] {
                case JavaScriptException(e) if e.isInstanceOf[ESExprFormatError] => ESExprFormatException(e.asInstanceOf[ESExprFormatError].message)
              }
              .map(ESExpr.fromJS)
          }
    )
}
