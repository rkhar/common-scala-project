package ingress

trait FaqHttpRoutes extends HttpHandler with PredefinedFromStringUnmarshallers {

  val faqService: FaqService

  @POST
  @Operation(
    summary = "Create a topic",
    description = "Create a new topic",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[CreateTopicRequest]),
          mediaType = "application/json",
          examples = Array(new ExampleObject(name = "createTopicRequest", value = "{\"priority\":\"1\",\"topicName\":\"Help\""))
        )
      ),
      required = true
    )
  )
  @Path("/faq/topics")
  @Tag(name = "FAQ")
  def createTopicRoute: Route =
    path("faq" / "topics") {
      entity(as[CreateTopicRequest]) { cmd =>
        post { ctx =>
          complete {
            faqService.insertTopic(Topic(priority = cmd.priority, topicName = cmd.topicName))
          }(ctx)
        }
      }
    }

  @GET
  @Operation(
    summary = "Get a topic information by ID",
    description = "Returns a full information about topic",
    method = "GET",
    parameters = Array(
      new Parameter(name = "topicId", in = ParameterIn.PATH, example = "65e7636f-0808-497f-9832-081587f20b63", required = true)
    )
  )
  @Path("/faq/topics/{topicId}")
  @Tag(name = "FAQ")
  def getTopicRoute: Route =
    path("faq" / "topics" / Segment) { topicId =>
      get { ctx =>
        complete {
          faqService.findTopic(topicId)
        }(ctx)
      }
    }

  @GET
  @Operation(summary = "Get topics information", description = "Returns full information about all topics", method = "GET")
  @Path("/faq/topics")
  @Tag(name = "FAQ")
  def getTopicsRoute: Route =
    path("faq" / "topics") {
      get { ctx =>
        complete {
          faqService.findAllTopics()
        }(ctx)
      }
    }

  @DELETE
  @Operation(
    summary = "Delete a topic information by ID",
    description = "Delete full information about topic",
    method = "DELETE",
    parameters = Array(
      new Parameter(name = "topicId", in = ParameterIn.PATH, example = "65e7636f-0808-497f-9832-081587f20b63", required = true)
    )
  )
  @Path("/faq/topics/{topicId}")
  @Tag(name = "FAQ")
  def deleteTopicRoute: Route =
    path("faq" / "topics" / Segment) { topicId =>
      delete { ctx =>
        complete {
          faqService.removeTopic(topicId)
        }(ctx)
      }
    }

  @POST
  @Operation(
    summary = "Create a question",
    description = "Create a new question",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[CreateQuestionRequest]),
          mediaType = "application/json",
          examples = Array(
            new ExampleObject(
              name = "createQuestionRequest",
              value = "{\"topicId\":\"asd\",\"questionDescription\":\"Help\",\"answerDescription\":\"pleH\""
            )
          )
        )
      ),
      required = true
    )
  )
  def createQuestionRoute: Route =
    path("faq" / "questions") {
      entity(as[CreateQuestionRequest]) { cmd =>
        post { ctx =>
          complete {
            faqService.insertQuestion(
              Question(
                topicId = cmd.topicId,
                questionDescription = cmd.questionDescription,
                answerDescription = cmd.answerDescription
              )
            )
          }(ctx)
        }
      }
    }

  @GET
  @Operation(
    summary = "Get a question information by ID",
    description = "Returns a full information about question",
    method = "GET",
    parameters = Array(
      new Parameter(name = "questionId", in = ParameterIn.PATH, example = "65e7636f-0808-497f-9832-081587f20b63", required = true)
    )
  )
  @Path("/faq/questions/{questionId}")
  @Tag(name = "FAQ")
  def getQuestionRoute: Route =
    path("faq" / "questions" / Segment) { questionId: String =>
      get { ctx =>
        complete {
          faqService.findQuestion(questionId)
        }(ctx)
      }
    }

  @GET
  @Operation(summary = "Get questions information", description = "Returns full information about all questions", method = "GET")
  @Path("/faq/questions")
  @Tag(name = "FAQ")
  def getQuestionsRoute: Route =
    path("faq" / "questions") {
      get { ctx =>
        complete {
          faqService.findAllQuestions()
        }(ctx)
      }
    }

  @GET
  @Operation(
    summary = "Get questions by priority",
    description = "Returns full information about all questions by priority",
    method = "GET"
  )
  @Path("/faq/questions/priority")
  @Tag(name = "FAQ")
  def getQuestionsByPriorityRoute: Route =
    path("faq" / "questions" / "priority") {
      get { ctx =>
        complete {
          faqService.findAllQuestionsByPriority()
        }(ctx)
      }
    }

//  @GET
//  @Operation(
//    summary = "Get topics and questions",
//    description = "Returns full information about all topics and questions",
//    method = "GET"
//  )
//  @Path("/faq/topics/questions")
//  @Tag(name = "FAQ")
//  def getTopicsAndQuestionsByPriorityRoute: Route =
//    path("faq" / "topics" / "questions") {
//      get { ctx =>
//        complete {
//          faqService.getTopicsAndQuestions()
//        }(ctx)
//      }
//    }

  @DELETE
  @Operation(
    summary = "Delete a question information by ID",
    description = "Delete full information about question",
    method = "DELETE",
    parameters = Array(
      new Parameter(name = "questionId", in = ParameterIn.PATH, example = "65e7636f-0808-497f-9832-081587f20b63", required = true)
    )
  )
  @Path("/faq/questions/{questionId}")
  @Tag(name = "FAQ")
  def deleteQuestionRoute: Route =
    path("faq" / "questions" / Segment) { questionId: String =>
      delete { ctx =>
        complete {
          faqService.removeQuestion(questionId)
        }(ctx)
      }
    }

  val faqHttpRoutes: Route =
    concat(
//      getTopicsAndQuestionsByPriorityRoute,
      createTopicRoute,
      getTopicRoute,
      getTopicsRoute,
      deleteTopicRoute,
      createQuestionRoute,
      getQuestionRoute,
      getQuestionsRoute,
      deleteQuestionRoute
    )
}
