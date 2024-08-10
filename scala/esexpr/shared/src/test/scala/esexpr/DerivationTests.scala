package esexpr

import zio.*
import zio.stream.*
import zio.test.{TestExecutor as _, *}
import zio.test.Assertion.*

object DerivationTests extends ZIOSpecDefault {

  final case class ConstructorName123Conversion(
    a: Int,
  ) derives ESExprCodec, CanEqual

  @constructor("my-ctor")
  final case class CustomConstructorName(
    a: Int,
  ) derives ESExprCodec, CanEqual

  enum ConstructorNameEnum derives ESExprCodec, CanEqual {
    case MyName123Test

    @constructor("my-ctor")
    case CustomName
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Derivation Tests")(
      suite("Constructor Name Conversion")(

        codecTest("record")(
          tags = Some(Set(ESExprTag.Constructor("constructor-name123-conversion"))),
          expr = ESExpr.Constructor("constructor-name123-conversion", args = Seq(ESExpr.Int(5)), kwargs = Map()),
          value = ConstructorName123Conversion(5),
          invalidExprs = Seq(ESExpr.Constructor("bad-name", args = Seq(ESExpr.Int(5)), kwargs = Map())),
        ),
        codecTest("enum")(
          tags = Some(Set(ESExprTag.Constructor("my-name123-test"), ESExprTag.Constructor("my-ctor"))),
          expr = ESExpr.Constructor("my-name123-test", args = Seq(), kwargs = Map()),
          value = ConstructorNameEnum.MyName123Test,
          invalidExprs = Seq(ESExpr.Constructor("bad-name", args = Seq(), kwargs = Map())),
        ),
      ),

      suite("Custom Constructor Name")(
        codecTest("record")(
          tags = Some(Set(ESExprTag.Constructor("my-ctor"))),
          expr = ESExpr.Constructor("my-ctor", args = Seq(ESExpr.Int(5)), kwargs = Map()),
          value = CustomConstructorName(5),
          invalidExprs = Seq(ESExpr.Constructor("bad-name", args = Seq(ESExpr.Int(5)), kwargs = Map())),
        ),
        codecTest("enum")(
          expr = ESExpr.Constructor("my-ctor", args = Seq(), kwargs = Map()),
          value = ConstructorNameEnum.CustomName,
        ),
      ),

      {
        enum InlineValueTest derives ESExprCodec, CanEqual {
          @inlineValue
          case Flag(b: Boolean)

          case NormalCase(b: Boolean)
        }

        suite("Inline value")(
          codecTest("inline case")(
            tags = Some(Set(ESExprTag.Constructor("normal-case"), ESExprTag.Bool)),
            expr = ESExpr.Bool(true),
            value = InlineValueTest.Flag(true),
            invalidExprs = Seq(ESExpr.Constructor("flag", Seq(ESExpr.Bool(true)), Map())),
          ),
          codecTest("normal case")(
            expr = ESExpr.Constructor("normal-case", Seq(ESExpr.Bool(true)), Map()),
            value = InlineValueTest.NormalCase(true),
          ),
        )
      },

      {
        final case class KeywordStruct(
          @keyword a: Boolean,
          @keyword("b2") b: Boolean,
          @keyword("c2") c: Option[Boolean],
          @keyword("d2") @optional d: Option[Boolean],
          @keyword @optional e: Option[Boolean],
          @keyword @defaultValue(false) g: Boolean,
          @keyword f: Boolean = false,
        ) derives ESExprCodec, CanEqual

        enum KeywordEnum derives ESExprCodec, CanEqual {
          case Value(
            @keyword a: Boolean,
            @keyword("b2") b: Boolean,
            @keyword("c2") c: Option[Boolean],
            @keyword("d2") @optional d: Option[Boolean],
            @keyword @optional e: Option[Boolean],
            @keyword @defaultValue(false) g: Boolean,
            @keyword f: Boolean = false,
          )
        }

        suite("Keyword")(
          codecTest("struct")(
            expr = ESExpr.Constructor("keyword-struct", Seq(), Map(
              "a" -> ESExpr.Bool(true),
              "b2" -> ESExpr.Bool(true),
              "c2" -> ESExpr.Bool(true),
              "d2" -> ESExpr.Bool(true),
              "e" -> ESExpr.Bool(true),
              "f" -> ESExpr.Bool(true),
              "g" -> ESExpr.Bool(true),
            )),
            value = KeywordStruct(
              a = true,
              b = true,
              c = Some(true),
              d = Some(true),
              e = Some(true),
              f = true,
              g = true,
            ),
            invalidExprs = Seq(
              ESExpr.Constructor("keyword-struct", Seq(), Map(
                "a" -> ESExpr.Bool(true),
                "b2" -> ESExpr.Bool(true),
                "d2" -> ESExpr.Bool(true),
                "e" -> ESExpr.Bool(true),
                "f" -> ESExpr.Bool(true),
                "g" -> ESExpr.Bool(true),
              )),
            ),
          ),
          codecTest("struct optional")(
            expr = ESExpr.Constructor("keyword-struct", Seq(), Map(
              "a" -> ESExpr.Bool(true),
              "b2" -> ESExpr.Bool(true),
              "c2" -> ESExpr.Null,
            )),
            value = KeywordStruct(
              a = true,
              b = true,
              c = None,
              d = None,
              e = None,
              f = false,
              g = false,
            ),
          ),

          codecTest[KeywordEnum]("enum")(
            expr = ESExpr.Constructor("value", Seq(), Map(
              "a" -> ESExpr.Bool(true),
              "b2" -> ESExpr.Bool(true),
              "c2" -> ESExpr.Bool(true),
              "d2" -> ESExpr.Bool(true),
              "e" -> ESExpr.Bool(true),
              "f" -> ESExpr.Bool(true),
              "g" -> ESExpr.Bool(true),
            )),
            value = KeywordEnum.Value(
              a = true,
              b = true,
              c = Some(true),
              d = Some(true),
              e = Some(true),
              f = true,
              g = true,
            ),
            invalidExprs = Seq(
              ESExpr.Constructor("value", Seq(), Map(
                "a" -> ESExpr.Bool(true),
                "b2" -> ESExpr.Bool(true),
                "d2" -> ESExpr.Bool(true),
                "e" -> ESExpr.Bool(true),
                "f" -> ESExpr.Bool(true),
                "g" -> ESExpr.Bool(true),
              )),
            ),
          ),
          codecTest[KeywordEnum]("struct optional")(
            expr = ESExpr.Constructor("value", Seq(), Map(
              "a" -> ESExpr.Bool(true),
              "b2" -> ESExpr.Bool(true),
              "c2" -> ESExpr.Null,
            )),
            value = KeywordEnum.Value(
              a = true,
              b = true,
              c = None,
              d = None,
              e = None,
              f = false,
              g = false,
            ),
          ),
        )
      },

      {
        @simple enum SimpleEnum derives ESExprCodec, CanEqual {
          case A, B

          @constructor("my-c")
          case C
        }

        suite("SimpleEnum")(
          codecTest("A")(
            tags = Some(Set(ESExprTag.Str)),

            expr = ESExpr.Str("a"),
            value = SimpleEnum.A,

            invalidExprs = Seq(
              ESExpr.Str("d"),
            ),
          ),
          codecTest("B")(
            expr = ESExpr.Str("b"),
            value = SimpleEnum.B,
          ),
          codecTest("C")(
            expr = ESExpr.Str("my-c"),
            value = SimpleEnum.C,

            invalidExprs = Seq(
              ESExpr.Str("c"),
            ),
          ),
        )
      },

      {
        @constructor("many")
        final case class ManyArgsRecord(
          @dict kwargs: Map[String, Boolean],
          @vararg args: Seq[Boolean],
        ) derives ESExprCodec, CanEqual

        enum ManyArgsEnum derives ESExprCodec, CanEqual {
          @constructor("many")
          case Value(
            @dict kwargs: Map[String, Boolean],
          @vararg args: Seq[Boolean],
          )
        }

        val expr = ESExpr.Constructor(
          "many",
          Seq(ESExpr.Bool(true), ESExpr.Bool(true), ESExpr.Bool(false)),
          Map(
            "a" -> ESExpr.Bool(true),
            "b" -> ESExpr.Bool(true),
            "c" -> ESExpr.Bool(false),
          ),
        )

        suite("Many")(
          codecTest("record")(
            expr = expr,
            value = ManyArgsRecord(
              Map("a" -> true, "b" -> true, "c" -> false),
              Seq(true, true, false),
            ),
          ),
          codecTest("enum")(
            expr = expr,
            value = ManyArgsEnum.Value(
              Map("a" -> true, "b" -> true, "c" -> false),
              Seq(true, true, false),
            ),
          ),
        )
      }
    )


  private def codecTest[A](
    name: String,
  )(
    expr: ESExpr,
    value: A,

    tags: Option[Set[ESExprTag]] = None,
    invalidExprs: Seq[ESExpr] = Seq(),
  )(using codec: ESExprCodec[A], eqA: CanEqual[A, A]): Spec[TestEnvironment & Scope, Any] =
    suite(name)((
      tags.toSeq.map(tags => test("tags") {
        assertTrue(codec.tags == tags)
      }) ++
        Seq(
          test("encode") {
            assertTrue(expr == codec.encode(value))
          },

          test("decode") {
            assertTrue(value == codec.decode(expr).toOption.get)
          },
        ) ++ (
          if invalidExprs.nonEmpty then
            Seq(test("decode invalid") {
              assertTrue(invalidExprs.forall(codec.decode(_).isLeft))
            })
          else
            Seq()
      )
    )*)

}
