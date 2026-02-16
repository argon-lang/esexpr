package esexpr

import dev.argon.esexpr.ESExprBinaryWriter
import dev.argon.util.async.{ErrorWrapper, ZStreamFromOutputStreamWriterZIO}
import zio.*
import zio.stream.*

import scala.annotation.unused

object ESExprBinaryEncoder {

  def writeAll[R, E](exprs: ZStream[R, E, ESExpr])(using @unused ew: ErrorWrapper[E]): ZStream[R, E, Byte] =
    ZStreamFromOutputStreamWriterZIO { os =>
      ZIO.succeed { ESExprBinaryWriter(os) }
        .flatMap { writer =>
          exprs.foreach(expr => ZIO.succeed(writer.write(ESExpr.toJava(expr))))
        }
    }

}
