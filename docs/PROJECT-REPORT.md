# Pluggable Workflow Engine Support for Mifos X

**Mifos Summer of Code 2026**

| | |
|---|---|
| Contributor | Albin Sunny ([@001Albin](https://github.com/001Albin)) |
| Mentor | Aleksandar Vidakovic ([@vidakovic](https://github.com/vidakovic)) |
| Upstream repository | [openMF/mifos-workflow](https://github.com/openMF/mifos-workflow) |
| Working branch | [001Albin/mifos-workflow @ feature/flowable-usecases](https://github.com/001Albin/mifos-workflow/tree/feature/flowable-usecases) |
| Jira epic | MX-326 |

---

## 1. Project objective

From the project description:

> Create an extension mechanism that allows to integrate workflow engines. The main goal is to avoid leaking technology details of the specific workflow engine into Apache (R) Fineract. It should be possible to abstract the interactions with the workflow engines with a relatively simple Java interface.

A large amount of process logic in Apache Fineract is hard-coded as `if`/`else` branches inside business services. Every deploying institution gets the same sequence of steps, and changing it means changing Fineract itself.

The goal of this project is an abstraction layer that lets an institution choose its own workflow engine and define its own processes, without the choice of engine leaking into the banking code, and without the banking code being rewritten for each engine.

Because upstream acceptance could not be guaranteed, the work is contributed to `openMF/mifos-workflow`, a Mifos-controlled repository, rather than to Fineract core.

---

## 2. Starting point and change of direction

### 2.1 The original approach

The project began as a Maven multi-module SPI:

```
mifos-workflow-core/               WorkflowService — one interface, ~20 methods
mifos-workflow-adapter-flowable/   Flowable implementation
mifos-workflow-adapter-conductor/  Conductor placeholder
mifos-workflow-app/                Spring Boot application
```

Engine selection was to be made by a `WorkflowEngineFactory` at runtime.

Mid-term review raised a structural concern: the design required one Flowable `JavaDelegate` class per callable operation. The existing code already had sixteen such classes in `src/main/java/org/mifos/workflow/core/engine/delegates/`, and exposing the full Fineract API through this mechanism would have required hundreds.

### 2.2 The restructure

The mentor rebuilt the project onto the Mifos build conventions — a set of Gradle convention plugins that standardise module layout, dependency versions, code quality tooling and licence headers across Mifos projects. The migration was merged upstream as commit `9c452fc` on the `dev` branch, with authorship of the migration commit preserved to this contributor.

This changed the build system (Maven to Gradle), the module layout, the shape of the SPI, and the mechanism for engine callbacks. The conceptual work from the original approach — a neutral contract, engine isolation, shared Fineract logic, configuration-driven engine selection — carried over intact.

The remainder of the project was implementation and integration work on the new structure, which the mentor handed over untested:

> "Should work, untested… will leave the rest for you."

---

## 3. Architecture

### 3.1 Top-level layout

Every top-level module under `modules/` represents one problem domain:

```
modules/
├── infrastructure/     workflow engine integration — domain-agnostic
└── fineract/           Fineract integration — engine-agnostic
```

Neither knows about the other. A future integration with an unrelated external system would become a third top-level module.

### 3.2 The four-role split

Almost every folder in the project divides the same four ways:

| Folder | Contains | Visible to |
|---|---|---|
| `core` | interfaces, models, constants, properties | everyone |
| `implementation` | working code, package-private classes | nobody outside |
| `mapping` | MapStruct converters | its own module |
| `starter` | Spring auto-configuration | the application |

Folder depth is the visibility signal: the deeper a class sits, the more private it is intended to be.

### 3.3 The SPI

`modules/infrastructure/core` defines the contract. Nine interfaces, each a single-method functional interface extending `MifosUsecase<Req, Res>` from the Mifos commons library:

| Interface | Operation |
|---|---|
| `MifosFlowDeployUsecase` | install a workflow definition |
| `MifosFlowDeleteUsecase` | remove a deployed definition |
| `MifosFlowStartUsecase` | start a process instance |
| `MifosFlowCompleteUsecase` | complete a human task |
| `MifosFlowTerminateUsecase` | terminate a running instance |
| `MifosFlowTaskPendingUsecase` | list a user's pending tasks |
| `MifosFlowHistoryUsecase` | query completed instances |
| `MifosFlowSignalUsecase` | send an event into a waiting process |
| `MifosFlowReplayUsecase` | re-run a past process |

Each has one paired request and response class. Requests and responses are never shared between operations — one request class corresponds to exactly one usecase, which is what makes the callback routing described in 3.5 possible.

### 3.4 Two directions

The distinction between `usecase/` and `support/` is the direction of the call:

```
infrastructure/usecase/<engine>     the application calls the engine
                                    "start this process", "terminate that one"

infrastructure/support/<engine>     the engine calls the application
                                    "I reached a service task, run the business logic"
```

Both exist for all seven engines: Cadence, Camunda, CIBSeven, Conductor, Eximee, Flowable, Operaton.

### 3.5 The universal delegate

This is the part that addresses the mid-term concern directly.

The old design, in `src/main/resources/processes/loan-origination.bpmn20.xml`:

```xml
<serviceTask id="createLoanInFineract"  flowable:delegateExpression="${loanCreationDelegate}"/>
<serviceTask id="approveLoanInFineract" flowable:delegateExpression="${loanApprovalDelegate}"/>
<serviceTask id="rejectLoanInFineract"  flowable:delegateExpression="${loanRejectionDelegate}"/>
```

Three service tasks, three delegate classes — sixteen across the six workflows, and unbounded as the API surface grows.

The new design uses one delegate for every operation, in every workflow, permanently:

```java
public void execute(DelegateExecution execution) {
    var clazz    = resolve(execution);                                              // read the `type` field
    var request  = jsonHelper.parseRequest(
                       execution.getVariable(properties.getInputVariable()), clazz); // rebuild the request
    var response = registry.execute(request);                                        // look up and run the usecase
    store(execution, response);                                                      // write the result back
}
```

The BPMN supplies the request class name in a field; because one request class maps to exactly one usecase, the request object is its own routing key. Adding a new operation means writing a request class and a usecase — work that would be done anyway — and referencing the type name in the BPMN. The delegate is never modified.

### 3.6 Engine selection

Every engine implementation carries:

```java
@ConditionalOnBooleanProperty(FLOWABLE_WORKFLOW_PROPERTIES_ENABLED)
```

which resolves to `mifos.workflow.infrastructure.flowable.enabled`. When false, Spring does not create those beans. Controllers depend only on the interfaces, so switching engines is a configuration change with no code change and no recompilation.

The design is deliberately one engine per application instance. The mentor's reasoning, from a project meeting: allowing several engines in one instance eventually requires workflows in different engines to signal each other, which is difficult to reason about and to operate.

### 3.7 Transport

`modules/infrastructure/transport/rest` exposes nine endpoints, one per operation:

```
POST /workflows/deploy          POST /workflows/history
POST /workflows/delete          POST /workflows/signal
POST /workflows/start           POST /workflows/replay
POST /workflows/complete        POST /workflows/tasks/pending
POST /workflows/terminate
```

All use the versioned vendor media type `application/vnd.mifos.workflow+json;charset=UTF-8;version=1.0`, which allows future API versions to coexist on the same paths.

The module is named `transport` rather than `rest` because gRPC and RSocket are possible siblings later.

---

## 4. Work completed

### 4.1 Usecase implementations

Seven of the nine Flowable usecases were implemented and verified end to end. All were previously present as stubs that called the engine and discarded the result, or had no body at all.

| Usecase | Before | After |
|---|---|---|
| Deploy | engine call, result discarded | returns the deployment id |
| Start | engine call, result discarded | returns the process instance id |
| Complete | engine call, empty response | returns the completed task id |
| Terminate | engine call, empty response | returns the terminated process id |
| Delete | engine call, empty response | returns the deleted deployment id |
| TaskPending | query run, mapper injected but unused | returns the mapped task list |
| History | query run, result discarded | returns the mapped history list, filtered by id when supplied |

Example — `FlowableFlowDeployUsecase` before:

```java
// var deployment =
repositoryService.createDeployment()
        .addInputStream(request.getName(), request.getProcessDefinition())
        .name(request.getName())
        .deploy();

// TODO: return some sensible data
return MifosFlowDeployResponse.builder().build();
```

and after:

```java
var deployment = repositoryService.createDeployment()
        .addInputStream(request.getName(),
                new ByteArrayInputStream(request.getProcessDefinition().getBytes(StandardCharsets.UTF_8)))
        .name(request.getName())
        .deploy();

log.debug("deployed process definition {} with id {}", request.getName(), deployment.getId());

return MifosFlowDeployResponse.builder()
        .id(UUID.fromString(deployment.getId()))
        .build();
```

### 4.2 Contract changes

Three changes were made to shared models in `infrastructure/core`. Each was driven by a runtime failure or a structural mismatch rather than preference, and each has been raised with the mentor for confirmation.

**`MifosFlowDeployRequest.processDefinition`: `InputStream` → `String`**

Jackson cannot deserialise an `InputStream` from a JSON body. The endpoint returned:

```
HttpMessageConversionException: Type definition error: [simple type, class java.io.InputStream]
(through reference chain: MifosFlowDeployRequest["processDefinition"])
```

The field now carries the definition as text and is converted to a stream inside the usecase. This affects all seven engines equally — the same limitation would have applied to each.

**`MifosFlowTaskPendingResponse`: single task → `List<MifosFlowTask>`**

The response held nine fields describing one task, while the query is `taskService.createTaskQuery().taskAssignee(...).list()`, which returns many. The injected `FlowableTaskPendingMapper` could not be used because there was no list to map into. A new `MifosFlowTask` model holds the per-task fields; the response holds a list of them; the mapper gained a list method.

**`MifosFlowHistoryResponse`: `UUID id` → `List<MifosFlowHistoryEntry>`**

The same mismatch. A new `MifosFlowHistoryEntry` model was added, along with a new `FlowableHistoryMapper`. The usecase now also honours `request.getId()`, filtering to a single instance when one is supplied and returning all completed instances when it is not — previously the request was ignored entirely.

### 4.3 Error handling

`MifosFlowException.MifosFlowErrorCode` contained two entries. Seven were added, each corresponding to a failure mode of the implemented operations:

```
MIFOS_FLOW_ERROR_DEPLOYMENT_FAILED
MIFOS_FLOW_ERROR_DEPLOYMENT_NOT_FOUND
MIFOS_FLOW_ERROR_PROCESS_DEFINITION_NOT_FOUND
MIFOS_FLOW_ERROR_PROCESS_NOT_FOUND
MIFOS_FLOW_ERROR_PROCESS_START_FAILED
MIFOS_FLOW_ERROR_TASK_NOT_FOUND
MIFOS_FLOW_ERROR_ENGINE_UNAVAILABLE
```

Numbering follows the existing scheme, derived from `ERROR_CODE_START` and `ERROR_CODE_INCREMENT` rather than hard-coded.

The six `messages*.properties` resource bundles in `infrastructure/core` were empty. All are now populated with one entry per error code, in English, German, Spanish, French and Indonesian. The non-English translations are machine quality and should be reviewed by native speakers.

The codes are defined but not yet thrown from the usecases; wiring them in awaits confirmation of the intended pattern, as the commons library provides its own `MifosErrorHandlingControllerAdvice`.

---

## 5. Defects found

Integration work surfaced five defects. They are recorded here because each affects any project built on the same libraries, not only this one.

### 5.1 Request filter never continues the chain

`MifosContextRequestFilter` in `mifos-commons-boot`:

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
    // TODO: implement this!
}
```

A servlet filter must call `filterChain.doFilter(request, response)` to pass the request onward. This one does not, so every request to any application using the library terminates in the filter and returns an empty HTTP 200 — including Spring's own endpoints.

This was the cause of a symptom that took considerable time to isolate: every URL, valid or invalid, returned `200` with `Content-Length: 0`. Adding the `doFilter` call locally and republishing produced a correct `404` on an unknown route and correct routing to the controllers.

**This is the most consequential finding of the project.** Any Spring MVC application depending on `mifos-commons-boot-transport-rest-imperative` is currently unable to serve any request.

### 5.2 Application does not depend on the workflow modules

`modules/fineract/application/backend/implementation` did not have `infrastructure-starter` on its runtime classpath. Verified with:

```
gradlew :mifos-workflow-fineract-application-backend-implementation:dependencies --configuration runtimeClasspath
```

which returned no `infrastructure` entries at all. None of the controllers, usecases, support classes or the engine were present in the running application.

### 5.3 Engine auto-configurations activate unconditionally

With the infrastructure starter present, all seven engines land on the classpath. Four of them ship Spring auto-configurations that activate without checking whether that engine is wanted, and fail at startup looking for a server that is not running:

```
io.orkes.conductor.client.spring.OrkesConductorClientAutoConfiguration
org.cibseven.bpm.spring.boot.starter.CamundaBpmAutoConfiguration
org.operaton.bpm.spring.boot.starter.OperatonBpmAutoConfiguration
com.netflix.conductor.client.spring.ConductorClientAutoConfiguration
```

The Mifos classes are correctly guarded with `@ConditionalOnBooleanProperty`; these third-party ones are not. An aggregating starter that includes all engines cannot currently be used without excluding them.

### 5.4 SDK starter declares no dependencies

`mifos-commons-oas-imperative-rest-sdk-fineract-starter` is published as `<packaging>pom</packaging>` with no `<dependencies>` block — only licence, developer and SCM metadata. A starter of this kind exists to bring in other artifacts; this one brings in nothing, so `api "...-fineract-starter"` in the consuming module supplies no classes.

Confirmed by building the artifact from source: the locally produced pom is identical to the published one, so this is not a caching or publication problem.

### 5.5 OAS repository does not build with its documented command

The README for `mifos-commons-oas-imperative` gives:

```
./gradlew mifosConfigUnzip
./gradlew clean build
./gradlew clean build publish publishToMavenLocal
```

The second and third fail at `sonarlintMain` on a commented-out block in `FineractRestSdkConfiguration.java:32`. `publishToMavenLocal` on its own succeeds and produces the required artifacts.

---

## 6. Demonstration

Verified end to end through Postman against a locally running instance.

### 6.1 Running the application

```
gradlew :mifos-workflow-fineract-application-backend-implementation:bootRun \
        --args="--mifos.workflow.infrastructure.flowable.enabled=true"
```

Local workarounds required, none of which are committed:

- `infrastructure-starter` added as a dependency of the application module (5.2)
- the four engine auto-configurations excluded in `application.yml` (5.3)
- `@ComponentScan` widened on `Main` so the transport and Flowable usecase packages are picked up
- `filterChain.doFilter(...)` added to `MifosContextRequestFilter` in a local build of `mifos-commons-boot` (5.1)
- `bootRun { systemProperty 'java.io.tmpdir', ... }` — local only, working around a Windows machine whose `java.io.tmpdir` resolves to an unwritable directory

The workflow used is `loan-origination.bpmn20.xml` from the legacy `src/` tree, with the process id renamed to `loan-demo` and the three `flowable:delegateExpression` attributes replaced, since the sixteen delegate beans they reference no longer exist.

### 6.2 Sequence

All requests are `POST` with `Content-Type: application/vnd.mifos.workflow+json;charset=UTF-8;version=1.0`.

**Deploy** — `/workflows/deploy`, body containing the definition name and the BPMN XML:

```json
{ "id": "1adacc5f-9efc-11f1-8bbe-4ccf7cb90771" }
```

**Start** — `/workflows/start`:

```json
{ "key": "loan-demo",
  "variables": { "loanOfficer": "rajesh", "assignee": "rajesh", "approver": "manager" } }
```
```json
{ "id": "0ba5cadb-9f0a-11f1-bc76-4ccf7cb90771" }
```

**Pending tasks** — `/workflows/tasks/pending`, `{ "userId": "rajesh" }`:

```json
{ "tasks": [ {
    "taskId": "0bacf6d3-9f0a-11f1-bc76-4ccf7cb90771",
    "name": "Submit Loan Application",
    "processId": "0ba5cadb-9f0a-11f1-bc76-4ccf7cb90771",
    "processDefinitionId": "loan-demo:1:08cbf6ea-9f0a-11f1-bc76-4ccf7cb90771",
    "assignee": "rajesh",
    "createTime": "2026-08-23T15:48:11.933",
    "description": "Loan officer submits the loan application",
    "priority": 50
} ] }
```

**Complete** — `/workflows/complete` with that task id:

```json
{ "id": "0bacf6d3-9f0a-11f1-bc76-4ccf7cb90771" }
```

**Pending tasks again** — the same process instance, now at the next step:

```json
{ "tasks": [ {
    "taskId": "1b98481b-9f0a-11f1-bc76-4ccf7cb90771",
    "name": "Review Loan Application",
    "processId": "0ba5cadb-9f0a-11f1-bc76-4ccf7cb90771",
    "createTime": "2026-08-23T15:48:38.665",
    "description": "Review the loan application for completeness and accuracy"
} ] }
```

The unchanged `processId` and the changed task confirm the instance advanced rather than a new one being created.

**Terminate**, **history** and **delete** were verified in the same way. History returns entries only for completed instances, correctly filtered when an id is supplied and empty for an unknown id. After deleting the deployment, a further start request fails with `No process definition found for key 'loan-demo'`, confirming removal.

### 6.3 What this does and does not show

Demonstrated: the outbound direction of the SPI — the application driving the engine through a neutral, engine-agnostic API, with no Flowable type appearing in any request or response.

Not yet demonstrated: the inbound direction. `FlowableMifosUsecaseDelegate` is present and wired, but showing a BPMN service task calling back into a Fineract usecase requires the support package in the component scan and a working `MifosUsecaseRegistry` bean, which is not yet resolved.

---

## 7. Current state

**Working**

- Full Gradle build green across all modules
- Application starts; Flowable initialises and creates its schema
- Seven of nine Flowable usecases implemented and verified through REST
- Nine error codes defined; six message bundles populated
- One defect fixed in `mifos-commons-boot`

**Not complete**

- `MifosFlowSignalUsecase` — `MifosFlowSignalRequest` carries only `UUID id`; Flowable's `signalEventReceived` requires a signal name, and `trigger()` requires an execution id. The request needs an additional field before the operation can be written.
- `MifosFlowReplayUsecase` — same missing information, and Flowable has no native replay operation. It would have to be constructed from history: read a completed instance, recover its variables, start a new instance. What "replay" should mean is a design decision.
- Error codes are defined but not yet raised from the usecases.
- Persistence is in-memory H2, because no datasource is configured and Spring Boot falls back to it. State is lost on restart. `.mifos/config/compose/postgresql.yml` and `.mifos/config/env/postgres.env` exist but are not wired into the application.
- The other six engines remain stubs. The contract is shared; each engine's nine implementations must be written against its own SDK.
- The six BPMN definitions exist only in the legacy `src/` tree and have no home in the new module structure.
- The `doc` modules are placeholders.

---

## 8. Next steps

### For this contributor

1. Resolve the request shapes for Signal and Replay with the mentor, then implement both.
2. Wire the error codes into the usecases, following whatever pattern the commons `MifosErrorHandlingControllerAdvice` expects.
3. Configure PostgreSQL using the compose and environment files already present in the Mifos config.
4. Demonstrate the inbound direction — a BPMN service task calling a Fineract usecase through the universal delegate.
5. Decide where the BPMN definitions belong in the new structure.
6. Write the module documentation in the `doc` modules.

A pair programming session with the mentor is planned to work through several of these.

### For the community

1. **`MifosContextRequestFilter` (5.1) needs fixing upstream.** Until it is, no Spring MVC application using `mifos-commons-boot-transport-rest-imperative` can serve a request.
2. **The SDK starter (5.4) needs a dependencies block**, or consuming modules must depend on the implementation artifact directly.
3. **The aggregating infrastructure starter (5.3) cannot currently include all engines**, because four third-party auto-configurations activate unconditionally. Either the starter should be split per engine, or the exclusions should be applied centrally.
4. **The remaining six engine adapters** — Cadence, Camunda, CIBSeven, Conductor, Eximee, Operaton — are open, well-defined pieces of work. The contract exists and the Flowable adapter is a worked example.
5. **BPMN definitions should be made overridable** so an institution can customise a process by supplying its own definition rather than modifying the project, which was the mentor's stated intent for the design.

---

## 9. Links

| | |
|---|---|
| Upstream repository | https://github.com/openMF/mifos-workflow |
| Working branch | https://github.com/001Albin/mifos-workflow/tree/feature/flowable-usecases |
| Migration PR (merged upstream) | https://github.com/openMF/mifos-workflow/pull/74 |
| Mifos commons (Spring Boot) | https://github.com/monkeysintown/mifos-commons-boot |
| Mifos build conventions (base) | https://github.com/monkeysintown/mifos-conventions-gradle-base |
| Mifos build conventions (boot) | https://github.com/monkeysintown/mifos-conventions-gradle-boot |
| Generated REST SDKs | https://github.com/monkeysintown/mifos-commons-oas-imperative |

---

## 10. Notes on accuracy

Everything in this report is drawn from source read directly, from build and application output observed locally, or from HTTP responses captured during testing. Where a change touches a shared contract, that is stated along with the failure that prompted it. Where something is unverified — the inbound delegate path, the intended error-raising pattern, the shape Signal and Replay should take — it is recorded as open rather than described as done.