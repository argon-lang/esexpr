package esexpr

import dev.argon.esexpr.{ESExpr as JESExpr, ESExprBinaryReader, SyntaxException}

import dev.argon.util.async.ErrorWrapper
import scala.jdk.CollectionConverters.*
import java.util.stream.Stream as JStream
import java.io.{IOException, InputStream}
import zio.{ZIO, Cause}
import zio.stream.ZStream
import scala.reflect.TypeTest

object ESExprBinaryDecoder {
  def readAll[R, E](data: ZStream[R, E, Byte])(stringTable: Seq[String])(using ErrorWrapper[E]): ZStream[R, E | IOException | ESExprFormatException, ESExpr] =
    readWith(data) { is => new ESExprBinaryReader(stringTable.asJava, is).readAll() }

  def readEmbeddedStringTable[R, E](data: ZStream[R, E, Byte])(using ErrorWrapper[E]): ZStream[R, E | IOException | ESExprFormatException, ESExpr] =
    readWith(data)(ESExprBinaryReader.readEmbeddedStringTable)



  private def readWith[R, E](data: ZStream[R, E, Byte])(f: InputStream => java.util.stream.Stream[JESExpr])(using errorWrapper: ErrorWrapper[E]): ZStream[R, E | IOException | ESExprFormatException, ESExpr] =
    ZStream.scoped[R](ErrorWrapper.wrapStream(data).toInputStream)
      .mapZIO { is =>
        ZIO.attempt {
            f(is)
          }
      }
      .catchAll {
        case ex: errorWrapper.EX => ZStream.failCause(errorWrapper.unwrap(ex))
        case ex: IOException => ZStream.fail(ex)
        case ex: SyntaxException => ZStream.fail(ESExprFormatException("Error decoding expression", ex))
        case ex => ZStream.die(ex)
      }
      .flatMap { s =>
        ZStream.fromJavaStream(s)
          .refineOrDie[Throwable] {
            case ex: RuntimeException if ex.getCause() ne null => ex.getCause().nn
          }
          .refineOrDie[IOException | ESExprFormatException] {
            case ex: IOException => ex
            case ex: SyntaxException => ESExprFormatException("Error decoding expression", ex)
          }
      }
      .map(ESExpr.fromJava)
}
