import software.amazon.smithy.model.Model

object DemoApp extends App {
  val trt = demo.MyTrait.builder().name("hello").build()
  assert(
    trt.getName() == "hello",
    s"Expected name to be hello but it was: ${trt.getName()}",
  )

  val model = Model
    .assembler()
    .addUnparsedModel(
      "test.smithy",
      """$version: "2"
        |namespace test
        |
        |@demo#myTrait(name: "hello")
        |structure Test {}
        |""".stripMargin,
    )
    .discoverModels()
    .assemble()
    .unwrap()

  val traitInModel = model
    .getShapesWithTrait(classOf[demo.MyTrait])
    .iterator()
    .next()
    .expectTrait(classOf[demo.MyTrait])

  assert(
    traitInModel == trt,
    s"Expected trait in model to be equal to the one created, but they were not: $traitInModel != $trt",
  )
}
