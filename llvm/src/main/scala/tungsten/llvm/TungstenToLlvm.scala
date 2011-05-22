package tungsten.llvm

import java.io.{InputStream, OutputStream, OutputStreamWriter, File}
import tungsten.Converter
import tungsten.ModuleIO
import tungsten.Utilities._

object TungstenToLlvm extends Converter[tungsten.Module, Module] {
  def usageSynopsis = "tungsten-to-llvm file1 file2..."

  def readSource(filename: String, input: InputStream): Option[tungsten.Module] = {
    val module = ModuleIO.readBinary(input)
    val errors = module.validate
    if (errors.isEmpty)
      Some(module)
    else {
      System.err.println(filename + ": validation errors occurred:")
      errors.foreach(System.err.println _)
      setErrorOccurred
      None
    }
  }

  def writeTarget(module: Module, filename: String, output: OutputStream) {
    val writer = new OutputStreamWriter(output)
    val definitions = module.definitions.values
    definitions.filter(_.isInstanceOf[Struct]).foreach { d => writer.write(d + "\n\n") }
    definitions.filter(_.isInstanceOf[Global]).foreach { d => writer.write(d + "\n\n") }
    definitions.filter(_.isInstanceOf[Function]).foreach { d => writer.write(d + "\n\n") }
    writer.flush
  }

  def convert(module: tungsten.Module): Option[Module] = {
    val lowered = tungsten.LowerPass(module)
    val compatible = LlvmCompatibilityPass(lowered)
    val converted = TungstenToLlvmConverter(compatible)
    Some(converted)
  }

  def convertFile(sourceFile: File): File = fileWithExtension(sourceFile, ".wo", ".ll")
}
