package tungsten.interpreter

import tungsten.{Parameter, Instruction, Function}

abstract class Value

final case object UnitValue extends Value

final case class BooleanValue(value: Boolean) extends Value

final case class Int8Value(value: Byte) extends Value

final case class Int16Value(value: Short) extends Value

final case class Int32Value(value: Int) extends Value

final case class Int64Value(value: Long) extends Value

final case class Float32Value(value: Float) extends Value

final case class Float64Value(value: Double) extends Value

final case object NullValue extends Value

final case class ScalarReferenceValue(var value: Value) extends Value

final case class ArrayReferenceValue(var array: ArrayValue, var index: Int64Value)
  extends Value

final case class ArrayValue(value: Array[Value]) extends Value

final case class FunctionValue(value: Function) extends Value

object Value {
  def eval(value: tungsten.Value, env: Environment): Value = {
    value match {
      case tungsten.UnitValue(_) => UnitValue
      case tungsten.BooleanValue(v, _) => BooleanValue(v)
      case tungsten.Int8Value(v, _) => Int8Value(v)
      case tungsten.Int16Value(v, _) => Int16Value(v)
      case tungsten.Int32Value(v, _) => Int32Value(v)
      case tungsten.Int64Value(v, _) => Int64Value(v)
      case tungsten.Float32Value(v, _) => Float32Value(v)
      case tungsten.Float64Value(v, _) => Float64Value(v)
      case tungsten.NullValue(_) => NullValue
      case tungsten.ArrayValue(_, vs, _) => {
        val values = vs.map(eval(_, env)).toArray
        ArrayValue(values)
      }
      case tungsten.DefinedValue(v, _) => env.module.getDefn(v) match {
        case Some(p: Parameter) => env.state.values(v)
        case Some(i: Instruction) => env.state.values(v)
        case Some(f: Function) => FunctionValue(f)
        case _ => throw new UnsupportedOperationException
        // TODO: other values
      }        
    }
  }
}
