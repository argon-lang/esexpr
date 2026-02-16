package esexpr

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import dev.argon.util.async.ErrorWrapper
import dev.argon.util.async.AsyncIterableTools
import dev.argon.util.async.AsyncIterableTools.AsyncIterable
import dev.argon.util.async.TypedArrayUtil

import scala.scalajs.js.typedarray.Uint8Array
import scala.scalajs.js.JavaScriptException
import java.io.IOException
import zio.{Runtime, ZIO}
import zio.stream.ZStream

import scala.annotation.unused


object ESExprBinaryDecoder {
  
  @JSImport("@argon-lang/esexpr/binary_format")
  @js.native
  private class ESExprFormatError(@unused message: String = js.native) extends js.Error


  @JSImport("@argon-lang/esexpr/binary_format")
  @js.native
  private def readExprStream(data: AsyncIterable[Uint8Array]): AsyncIterable[esexpr.sjs.ESExpr] = js.native


  def readAll[R, E](data: ZStream[R, E, Byte])(using ErrorWrapper[E]): ZStream[R, E | IOException | ESExprFormatException, ESExpr] =
    readWith(data) { b => readExprStream(b) }

    


  private def readWith[R, E, EX <: Throwable](data: ZStream[R, E, Byte])(f: AsyncIterable[Uint8Array] => AsyncIterable[esexpr.sjs.ESExpr])(using errorWrapper: ErrorWrapper[E]): ZStream[R, E | IOException | ESExprFormatException, ESExpr] =
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
