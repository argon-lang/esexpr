package esexpr

import io.circe.DecodingFailure.Reason.WrongTypeExpectation
import zio.test.*
import zio.test.Assertion.*
import zio.*
import zio.stream.*
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import esexpr.toFloat16


import java.util.Base64


object BinaryEncodingTests extends ZIOSpecDefault with EsxbTestLoader {
  override val testCaseDir: String = "../tests"

  override def spec = suite("ESExpr Binary Encoding")(
    Spec.scoped(
      loadTestCases()
        .map { testCase =>
          suite(testCase.name)(
            test("matches JSON") {
              for
                jsonValue <- ZIO.fromEither(io.circe.parser.parse(testCase.jsonContent))
                exprs <- ESExprBinaryDecoder.readAll(ZStream.fromChunk(testCase.esxbData)).runCollect
                jsonExprs = decodeJsonFile(jsonValue)
              yield assertTrue(exprs == jsonExprs)
            },
            test("round-trip") {
              for
                exprs <- ESExprBinaryDecoder.readAll(ZStream.fromChunk(testCase.esxbData)).runCollect
                reencoded <- ESExprBinaryDecoder.readAll(ESExprBinaryEncoder.writeAll(ZStream.fromIterable(exprs))).runCollect
              yield assertTrue(exprs == reencoded)
            },
          )
        }
        .runCollect
        .tap { tests => if tests.isEmpty then ZIO.die(RuntimeException("Could not load test cases")) else ZIO.unit }
        .map(Spec.multiple)
    )
  )

  private def decodeJsonFile(json: Json): Seq[ESExpr] =
    json.asArray match {
      case Some(arr) => arr.map(decodeJsonExpr)
      case _ => Seq(decodeJsonExpr(json))
    }

  private def decodeJsonExpr(json: Json): ESExpr =
    json.fold(
      jsonNull = ESExpr.Null(0),
      jsonBoolean = ESExpr.Bool.apply,
      jsonNumber = _ => throw new Exception("Unexpected JSON number"),
      jsonString = ESExpr.Str.apply,
      jsonArray = arr => ESExpr.Constructor("list", arr.map(decodeJsonExpr), Map()),
      jsonObject = obj => {
        json.hcursor.downField("constructor_name").as[String].toOption
          .map { ctorName =>
            val args = json.hcursor.downField("args").as[Vector[Json]].getOrElse(Vector()).map(decodeJsonExpr)
            
            // Handle keyword arguments (kwargs)
            val kwargs = json.hcursor.downField("kwargs").as[Map[String, Json]].getOrElse(Map()).view.mapValues(decodeJsonExpr).toMap

            ESExpr.Constructor(ctorName, args, kwargs)
          }
          .orElse {
            json.hcursor.downField("int").as[String].toOption
              .map { intValue =>
                ESExpr.Int(BigInt(intValue))
              }
          }
          .orElse {
            json.hcursor.downField("float16").as[Json].toOption
              .map { floatValue =>
                ESExpr.Float16(floatValue.asString match {
                  case Some("+inf") => Float16.PositiveInfinity
                  case Some("-inf") => Float16.NegativeInfinity
                  case _ => floatValue.asNumber.get.toFloat.toFloat16
                })
              }
          }
          .orElse {
            json.hcursor.downField("float32").as[Json].toOption
              .map { floatValue =>
                ESExpr.Float32(floatValue.asString match {
                  case Some("+inf") => Float.PositiveInfinity
                  case Some("-inf") => Float.NegativeInfinity
                  case _ => floatValue.asNumber.get.toFloat
                })
              }
          }
          .orElse {
            json.hcursor.downField("float64").as[Json].toOption
              .map { floatValue =>
                ESExpr.Float64(floatValue.asString match {
                  case Some("+inf") => Double.PositiveInfinity
                  case Some("-inf") => Double.NegativeInfinity
                  case _ => floatValue.asNumber.get.toDouble
                })
              }
          }
          .orElse {
            json.hcursor.downField("base64").as[String].toOption
              .map { binValue =>
                ESExpr.Array8(Chunk.fromArray(Base64.getDecoder().decode(binValue)))
              }
          }
          .orElse {
            json.hcursor
              .downField("array8")
              .as[Seq[Byte]](using arrayNDecoder(_.toByte))
              .map(a => ESExpr.Array8(Chunk.fromIterable(a)))
              .toOption
          }
          .orElse {
            json.hcursor
              .downField("array16")
              .as[Seq[Short]](using arrayNDecoder(_.toShort))
              .map(a => ESExpr.Array16(Chunk.fromIterable(a)))
              .toOption
          }
          .orElse {
            json.hcursor
              .downField("array32")
              .as[Seq[Int]](using arrayNDecoder(_.toInt))
              .map(a => ESExpr.Array32(Chunk.fromIterable(a)))
              .toOption
          }
          .orElse {
            json.hcursor
              .downField("array64")
              .as[Seq[Long]](using arrayNDecoder(_.toLong))
              .map(a => ESExpr.Array64(Chunk.fromIterable(a)))
              .toOption
          }
          .orElse {
            json.hcursor
              .downField("array128")
              .as[Seq[BigInt]](using arrayNDecoder(identity))
              .map(a => ESExpr.Array128(Chunk.fromIterator(a.iterator.flatMap(i => Seq(i.toLong, (i >> 64).toLong)))))
              .toOption
          }
          .orElse {
            json.hcursor.downField("null").as[String].toOption
              .map { nullLevel =>
                ESExpr.Null(BigInt(nullLevel))
              }
          }
          .getOrElse { throw new Exception("Unexpected JSON object: " + obj) }
      }
    )

  def arrayNDecoder[I](f: BigInt => I): Decoder[Seq[I]] = {
    given Decoder[I]:
      override def apply(c: HCursor): Decoder.Result[I] =
        if c.value.isNumber then
          c.value.as[BigInt].map(f)
        else if c.value.isString then
          c.value.as[String].map(s => f(BigInt(s)))
        else
          Left(DecodingFailure(WrongTypeExpectation("string or int", c.value), c.history))
    end given

    summon[Decoder[Seq[I]]]
  }
}
