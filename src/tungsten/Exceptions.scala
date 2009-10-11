package tungsten

sealed abstract class CompileException(message: String, location: Location) extends Exception {
  override def toString = {
    location + ": error: " + message
  }
}

final case class EmptyStructException(symbol: Symbol, location: Location)
  extends CompileException("struct " + symbol.toString + " must contain at least one field",
                           location)

final case class InappropriateSymbolException(symbol: Symbol,
                                              location: Location,
                                              defnLocation: Location,
                                              expected: String)
  extends CompileException(symbol.toString + " defined at " + defnLocation + 
                             " does not refer to a(n) " + expected,
                           location)

final case class RedefinedSymbolException(symbol: Symbol, 
                                          location: Location, 
                                          oldLocation: Location)
  extends CompileException(symbol.toString + " was redefined; original definition was at " + 
                             oldLocation,
                           location)
                                          
final case class UndefinedSymbolException(symbol: Symbol, location: Location)
  extends CompileException(symbol.toString + " is not defined", location)
