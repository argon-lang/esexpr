package esexpr

import zio.*
import zio.test.*

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
        test("direct")(
          assertTrue(
            "test-abc" == ESExprCodec.CodecDerivation.toSExprName("TestABC") &&
              "test-name-with-parts" == ESExprCodec.CodecDerivation.toSExprName("TestNameWithParts") &&
              "test-abc-after" == ESExprCodec.CodecDerivation.toSExprName("TestABCAfter")
          )
        ),
        codecTest("record")(
          tags = Some(ESExprTagSet(ESExprTag.Constructor("constructor-name123-conversion"))),
          expr = ESExpr.Constructor("constructor-name123-conversion", args = Seq(ESExpr.Int(5)), kwargs = Map()),
          value = ConstructorName123Conversion(5),
          invalidExprs = Seq(ESExpr.Constructor("bad-name", args = Seq(ESExpr.Int(5)), kwargs = Map())),
        ),
        codecTest("enum")(
          tags = Some(ESExprTagSet(ESExprTag.Constructor("my-name123-test"), ESExprTag.Constructor("my-ctor"))),
          expr = ESExpr.Constructor("my-name123-test", args = Seq(), kwargs = Map()),
          value = ConstructorNameEnum.MyName123Test,
          invalidExprs = Seq(ESExpr.Constructor("bad-name", args = Seq(), kwargs = Map())),
        ),
      ),

      suite("Custom Constructor Name")(
        codecTest("record")(
          tags = Some(ESExprTagSet(ESExprTag.Constructor("my-ctor"))),
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
            tags = Some(ESExprTagSet(ESExprTag.Constructor("normal-case"), ESExprTag.Bool)),
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
              "c2" -> ESExpr.Null(0),
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
              "c2" -> ESExpr.Null(0),
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
            tags = Some(ESExprTagSet(ESExprTag.Str)),

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
      },

      {
        final case class MultipleVarargs(
          @vararg args1: Seq[Boolean],
          @vararg args2: Seq[String],
        ) derives ESExprCodec, CanEqual

        val expr = ESExpr.Constructor(
          "multiple-varargs",
          Seq(ESExpr.Bool(true), ESExpr.Bool(true), ESExpr.Bool(false), ESExpr.Str("A"), ESExpr.Str("B")),
          Map(),
        )

        codecTest("MultipleVarargs")(
          expr = expr,
          value = MultipleVarargs(
            Seq(true, true, false),
            Seq("A", "B"),
          ),
        )
      },

      {
        final case class OptionalPositional(
          @optional a: Option[Boolean],
          @optional b: Option[String],
        ) derives ESExprCodec, CanEqual

        suite("Optional positional")(
          codecTest("both")(
            expr = ESExpr.Constructor(
              "optional-positional",
              Seq(ESExpr.Bool(true), ESExpr.Str("A")),
              Map(),
            ),
            value = OptionalPositional(Some(true), Some("A")),
          ),
          codecTest("first")(
            expr = ESExpr.Constructor(
              "optional-positional",
              Seq(ESExpr.Bool(true)),
              Map(),
            ),
            value = OptionalPositional(Some(true), None),
          ),
          codecTest("both")(
            expr = ESExpr.Constructor(
              "optional-positional",
              Seq(ESExpr.Str("A")),
              Map(),
            ),
            value = OptionalPositional(None, Some("A")),
          ),
          codecTest("none")(
            expr = ESExpr.Constructor(
              "optional-positional",
              Seq(),
              Map(),
            ),
            value = OptionalPositional(None, None),
          ),
        )
      },

      {
        final case class DefaultPositional(
          a: Boolean = false,
          b: String = "X",
        ) derives ESExprCodec, CanEqual

        suite("Default positional")(
          codecTest("both")(
            expr = ESExpr.Constructor(
              "default-positional",
              Seq(ESExpr.Bool(true), ESExpr.Str("A")),
              Map(),
            ),
            value = DefaultPositional(true, "A"),
          ),
          codecTest("first")(
            expr = ESExpr.Constructor(
              "default-positional",
              Seq(ESExpr.Bool(true)),
              Map(),
            ),
            value = DefaultPositional(true, "X"),
          ),
          codecTest("both")(
            expr = ESExpr.Constructor(
              "default-positional",
              Seq(ESExpr.Str("A")),
              Map(),
            ),
            value = DefaultPositional(false, "A"),
          ),
          codecTest("none")(
            expr = ESExpr.Constructor(
              "default-positional",
              Seq(),
              Map(),
            ),
            value = DefaultPositional(false, "X"),
          ),
        )
      },

      {
        final case class DefaultPositional2(
          @defaultValue(false) a: Boolean,
          @defaultValue("X") b: String,
        ) derives ESExprCodec, CanEqual

        suite("Default positional2")(
          codecTest("both")(
            expr = ESExpr.Constructor(
              "default-positional2",
              Seq(ESExpr.Bool(true), ESExpr.Str("A")),
              Map(),
            ),
            value = DefaultPositional2(true, "A"),
          ),
          codecTest("first")(
            expr = ESExpr.Constructor(
              "default-positional2",
              Seq(ESExpr.Bool(true)),
              Map(),
            ),
            value = DefaultPositional2(true, "X"),
          ),
          codecTest("both")(
            expr = ESExpr.Constructor(
              "default-positional2",
              Seq(ESExpr.Str("A")),
              Map(),
            ),
            value = DefaultPositional2(false, "A"),
          ),
          codecTest("none")(
            expr = ESExpr.Constructor(
              "default-positional2",
              Seq(),
              Map(),
            ),
            value = DefaultPositional2(false, "X"),
          ),
        )
      },

      {
        @flags
        final case class TwoFlags(
          @flagmask(0b01)
          a: Boolean,
          @flagmask(0b10)
          b: Boolean,
        ) derives ESExprCodec, CanEqual



        enum FlagEnumB {
          @flagmask(0b000)
          case A
          
          @flagmask(0b010)
          case B
          
          @flagmask(0b100)
          case C
        }

        @flags
        final case class FlagWithEnum(
          @flagmask(0b001)
          a: Boolean,
          b: FlagEnumB
        ) derives ESExprCodec, CanEqual

        suite("Flags")(
          codecTest("two flags 0b00")(
            expr = ESExpr.Int(0b00),
            value = TwoFlags(false, false),
          ),
          codecTest("two flags 0b01")(
            expr = ESExpr.Int(0b01),
            value = TwoFlags(true, false),
          ),
          codecTest("two flags 0b10")(
            expr = ESExpr.Int(0b10),
            value = TwoFlags(false, true),
          ),
          codecTest("two flags 0b11")(
            expr = ESExpr.Int(0b11),
            value = TwoFlags(true, true),
          ),
          codecTest("flag with enum - A")(
            expr = ESExpr.Int(0b000),
            value = FlagWithEnum(false, FlagEnumB.A),
          ),
          codecTest("flag with enum - B")(
            expr = ESExpr.Int(0b011),
            value = FlagWithEnum(true, FlagEnumB.B),
          ),
          codecTest("flag with enum - C")(
            expr = ESExpr.Int(0b100),
            value = FlagWithEnum(false, FlagEnumB.C),
            invalidExprs = Seq(ESExpr.Int(0b110)),
          ),
        )
      },
      
      {
        enum StringOrA[A] derives ESExprCodec, CanEqual {
          @inlineValue
          case StrValue(s: String)

          case A(a: A)
        }

        suite("StringOrA")(
          codecTest[StringOrA[Int]]("str")(
            expr = ESExpr.Str("abc"),
            value = StringOrA.StrValue("abc"),
          ),
          codecTest("A")(
            expr = ESExpr.Constructor("a", Seq(ESExpr.Int(5)), Map()),
            value = StringOrA.A(5),
          ),
        )
      }
    )


  private def codecTest[A](
    name: String,
  )(
    expr: ESExpr,
    value: A,

    tags: Option[ESExprTagSet] = None,
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
