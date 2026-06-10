package org.mifos.workflow.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a newly started workflow process instance.
 *
 * Returned by WorkflowService.startProcess() after successfully
 * creating a new process instance in the engine.
 *
 * Both Flowable and Conductor adapters must populate this object
 * using their engine-specific response data.
 *
 * SECURITY: This object contains no credentials, passwords,
 * or sensitive financial data — only process tracking identifiers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstance {

    /**
     * The unique identifier of this process instance.
     * In Flowable: ProcessInstance.getId()
     * In Conductor: the workflow instance ID returned by startWorkflow()
     */
    private String instanceId;

    /**
     * The name/key of the process definition that was started.
     * e.g. "client-onboarding", "loan-origination", "loan-disbursement"
     */
    private String workflowName;

    /**
     * The current status of the process at the moment it was created.
     * Will always be RUNNING immediately after startProcess() succeeds.
     */
    private WorkflowStatus status;

    /**
     * The timestamp when this process instance was created.
     */
    private LocalDateTime startTime;

    /**
     * Optional business key for correlating the process with an
     * external entity — e.g. a loan ID or client ID.
     */
    private String businessKey;
}