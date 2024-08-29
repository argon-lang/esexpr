package esexpr

import dev.argon.esexpr.ESExprBinaryWriter
import dev.argon.util.async.ZStreamFromOutputStreamWriterZIO
import zio.*
import zio.stream.*

import scala.jdk.CollectionConverters.*

object ESExprBinaryEncoder {
  def buildStringTable[R, E](exprs: ZStream[R, E, ESExpr]): ZIO[R, E, StringTable] =
    ZIO.succeed { ESExprBinaryWriter.SymbolTableBuilder() }
      .tap { builder =>
        exprs.foreach { expr =>
          ZIO.succeed { builder.add(ESExpr.toJava(expr)) }
        }
      }
      .flatMap { builder =>
        ZIO.succeed { builder.build() }
      }
      .map(StringTable.fromJava)

  def write(stringTable: StringTable, expr: ESExpr): UStream[Byte] =
    ZStreamFromOutputStreamWriterZIO { os =>
      ZIO.succeed { ESExprBinaryWriter(stringTable.values.asJava, os).write(ESExpr.toJava(expr)) }
    }

  def writeWithSymbolTable(expr: ESExpr): UStream[Byte] =
    ZStreamFromOutputStreamWriterZIO { os =>
      ZIO.succeed {
        ESExprBinaryWriter.writeWithSymbolTable(os, ESExpr.toJava(expr))
      }
    }
}
