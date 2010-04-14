package tungsten

sealed abstract class Token {
  var location: Location = Nowhere
}

final case class ErrorToken(msg: String) extends Token {
  override def equals(that: Any) = that.isInstanceOf[ErrorToken]
  override val hashCode = "ErrorToken".hashCode
}
final case class ReservedToken(text: String) extends Token
final case class SymbolToken(symbol: Symbol) extends Token
final case class LocationToken(loc: Location) extends Token
final case class VersionToken(version: Version) extends Token
final case class ModuleDependencyToken(dependency: ModuleDependency) extends Token

sealed abstract class IntegerToken extends Token
final case class ByteToken(value: Byte) extends IntegerToken
final case class ShortToken(value: Short) extends IntegerToken
final case class IntToken(value: Int) extends IntegerToken
final case class LongToken(value: Long) extends IntegerToken

sealed abstract class FloatToken extends Token
final case class Float32Token(value: Float) extends FloatToken
final case class Float64Token(value: Double) extends FloatToken

final case class StringToken(value: String) extends Token