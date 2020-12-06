package ingress

import java.io.File
import scala.util.{Failure, Success}

trait CustomerHttpRoutes extends PredefinedFromStringUnmarshallers {

  val customerHttpClient: CustomerHttpClient
  val zeebeHttpClient: ZeebeHttpClient
  val customerService: CustomerService

  val customerHttpRoutes: Route =
    concat(
      getApplicantToken(),
      getCustomerRoute(),
      getCustomersByPhonesRoute(),
      verifyCustomerByEmailWithLinkRoute(),
      verifyCustomerByEmailWithOtpRoute(),
      verifyCustomerBySumsubRoute(),
      updateEmailWithLinkRoute(),
      updateEmailWithOtpRoute(),
      startUpdateEmailRoute(),
      addCustomerDocumentRoute(),
      getApplicantRoute(),
      startDeleteProfileFlow(),
      startBlockProfileFlow(),
      checkEmailRoute()
    )

  @POST
  @Operation(
    summary = "Get a customer information by ID, or phone [need auth token]",
    description = "Returns a full information about a customer",
    method = "POST",
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        content = Array(
          new Content(
            examples = Array(
              new ExampleObject(name = "Singe response", value = """{
  "customerId": "abbd85d0-09f8-43ef-8446-e3fe7e146e62",
  "ttmCustomerId": "someId",
  "sumsubCustomerId": "5efc174c155a631e4647a530",
  "griffonCustomerId": "5efc174c155a631e4647a530",
  "firstName": "Nurzhan",
  "lastName": "Nurzhanov",
  "phoneNumber": "+77777777777",
  "dateOfBirth": "1975-01-02",
  "createdAt": "2020-07-01 04:55:40",
  "isBlocked": false,
  "isEmailVerified": false,
  "isSumsubVerified": true,
  "sumsubData": {
    "id": "5efc174c155a631e4647a530",
    "createdAt": "2020-07-01 04:55:40",
    "key": "BYBULECCLVMBZS",
    "clientId": "ttmm",
    "inspectionId": "5efc174c155a631e4647a531",
    "externalUserId": "nurzhanTestUser",
    "sourceKey": "",
    "info": {
      "firstName": "Nurzhan",
      "firstNameEn": "Nurzhan",
      "middleName": "Nurzhanovich",
      "middleNameEn": "Nurzhanovich",
      "lastName": "Nurzhanov",
      "lastNameEn": "Nurzhanov",
      "dob": "1975-01-02",
      "gender": "M",
      "placeOfBirth": "Kazakhstan",
      "placeOfBirthEn": "Kazakhstan",
      "country": "KAZ",
      "addresses": {
        "street": null,
        "streetEn": null,
        "subStreet": null,
        "subStreetEn": null,
        "town": null,
        "townEn": null,
        "postCode": null,
        "country": null
      },
      "nationality": "KAZ",
      "countryOfBirth": "RUS",
      "phone": "+77777777777",
      "idDocs": [
        {
          "idDocType": null,
          "country": null,
          "firstName": null,
          "firstNameEn": null,
          "middleName": null,
          "middleNameEn": null,
          "lastName": null,
          "lastNameEn": null,
          "issuedDate": null,
          "issueAuthority": null,
          "issueAuthorityCode": null,
          "validUntil": null,
          "firstIssuedDate": null,
          "number": null,
          "additionalNumber": null,
          "dob": null,
          "placeOfBirth": null,
          "mrzLine1": null,
          "mrzLine2": null,
          "mrzLine3": null
        }
      ],
      "legalName": "Nurzh"
    },
    "agreement": {
      "createdAt": null,
      "source": null,
      "targets": null
    },
    "email": "some@fmail.some",
    "env": null,
    "applicantPlatform": "macOS",
    "requiredIdDocs": {
      "includedCountries": [
        "KAZ"
      ],
      "docSets": [
        {
          "idDocSetType": "IDENTITY",
          "types": [
            "ID_CARD"
          ],
          "subTypes": [
            "FRONT_SIDE",
            "BACK_SIDE"
          ],
          "videoRequired": null
        },
        {
          "idDocSetType": "SELFIE",
          "types": [
            "SELFIE"
          ],
          "subTypes": null,
          "videoRequired": "liveness"
        }
      ]
    },
    "review": {
      "elapsedSincePendingMs": null,
      "elapsedSinceQueuedMs": null,
      "reprocessing": null,
      "levelName": null,
      "createDate": "2020-07-01 04:55:40+0000",
      "reviewDate": null,
      "startDate": null,
      "reviewResult": {
        "moderationComment": null,
        "clientComment": null,
        "reviewAnswer": null,
        "rejectLabels": null,
        "reviewRejectType": null
      },
      "reviewStatus": "init",
      "notificationFailureCnt": 0,
      "priority": null,
      "autoChecked": null
    },
    "lang": "ru",
    "type": "individual"
  }
}""")
            ),
            mediaType = "application/json"
          )
        )
      )
    ),
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[GetCustomerDTO]),
          mediaType = "application/json",
          examples = Array(
            new ExampleObject(
              name = "GetCustomerByTtmCustomerId",
              value = "{\"ttmCustomerId\":\"65e7636f-0808-497f-9832-081587f20b63\"}"
            ),new ExampleObject(
              name = "GetCustomerByCustomerId",
              value = "{\"customerId\":\"65e7636f-0808-497f-9832-081587f20b63\"}"
            ),
            new ExampleObject(name = "GetCustomerByPhoneNumber", value = "{\"phoneNumber\":\"+70000000001\"}")
          )
        )
      ),
      required = true
    )
  )
  @Path("/customers")
  @Tag(name = "Customers")
  def getCustomerRoute(): Route =
    path("customers") {
      authenticated() { authUserContext =>

        entity(as[GetCustomerDTO]) { body =>
        post {
              onComplete(customerHttpClient.get(body)) {
                case Success(Right(value)) =>
                  complete(value)
                case Success(Left(errorInfo)) =>
                  complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
                case Failure(ex) => complete(ex)
              }
          }
        }
      }
    }

  @POST
  @Operation(
    summary = "Get a customers by list of phone numbers [need auth token]",
    description = "Returns a list of customers with given phone numbers",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[List[String]]),
          mediaType = "application/json",
          examples = Array(new ExampleObject(name = "Phone numbers", value = """[ "+77777878899", "+70000000001" ]"""))
        )
      ),
      required = true
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        content = Array(
          new Content(
            examples =
              Array(new ExampleObject(name = "Array", value =   """[{
  "customerId": "abbd85d0-09f8-43ef-8446-e3fe7e146e62",
  "ttmCustomerId": "someId",
  "sumsubCustomerId": "5efc174c155a631e4647a530",
  "griffonCustomerId": "5efc174c155a631e4647a530",
  "firstName": "Nurzhan",
  "lastName": "Nurzhanov",
  "phoneNumber": "+77777777777",
  "dateOfBirth": "1975-01-02",
  "createdAt": "2020-07-01 04:55:40",
  "isBlocked": false,
  "isEmailVerified": false,
  "isSumsubVerified": true,
  "sumsubData": {
    "id": "5efc174c155a631e4647a530",
    "createdAt": "2020-07-01 04:55:40",
    "key": "BYBULECCLVMBZS",
    "clientId": "ttmm",
    "inspectionId": "5efc174c155a631e4647a531",
    "externalUserId": "nurzhanTestUser",
    "sourceKey": "",
    "info": {
      "firstName": "Nurzhan",
      "firstNameEn": "Nurzhan",
      "middleName": "Nurzhanovich",
      "middleNameEn": "Nurzhanovich",
      "lastName": "Nurzhanov",
      "lastNameEn": "Nurzhanov",
      "dob": "1975-01-02",
      "gender": "M",
      "placeOfBirth": "Kazakhstan",
      "placeOfBirthEn": "Kazakhstan",
      "country": "KAZ",
      "addresses": {
        "street": null,
        "streetEn": null,
        "subStreet": null,
        "subStreetEn": null,
        "town": null,
        "townEn": null,
        "postCode": null,
        "country": null
      },
      "nationality": "KAZ",
      "countryOfBirth": "RUS",
      "phone": "+77777777777",
      "idDocs": [
        {
          "idDocType": null,
          "country": null,
          "firstName": null,
          "firstNameEn": null,
          "middleName": null,
          "middleNameEn": null,
          "lastName": null,
          "lastNameEn": null,
          "issuedDate": null,
          "issueAuthority": null,
          "issueAuthorityCode": null,
          "validUntil": null,
          "firstIssuedDate": null,
          "number": null,
          "additionalNumber": null,
          "dob": null,
          "placeOfBirth": null,
          "mrzLine1": null,
          "mrzLine2": null,
          "mrzLine3": null
        }
      ],
      "legalName": "Nurzh"
    },
    "agreement": {
      "createdAt": null,
      "source": null,
      "targets": null
    },
    "email": "some@fmail.some",
    "env": null,
    "applicantPlatform": "macOS",
    "requiredIdDocs": {
      "includedCountries": [
        "KAZ"
      ],
      "docSets": [
        {
          "idDocSetType": "IDENTITY",
          "types": [
            "ID_CARD"
          ],
          "subTypes": [
            "FRONT_SIDE",
            "BACK_SIDE"
          ],
          "videoRequired": null
        },
        {
          "idDocSetType": "SELFIE",
          "types": [
            "SELFIE"
          ],
          "subTypes": null,
          "videoRequired": "liveness"
        }
      ]
    },
    "review": {
      "elapsedSincePendingMs": null,
      "elapsedSinceQueuedMs": null,
      "reprocessing": null,
      "levelName": null,
      "createDate": "2020-07-01 04:55:40+0000",
      "reviewDate": null,
      "startDate": null,
      "reviewResult": {
        "moderationComment": null,
        "clientComment": null,
        "reviewAnswer": null,
        "rejectLabels": null,
        "reviewRejectType": null
      },
      "reviewStatus": "init",
      "notificationFailureCnt": 0,
      "priority": null,
      "autoChecked": null
    },
    "lang": "ru",
    "type": "individual"
  }
}]""")),
            mediaType = "application/json"
          )
        )
      )
    )
  )
  @Path("/customers/phoneNumber")
  @Tag(name = "Customers")
  def getCustomersByPhonesRoute(): Route = path("customers" / "phoneNumber") {
    authenticated() { authUserContext =>

      entity(as[List[String]]) { phones =>
        post {
          onComplete(customerHttpClient.getByPhoneNumbers(phones)) {
            case Success(Right(value)) =>
              complete(value)
            case Success(Left(errorInfo)) =>
              complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
            case Failure(ex) => complete(ex)
          }
        }
      }
    }
  }

  @GET
  @Operation(
    summary = "Verify customer email",
    description = "Verify customer email by ttmCustomerId with link",
    method = "GET",
    parameters = Array(
      new Parameter(name = "ttmCustomerId", in = ParameterIn.QUERY, example = "ttmCustomerId", required = true),
      new Parameter(name = "email", in = ParameterIn.QUERY, example = "email@mail.com", required = true),
      new Parameter(name = "hash", in = ParameterIn.QUERY, example = "hash", required = true)
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "Email was verified",
        content =
          Array(
            new Content(
              examples =
                Array(new ExampleObject(value = "Email successfully verified for ttmCustomerId: $ttmCustomerId")),
              mediaType = "application/json"
            )
          )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/customers/verify/email/link")
  @Tag(name = "Customers")
  def verifyCustomerByEmailWithLinkRoute(): Route =
    path("customers" / "verify" / "email" / "link") {
      parameters(("ttmCustomerId", "email", "hash")) { (ttmCustomerId, email, hash) =>

        get {
          onComplete(customerHttpClient.verifyCustomerByEmailWithLink(ttmCustomerId, email, hash)) {
            case Success(Right(value)) =>
              complete(value)
            case Success(Left(errorInfo)) =>
              complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
            case Failure(ex) => complete(ex)
          }
        }
      }
    }

  @POST
  @Operation(
    summary = "Verify customer email",
    description = "Verify customer email by ttmCustomerId with OTP",
    method = "POST",
    parameters = Array(
      new Parameter(name = "ttmCustomerId", in = ParameterIn.QUERY, example = "ttmCustomerId", required = true),
      new Parameter(name = "email", in = ParameterIn.QUERY, example = "email@mail.com", required = true),
      new Parameter(name = "otp", in = ParameterIn.QUERY, example = "12343", required = true)
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "Email was verified",
        content =
          Array(
            new Content(
              examples =
                Array(new ExampleObject(value = "Email successfully verified for ttmCustomerId: $ttmCustomerId")),
              mediaType = "application/json"
            )
          )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/customers/verify/email/otp")
  @Tag(name = "Customers")
  def verifyCustomerByEmailWithOtpRoute(): Route =
    path("customers" / "verify" / "email" / "otp") {
      parameters(("ttmCustomerId", "email", "otp")) { (ttmCustomerId, email, otp) =>

        post {
          onComplete(customerHttpClient.verifyCustomerByEmailWithOtp(ttmCustomerId, email, otp)) {
            case Success(Right(value)) =>
              complete(value)
            case Success(Left(errorInfo)) =>
              complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
            case Failure(ex) => complete(ex)
          }
        }
      }
    }

  @POST
  @Operation(
    summary = "Verify customer by sumsub",
    description = "Verify customer by sumsub",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[SumsubBody]),
          mediaType = "application/json",
          examples = Array(new ExampleObject(name = "SumsubBody", value = "{\"externalUserId\":\"someId\"}"))
        )
      ),
      required = true
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "Email was verified",
        content = Array(
          new Content(
            examples =
              Array(new ExampleObject(value = "Sumsub successfully verified for ttmCustomerId: $ttmCustomerId")),
            mediaType = "application/json"
          )
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/customers/verify/sumsub")
  @Tag(name = "Customers")
  def verifyCustomerBySumsubRoute(): Route =
    path("customers" / "verify" / "sumsub") {
      entity(as[SumsubBody]) { sumsubBody =>
        post {
          onComplete(customerHttpClient.verifyCustomerBySumsub(sumsubBody.externalUserId)) {
            case Success(Right(value)) =>
              complete(value)
            case Success(Left(errorInfo)) =>
              complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
            case Failure(ex) => complete(ex)
          }
        }
      }
    }

//  @POST
//  @Operation(
//    summary = "Add document",
//    description = "Add document to sumsub",
//    method = "POST",
//    parameters = Array(
//      new Parameter(name = "customerId", in = ParameterIn.PATH, example = "customerId", required = true),
//      new Parameter(name = "docType", in = ParameterIn.PATH, example = "docType", required = true),
//      new Parameter(name = "docValue", in = ParameterIn.PATH, example = "docValue", required = true)
//    )
//  )
//  @Path("/customers/{customerId}/sumsub?docType={docType}&docValue={docValue}")
//  @Tag(name = "SumSub")
  def addCustomerDocumentRoute(): Route = path("customers" / Segment / "sumsub") { customerId: String =>
  authenticated() { authUserContext =>

    post {
      parameters(("docType", "docValue")) { (docType, docValue) =>

        storeUploadedFile("file", tempDestination) {
          case (_, file) =>
            onComplete(customerHttpClient.addDocument(customerId, docType, docValue, file)) {
              case Success(Right(value)) =>
                file.delete()
                complete(value)
              case Success(Left(errorInfo)) =>
                file.delete()
                complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
              case Failure(ex) =>
                file.delete()
                complete(ex)
            }
        }
      }
    }
  }
  }

//  @POST
//  @Operation(
//    summary = "Get applicant",
//    description = "Get applicant's data from sumsub",
//    method = "GET",
//    parameters = Array(
//      new Parameter(name = "customerId", in = ParameterIn.PATH, example = "customerId", required = true)
//    )
//  )
//  @Path("/customers/{customerId}/sumsub")
//  @Tag(name = "SumSub")
  def getApplicantRoute(): Route = path("customers" / Segment / "sumsub") { customerId: String =>
  authenticated() { authUserContext =>

    get {
      onComplete(customerHttpClient.getApplicant(customerId)) {
        case Success(Right(value)) =>
          complete(value)
        case Success(Left(errorInfo)) =>
          complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
        case Failure(ex) =>
          complete(ex)
      }
    }
  }
  }

  @DELETE
  @Operation(
    summary = "start delete profile zeebe flow [need auth token]",
    description = "start delete profile zeebe flow",
    method = "DELETE"
  )
  @Path("customers/deleteProfile")
  @Tag(name = "Customers")
  def startDeleteProfileFlow(): Route =
    path("customers" / "deleteProfile") {
      authenticated() { authUserContext =>
        checkFullVerificationAndAccess(authUserContext, customerHttpClient, onlyCustomer = true) { customerDTO =>
          headerValueByName("Authorization") { jwt =>

            delete {
               onComplete(customerService.deleteProfileFlow(customerDTO, jwt.split(" ").lift(1).get)) {
                case Success(Right(value)) =>
                  complete(value)
                case Success(Left(errorInfo)) =>
                  complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
                case Failure(ex) =>
                  complete(ex)
              }
            }
          }
        }
      }
    }

  @POST
  @Operation(
    summary = "start block profile zeebe flow [need auth token]",
    description = "start block profile zeebe flow",
    method = "POST",
    requestBody = new RequestBody(
      content = Array(
        new Content(
          schema = new Schema(implementation = classOf[StartBlockProfileFlow]),
          mediaType = "application/json",
          examples = Array(
            new ExampleObject(
              name = "startBlockProfileFlowBody",
              value = "{\"customerId\": \"527092cf-4960-4a01-9476-1ec091ef2755\",\"isBlocked\": true}",
              description = "set isBlocked true to block user, false to unblock"
            )
          )
        )
      ),
      required = true
    )
  )
  @Path("customers/blockProfile")
  @Tag(name = "Customers")
  def startBlockProfileFlow(): Route =
    path("customers" / "blockProfile") {
      authenticated() { authUserContext =>
        post {
          entity(as[StartBlockProfileFlow]) { body =>
            onComplete(zeebeHttpClient.startZeebeWorkflow(body)) {
              case Success(Right(value)) =>
                complete(value)
              case Success(Left(errorInfo)) =>
                complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
              case Failure(ex) =>
                complete(ex)
            }
          }
        }
      }
    }

  @Operation(
    summary = "Send a notification for email update [need auth token]",
    description = "Send a notification for email update",
    method = "POST",
    parameters = Array(
      new Parameter(name = "customerId", in = ParameterIn.PATH, example = "customerId", required = true),
      new Parameter(name = "newEmail", in = ParameterIn.QUERY, example = "newEmail", required = true),
      new Parameter(name = "oldEmail", in = ParameterIn.QUERY, example = "oldEmail", required = true),
      new Parameter(name = "notificationType", in = ParameterIn.QUERY, example = "notificationType", required = true)
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "Notification was sent",
        content = Array(
          new Content(examples = Array(new ExampleObject(value = "Correct email")), mediaType = "application/json")
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/customers/{customerId}/email/update/start")
  @Tag(name = "Customers")
  def startUpdateEmailRoute(): Route =
    path("customers" / Segment / "email" / "update" / "start") { customerId: String =>
      parameters(("newEmail", "oldEmail", "notificationType")) { (newEmail, oldEmail, notificationType) =>
        authenticated() { authUserContext =>

          post {
            onComplete(customerHttpClient.startUpdateEmail(customerId, newEmail, oldEmail, notificationType)) {
              case Success(Right(value)) =>
                complete(value)
              case Success(Left(errorInfo)) =>
                complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
              case Failure(ex) =>
                complete(ex)
            }
          }
        }
      }
    }

  @POST
  @Operation(
    summary = "Check customer's email",
    description = "Check customer's email",
    method = "POST",
    parameters = Array(
      new Parameter(name = "customerId", in = ParameterIn.PATH, example = "customerId", required = true)
    )
  )
  @Path("/customers/{customerId}/email/check")
  @Tag(name = "Customers")
  def checkEmailRoute(): Route =
    path("customers" / Segment / "email" / "check") { customerId: String =>
      parameters(("newEmail", "oldEmail")) { (newEmail, oldEmail) =>

        post {
          onComplete(customerHttpClient.checkEmail(customerId, newEmail, oldEmail)) {
            case Success(Right(value)) =>
              complete(value)
            case Success(Left(errorInfo)) =>
              complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
            case Failure(ex) =>
              complete(ex)
          }
        }
      }
    }

  @GET
  @Operation(
    summary = "Update customer's email via link",
    description = "Update customer's email via link",
    method = "GET",
    parameters = Array(
      new Parameter(name = "customerId", in = ParameterIn.PATH, example = "customerId", required = true),
      new Parameter(name = "newEmail", in = ParameterIn.QUERY, example = "newEmail", required = true),
      new Parameter(name = "oldEmail", in = ParameterIn.QUERY, example = "oldEmail", required = true),
      new Parameter(name = "hash", in = ParameterIn.QUERY, example = "hash", required = true)
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "Email was updated",
        content = Array(
          new Content(examples = Array(new ExampleObject(value = "Email updated")), mediaType = "application/json")
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/customers/{customerId}/email/update/link")
  @Tag(name = "Customers")
  def updateEmailWithLinkRoute(): Route =
    path("customers" / Segment / "email" / "update" / "link") { customerId: String =>
      parameters(("newEmail", "oldEmail", "hash")) { (newEmail, oldEmail, hash) =>

        get {
          onComplete(customerHttpClient.updateEmailWithLink(customerId, newEmail, oldEmail, hash)) {
            case Success(Right(value)) =>
              complete(value)
            case Success(Left(errorInfo)) =>
              complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
            case Failure(ex) =>
              complete(ex)
          }
        }
      }
    }

  @POST
  @Operation(
    summary = "Update customer's email via otp",
    description = "Update customer's email via otp",
    method = "POST",
    parameters = Array(
      new Parameter(name = "customerId", in = ParameterIn.PATH, example = "customerId", required = true),
      new Parameter(name = "newEmail", in = ParameterIn.QUERY, example = "newEmail", required = true),
      new Parameter(name = "oldEmail", in = ParameterIn.QUERY, example = "oldEmail", required = true),
      new Parameter(name = "otp", in = ParameterIn.QUERY, example = "otp", required = true)
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "Email was updated",
        content = Array(
          new Content(examples = Array(new ExampleObject(value = "Email updated")), mediaType = "application/json")
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/customers/{customerId}/email/update/otp")
  @Tag(name = "Customers")
  def updateEmailWithOtpRoute(): Route =
    path("customers" / Segment / "email" / "update" / "otp") { customerId: String =>
      parameters(("newEmail", "oldEmail", "otp")) { (newEmail, oldEmail, otp) =>

        post {
          onComplete(customerHttpClient.updateEmailWithOtp(customerId, newEmail, oldEmail, otp)) {
            case Success(Right(value)) =>
              complete(value)
            case Success(Left(errorInfo)) =>
              complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
            case Failure(ex) =>
              complete(ex)
          }
        }
      }
    }

  @GET
  @Operation(
    summary = "Get sumsub applicant's token and flow name",
    description = "Get sumsub applicant's token and flow name",
    method = "GET",
    parameters = Array(
      new Parameter(name = "ttmCustomerId", in = ParameterIn.PATH, example = "nurzhanTestUser", required = true),
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        content = Array(
          new Content(examples = Array(new ExampleObject(value = "{\n\"token\":\"_act-0f7c4f20-e802-487c-bffa-f19a030809a6\",\n\"userId\":\"nurzhanTestUser\",\n\"flowName:\"ttmm-kyc-flow\"\n}")), mediaType = "application/json")
        )
      ),
      new ApiResponse(responseCode = "500", description = "Internal server error")
    )
  )
  @Path("/customers/sumsub/token/{ttmCustomerId}")
  @Tag(name = "Customers")
  def getApplicantToken(): Route = {
    path("customers" / "sumsub" / "token" / Segment){ ttmCustomerId =>

      get {
        onComplete(customerHttpClient.getApplicantToken(ttmCustomerId)) {
          case Success(Right(value)) =>
            complete(value)
          case Success(Left(errorInfo)) =>
            complete(StatusCode.int2StatusCode(errorInfo.status.getOrElse(500)) -> errorInfo)
          case Failure(ex) =>
            complete(ex)
        }
      }
    }
  }

  def tempDestination(fileInfo: FileInfo): File =
    File.createTempFile(java.util.UUID.randomUUID.toString, "tmp")
}
