import software.amazon.smithy.model.Model

object DemoApp extends App {
  assert(
    smithy4bazinga.BazingaTrait.ID.toString() == "smithy4bazinga#bazinga",
    s"Expected smithy4bazinga.BazingaTrait.ID to be 'smithy4bazinga#bazinga', but got ${smithy4bazinga.BazingaTrait.ID.toString()}",
  )
}
