package leon
package synthesis

object Main {
  def main(args : Array[String]) {
    new Synthesizer(new DefaultReporter).test()
  }

}