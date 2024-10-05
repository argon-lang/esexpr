package esexpr.unsigned

import zio.*
import zio.stream.*
import zio.test.Assertion.*
import zio.test.{TestExecutor as _, *}
import scala.quoted.*

object UnsignedTests extends ZIOSpecDefault {

  private abstract class OperatorChecks[T](using CanEqual[T, T]) {
    protected val minValue: T
    protected val maxValue: T
    protected def toBigInt(i: T): BigInt
    protected def fromBigInt(i: BigInt): T

    private def tBigIntGen: Gen[Any, BigInt] =
      Gen.bigInt(toBigInt(minValue), toBigInt(maxValue))

    def checkBinOp(f1: (BigInt, BigInt) => BigInt)(f2: (T, T) => T): UIO[TestResult] =
      check(tBigIntGen, tBigIntGen) { (a, b) =>
        assertTrue(fromBigInt(f1(a, b)) == f2(fromBigInt(a), fromBigInt(b)))
      }

    def checkDiv(f2: (T, T) => T): UIO[TestResult] =
      check(tBigIntGen, tBigIntGen.filter(_ != 0)) { (a, b) =>
        assertTrue(a / b == toBigInt(f2(fromBigInt(a), fromBigInt(b))))
      }

    def checkCmpOp[Res](f1: (BigInt, BigInt) => Res)(f2: (T, T) => Res)(using CanEqual[Res, Res]): UIO[TestResult] =
      check(tBigIntGen, tBigIntGen) { (a, b) =>
        assertTrue(f1(a, b) == f2(fromBigInt(a), fromBigInt(b)))
      }

    def checkConvert[U](f1: BigInt => U)(f2: T => U)(using CanEqual[U, U]): UIO[TestResult] =
      check(tBigIntGen) { a =>
        assertTrue(f1(a) == f2(fromBigInt(a)))
      }

  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Unsigned Tests")(
      {
        object UByteOps extends OperatorChecks[UByte] {
          protected override val minValue: UByte = UByte.MinValue
          protected override val maxValue: UByte = UByte.MaxValue

          protected override def toBigInt(i: UByte): BigInt = i.toBigInt
          protected override def fromBigInt(i: BigInt): UByte = i.toUByte
        }
        import UByteOps.*

        suite("UByte")(
          test("+") {
            checkBinOp(_ + _)(_ + _)
          },
          test("-") {
            checkBinOp(_ - _)(_ - _)
          },
          test("*") {
            checkBinOp(_ * _)(_ * _)
          },
          test("/") {
            checkBinOp(_ + _)(_ + _)
          },
          test("compare") {
            checkCmpOp((a, b) => a.compare(b).sign)((a, b) => a.compare(b).sign)
          },
          test("<") {
            checkCmpOp(_ < _)(_ < _)
          },
          test("<=") {
            checkCmpOp(_ <= _)(_ <= _)
          },
          test(">") {
            checkCmpOp(_ > _)(_ > _)
          },
          test(">=") {
            checkCmpOp(_ >= _)(_ >= _)
          },
          test("toByte") {
            checkConvert(_.toByte)(_.toByte)
          },
          test("toShort") {
            checkConvert(_.toShort)(_.toShort)
          },
          test("toUShort") {
            checkConvert(_.toUShort)(_.toUShort)
          },
          test("toInt") {
            checkConvert(_.toInt)(_.toInt)
          },
          test("toUInt") {
            checkConvert(_.toUInt)(_.toUInt)
          },
          test("toLong") {
            checkConvert(_.toLong)(_.toLong)
          },
          test("toULong") {
            checkConvert(_.toULong)(_.toULong)
          },
        )
      },
      {
        object UShortOps extends OperatorChecks[UShort] {
          protected override val minValue: UShort = UShort.MinValue
          protected override val maxValue: UShort = UShort.MaxValue

          protected override def toBigInt(i: UShort): BigInt = i.toBigInt
          protected override def fromBigInt(i: BigInt): UShort = i.toUShort
        }
        import UShortOps.*

        suite("UShort")(
          test("+") {
            checkBinOp(_ + _)(_ + _)
          },
          test("-") {
            checkBinOp(_ - _)(_ - _)
          },
          test("*") {
            checkBinOp(_ * _)(_ * _)
          },
          test("/") {
            checkBinOp(_ + _)(_ + _)
          },
          test("compare") {
            checkCmpOp((a, b) => a.compare(b).sign)((a, b) => a.compare(b).sign)
          },
          test("<") {
            checkCmpOp(_ < _)(_ < _)
          },
          test("<=") {
            checkCmpOp(_ <= _)(_ <= _)
          },
          test(">") {
            checkCmpOp(_ > _)(_ > _)
          },
          test(">=") {
            checkCmpOp(_ >= _)(_ >= _)
          },
          test("toByte") {
            checkConvert(_.toByte)(_.toByte)
          },
          test("toUByte") {
            checkConvert(_.toUByte)(_.toUByte)
          },
          test("toShort") {
            checkConvert(_.toShort)(_.toShort)
          },
          test("toInt") {
            checkConvert(_.toInt)(_.toInt)
          },
          test("toUInt") {
            checkConvert(_.toUInt)(_.toUInt)
          },
          test("toLong") {
            checkConvert(_.toLong)(_.toLong)
          },
          test("toULong") {
            checkConvert(_.toULong)(_.toULong)
          },
        )
      },
      {
        object UIntOps extends OperatorChecks[UInt] {
          protected override val minValue: UInt = UInt.MinValue
          protected override val maxValue: UInt = UInt.MaxValue

          protected override def toBigInt(i: UInt): BigInt = i.toBigInt
          protected override def fromBigInt(i: BigInt): UInt = i.toUInt
        }
        import UIntOps.*

        suite("UInt")(
          test("+") {
            checkBinOp(_ + _)(_ + _)
          },
          test("-") {
            checkBinOp(_ - _)(_ - _)
          },
          test("*") {
            checkBinOp(_ * _)(_ * _)
          },
          test("/") {
            checkBinOp(_ + _)(_ + _)
          },
          test("compare") {
            checkCmpOp((a, b) => a.compare(b).sign)((a, b) => a.compare(b).sign)
          },
          test("<") {
            checkCmpOp(_ < _)(_ < _)
          },
          test("<=") {
            checkCmpOp(_ <= _)(_ <= _)
          },
          test(">") {
            checkCmpOp(_ > _)(_ > _)
          },
          test(">=") {
            checkCmpOp(_ >= _)(_ >= _)
          },
          test("toByte") {
            checkConvert(_.toByte)(_.toByte)
          },
          test("toUByte") {
            checkConvert(_.toUByte)(_.toUByte)
          },
          test("toShort") {
            checkConvert(_.toShort)(_.toShort)
          },
          test("toUShort") {
            checkConvert(_.toUShort)(_.toUShort)
          },
          test("toInt") {
            checkConvert(_.toInt)(_.toInt)
          },
          test("toLong") {
            checkConvert(_.toLong)(_.toLong)
          },
          test("toULong") {
            checkConvert(_.toULong)(_.toULong)
          },
        )
      },
      {
        object ULongOps extends OperatorChecks[ULong] {
          protected override val minValue: ULong = ULong.MinValue
          protected override val maxValue: ULong = ULong.MaxValue

          protected override def toBigInt(i: ULong): BigInt = i.toBigInt
          protected override def fromBigInt(i: BigInt): ULong = i.toULong
        }
        import ULongOps.*

        suite("ULong")(
          test("+") {
            checkBinOp(_ + _)(_ + _)
          },
          test("-") {
            checkBinOp(_ - _)(_ - _)
          },
          test("*") {
            checkBinOp(_ * _)(_ * _)
          },
          test("/") {
            checkBinOp(_ + _)(_ + _)
          },
          test("compare") {
            checkCmpOp((a, b) => a.compare(b).sign)((a, b) => a.compare(b).sign)
          },
          test("<") {
            checkCmpOp(_ < _)(_ < _)
          },
          test("<=") {
            checkCmpOp(_ <= _)(_ <= _)
          },
          test(">") {
            checkCmpOp(_ > _)(_ > _)
          },
          test(">=") {
            checkCmpOp(_ >= _)(_ >= _)
          },
          test("toByte") {
            checkConvert(_.toByte)(_.toByte)
          },
          test("toUByte") {
            checkConvert(_.toUByte)(_.toUByte)
          },
          test("toShort") {
            checkConvert(_.toShort)(_.toShort)
          },
          test("toUShort") {
            checkConvert(_.toUShort)(_.toUShort)
          },
          test("toInt") {
            checkConvert(_.toInt)(_.toInt)
          },
          test("toUInt") {
            checkConvert(_.toUInt)(_.toUInt)
          },
          test("toLong") {
            checkConvert(_.toLong)(_.toLong)
          },
        )
      },
    )



}
