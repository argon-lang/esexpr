package esexpr

import zio.test.*
import zio.test.Assertion.*
import zio.*
import zio.stream.*
import io.circe.Json
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
                ESExpr.Binary(Chunk.fromArray(Base64.getDecoder().decode(binValue)))
              }
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
}
